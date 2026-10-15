package com.rifledope.config.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rifledope.config.ballistics.DopeRow
import com.rifledope.config.ballistics.Solver
import com.rifledope.config.ble.BleManager
import com.rifledope.config.ble.ConnState
import com.rifledope.config.ble.Protocol
import com.rifledope.config.data.ProfileStore
import com.rifledope.config.model.BoardStatus
import com.rifledope.config.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ble = BleManager(app)
    private val store = ProfileStore(app)

    // BLE surface
    val connState: StateFlow<ConnState> = ble.state
    val scan = ble.scan
    val connectedName = ble.connectedName
    fun isBluetoothOn() = ble.isBluetoothOn()

    // profiles
    private val _profiles = MutableStateFlow(store.load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    private val _activeIdx = MutableStateFlow(store.activeIndex().coerceIn(0, (_profiles.value.size - 1).coerceAtLeast(0)))
    val activeIdx: StateFlow<Int> = _activeIdx.asStateFlow()

    private val _active = MutableStateFlow(_profiles.value[_activeIdx.value])
    val active: StateFlow<Profile> = _active.asStateFlow()

    // bumped only on a wholesale profile change (select / load-from-board / add / delete),
    // NOT on field edits, so the editor can re-key its fields without cursor jumps while typing.
    private val _editEpoch = MutableStateFlow(0)
    val editEpoch: StateFlow<Int> = _editEpoch.asStateFlow()

    private val _dope = MutableStateFlow<List<DopeRow>>(emptyList())
    val dope: StateFlow<List<DopeRow>> = _dope.asStateFlow()

    private val _status = MutableStateFlow<BoardStatus?>(null)
    val status: StateFlow<BoardStatus?> = _status.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { ble.incoming.collect { onMessage(it) } }
        recompute()
    }

    // ---- BLE actions ----
    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connect(address: String) = ble.connect(address)
    fun disconnect() { ble.disconnect(); _status.value = null }

    fun pushToBoard() {
        if (connState.value != ConnState.CONNECTED) { emit("Connect to a board first"); return }
        _busy.value = true
        viewModelScope.launch {
            val msg = withContext(Dispatchers.Default) { Protocol.computeAndEncodeSet(_active.value) }
            ble.send(msg)
        }
    }

    fun readFromBoard() {
        if (connState.value != ConnState.CONNECTED) { emit("Connect to a board first"); return }
        ble.send("GET")
    }

    fun refreshStatus() { if (connState.value == ConnState.CONNECTED) ble.send("STATUS") }

    private fun onMessage(msg: String) {
        val verb = msg.substringBefore('\n').trim()
        val body = if (msg.contains('\n')) msg.substringAfter('\n') else ""
        when {
            verb == "CFG" -> {
                val merged = Protocol.parseCfg(body, _active.value)
                setActiveProfile(merged); _editEpoch.value++; recompute(); persist()
                emit("Loaded settings from board")
            }
            verb == "ST" -> _status.value = Protocol.parseStatus(body)
            verb == "OK" -> { _busy.value = false; emit("Sent to board ✓"); refreshStatus() }
            verb.startsWith("ERR") -> { _busy.value = false; emit("Board rejected: " + verb.removePrefix("ERR").trim()) }
            verb == "PONG" -> {}
        }
    }

    private fun emit(s: String) { viewModelScope.launch { _toast.emit(s) } }

    // ---- profile editing ----
    fun updateActive(mutate: (Profile) -> Profile) {
        setActiveProfile(mutate(_active.value)); recompute(); persist()
    }

    private fun setActiveProfile(p: Profile) {
        _active.value = p
        val list = _profiles.value.toMutableList()
        if (_activeIdx.value in list.indices) list[_activeIdx.value] = p
        _profiles.value = list
    }

    fun selectProfile(i: Int) {
        if (i !in _profiles.value.indices) return
        _activeIdx.value = i; _active.value = _profiles.value[i]
        store.setActiveIndex(i); _editEpoch.value++; recompute()
    }

    fun addProfile() {
        val list = _profiles.value.toMutableList()
        list.add(_active.value.copy(name = _active.value.name + " copy"))
        _profiles.value = list; selectProfile(list.size - 1); persist()
    }

    fun deleteActive() {
        if (_profiles.value.size <= 1) { emit("Keep at least one profile"); return }
        val list = _profiles.value.toMutableList()
        list.removeAt(_activeIdx.value)
        _profiles.value = list
        val ni = _activeIdx.value.coerceIn(0, list.size - 1)
        _activeIdx.value = ni; _active.value = list[ni]
        store.setActiveIndex(ni); _editEpoch.value++; persist(); recompute()
    }

    private fun recompute() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.Default) { Solver.solve(_active.value.solveInput()) }
            _dope.value = rows
        }
    }

    private fun persist() = store.save(_profiles.value)

    override fun onCleared() { ble.disconnect(); super.onCleared() }
}
