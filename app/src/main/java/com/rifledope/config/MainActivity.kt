package com.rifledope.config

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.rifledope.config.ui.App
import com.rifledope.config.ui.theme.RifleTheme
import com.rifledope.config.vm.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var hasPerms by mutableStateOf(false)
    private var pendingScan = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerms = result.values.all { it }
        if (hasPerms && pendingScan) { pendingScan = false; ensureBtThenScan() }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (vm.isBluetoothOn()) vm.startScan() }

    private fun requiredPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun permsOk() = requiredPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureBtThenScan() {
        if (!vm.isBluetoothOn()) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else vm.startScan()
    }

    /** Called from the UI's Scan button. */
    private fun onScanRequested() {
        if (permsOk()) { hasPerms = true; ensureBtThenScan() }
        else { pendingScan = true; permLauncher.launch(requiredPerms()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPerms = permsOk()
        setContent {
            RifleTheme {
                App(vm = vm, onScan = { onScanRequested() })
            }
        }
    }
}
