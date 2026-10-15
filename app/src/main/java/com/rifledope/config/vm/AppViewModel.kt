package com.rifledope.config.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rifledope.config.ballistics.CsvDopeImport
import com.rifledope.config.ballistics.CsvImport
import com.rifledope.config.ballistics.CsvImportException
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

    // GeoBallistics CSV import. When _imported is non-null the dope table came from a CSV the
    // shooter exported himself (not the on-phone solver), so recompute() must leave it alone.
    // _pendingImport is a parsed-but-unconfirmed import that drives the review dialog, so the
    // shooter sees the detected columns/units before anything replaces the table.
    private val _imported = MutableStateFlow<CsvImport?>(null)
    val imported: StateFlow<CsvImport?> = _imported.asStateFlow()
    private val _pendingImport = MutableStateFlow<CsvImport?>(null)
    val pendingImport: StateFlow<CsvImport?> = _pendingImport.asStateFlow()

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
        val imp = _imported.value
        viewModelScope.launch {
            // Send exactly what's on screen: imported rows if a CSV is applied, else the solved
            // table. When imported, the SET header's grid must come from the import (not the
            // profile, which the shooter may have edited since) so base/step match the rows'
            // yardages. Fall back to a fresh solve only if the table hasn't been built yet.
            val rows = _dope.value
            val prof = if (imp != null) _active.value.copy(base = imp.base, step = imp.step)
                       else _active.value
            val msg = withContext(Dispatchers.Default) {
                if (rows.isEmpty()) Protocol.computeAndEncodeSet(_active.value)
                else Protocol.encodeSet(prof, rows)
            }
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
                setActiveProfile(merged); _editEpoch.value++
                _imported.value = null; _pendingImport.value = null
                recompute(); persist()
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
        store.setActiveIndex(i); _editEpoch.value++
        _imported.value = null; _pendingImport.value = null   // a CSV import belongs to one load
        recompute()
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
        store.setActiveIndex(ni); _editEpoch.value++
        _imported.value = null; _pendingImport.value = null
        persist(); recompute()
    }

    // ---- GeoBallistics CSV import ----
    /** Read a CSV the shooter picked from storage, then parse + stage it for review. */
    fun importCsvUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: Exception) { null }
            }
            if (text.isNullOrBlank()) emit("Couldn't open that file") else importCsv(text)
        }
    }

    /** Parse a CSV the shooter exported from GeoBallistics; on success, stage it for review. */
    fun importCsv(text: String) {
        val p = _active.value
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val solved = Solver.solve(p.solveInput())
                    CsvDopeImport.parse(
                        text = text,
                        base = p.base,
                        step = p.step,
                        maxRows = p.rows,
                        solved = solved,
                    )
                }
                _pendingImport.value = result
            } catch (e: CsvImportException) {
                emit(e.message ?: "Couldn't read that CSV")
            } catch (e: Exception) {
                emit("Couldn't read that CSV: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Confirm the staged import: it becomes the live dope table (sent as-is to the board). */
    fun applyImport() {
        val pend = _pendingImport.value ?: return
        _imported.value = pend
        _dope.value = pend.rows
        _pendingImport.value = null
        emit("Imported " + pend.rows.size + " rows from CSV")
    }

    /** Dismiss the review dialog without applying. */
    fun cancelImport() { _pendingImport.value = null }

    /** Drop an applied import and go back to the on-phone solved table. */
    fun clearImport() {
        _imported.value = null
        _pendingImport.value = null
        recompute()
        emit("Back to computed DOPE")
    }

    private fun recompute() {
        if (_imported.value != null) return   // imported dope stands until the shooter clears it
        viewModelScope.launch {
            val rows = withContext(Dispatchers.Default) { Solver.solve(_active.value.solveInput()) }
            _dope.value = rows
        }
    }

    private fun persist() = store.save(_profiles.value)

    override fun onCleared() { ble.disconnect(); super.onCleared() }
}
