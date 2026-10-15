package com.rifledope.config.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rifledope.config.ble.ConnState
import com.rifledope.config.vm.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel, onScan: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val conn by vm.connState.collectAsState()
    val connectedName by vm.connectedName.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.toast.collect { scope.launch { snackbar.showSnackbar(it) } }
    }

    val linkLabel = when (conn) {
        ConnState.CONNECTED -> connectedName ?: "Connected"
        ConnState.CONNECTING -> "Connecting…"
        ConnState.SCANNING -> "Scanning…"
        ConnState.IDLE -> "Not connected"
    }
    val linkColor = if (conn == ConnState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rifle Config") },
                actions = {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = linkColor,
                        modifier = Modifier.padding(end = 6.dp))
                    Text(linkLabel, color = linkColor, modifier = Modifier.padding(end = 14.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Bluetooth, null) }, label = { Text("Board") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Load") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.List, null) }, label = { Text("DOPE") })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> DevicesScreen(vm, onScan)
                1 -> ProfileScreen(vm)
                else -> DopeScreen(vm)
            }
        }
    }
}
