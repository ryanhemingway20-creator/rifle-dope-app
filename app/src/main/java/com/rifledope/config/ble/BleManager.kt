package com.rifledope.config.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class BleDevice(val name: String, val address: String, val rssi: Int)

enum class ConnState { IDLE, SCANNING, CONNECTING, CONNECTED }

/**
 * One-tap BLE link to a RifleDOPE board. Scans by service UUID, connects, raises MTU,
 * enables notifications, writes config in chunks, reassembles EOT-framed replies, and
 * auto-reconnects. Permissions are gated by the caller (MainActivity).
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {
    companion object {
        val SVC: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")  // phone -> board (write)
        val TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")  // board -> phone (notify)
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_MSG = 4096
    }

    private val appCtx = context.applicationContext
    private val btManager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = btManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private val main = Handler(Looper.getMainLooper())

    private val _scan = MutableStateFlow<List<BleDevice>>(emptyList())
    val scan: StateFlow<List<BleDevice>> = _scan.asStateFlow()
    private val _state = MutableStateFlow(ConnState.IDLE)
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private var wantConnected = false
    private var targetAddress: String? = null
    private var retries = 0

    private val rxBuf = StringBuilder()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writing = false

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    // ---- scanning ----
    private val found = LinkedHashMap<String, BleDevice>()
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = dev.name ?: result.scanRecord?.deviceName ?: "RifleDOPE board"
            found[dev.address] = BleDevice(name, dev.address, result.rssi)
            _scan.value = found.values.sortedByDescending { it.rssi }
        }
    }

    fun startScan() {
        val s = scanner ?: return
        found.clear(); _scan.value = emptyList()
        _state.value = ConnState.SCANNING
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SVC)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try { s.startScan(listOf(filter), settings, scanCb) } catch (_: Exception) {}
        main.postDelayed({ stopScan() }, 12_000)
    }

    fun stopScan() {
        try { scanner?.stopScan(scanCb) } catch (_: Exception) {}
        if (_state.value == ConnState.SCANNING) _state.value = ConnState.IDLE
    }

    // ---- connect ----
    fun connect(address: String) {
        stopScan()
        wantConnected = true; targetAddress = address; retries = 0
        openGatt(address)
    }

    private fun openGatt(address: String) {
        val dev: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        _state.value = ConnState.CONNECTING
        closeGatt()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            dev.connectGatt(appCtx, false, cb, BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(appCtx, false, cb)
    }

    fun disconnect() {
        wantConnected = false
        closeGatt()
        _state.value = ConnState.IDLE
        _connectedName.value = null
    }

    private fun closeGatt() {
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null; rxChar = null; txChar = null
        synchronized(writeQueue) { writeQueue.clear(); writing = false }
        rxBuf.setLength(0)
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                retries = 0
                main.post { try { g.requestMtu(247) } catch (_: Exception) { g.discoverServices() } }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = if (wantConnected) ConnState.CONNECTING else ConnState.IDLE
                _connectedName.value = null
                if (wantConnected && retries < 5) {
                    retries++
                    main.postDelayed({ targetAddress?.let { openGatt(it) } }, 1500L * retries)
                } else closeGatt()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) {
            mtu = if (status == BluetoothGatt.GATT_SUCCESS) m else 23
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SVC)
            rxChar = svc?.getCharacteristic(RX)
            txChar = svc?.getCharacteristic(TX)
            val tx = txChar
            if (tx != null) {
                g.setCharacteristicNotification(tx, true)
                val d = tx.getDescriptor(CCCD)
                if (d != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION") g.writeDescriptor(d)
                    }
                }
            }
            if (rxChar != null && txChar != null) {
                _state.value = ConnState.CONNECTED
                _connectedName.value = g.device?.name
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray,
        ) { onNotify(value) }

        @Deprecated("pre-33")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") onNotify(ch.value ?: ByteArray(0))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            synchronized(writeQueue) { writing = false }
            pumpWrites()
        }
    }

    private fun onNotify(value: ByteArray) {
        for (b in value) {
            if (b == Protocol.EOT) {
                val msg = rxBuf.toString(); rxBuf.setLength(0)
                if (msg.isNotEmpty()) _incoming.tryEmit(msg)
            } else if (rxBuf.length < MAX_MSG) {
                rxBuf.append((b.toInt() and 0xFF).toChar())
            }
        }
    }

    // ---- write (chunked, EOT-framed, sequential) ----
    fun send(message: String) {
        val framed = (message + EOT_STR).toByteArray(Charsets.UTF_8)
        val chunkSize = (mtu - 3).coerceIn(20, 512)
        synchronized(writeQueue) {
            var i = 0
            while (i < framed.size) {
                val n = minOf(chunkSize, framed.size - i)
                writeQueue.add(framed.copyOfRange(i, i + n)); i += n
            }
        }
        pumpWrites()
    }

    private fun pumpWrites() {
        val g = gatt ?: return
        val ch = rxChar ?: return
        val next: ByteArray
        synchronized(writeQueue) {
            if (writing) return
            next = writeQueue.poll() ?: return
            writing = true
        }
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, next, type)
            } else {
                @Suppress("DEPRECATION") run { ch.writeType = type; ch.value = next; g.writeCharacteristic(ch) }
            }
        } catch (_: Exception) {
            synchronized(writeQueue) { writing = false }
        }
    }

    private val EOT_STR = String(charArrayOf(0x04.toChar()))
}
