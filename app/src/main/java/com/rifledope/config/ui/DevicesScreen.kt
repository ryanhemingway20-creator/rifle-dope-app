package com.rifledope.config.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rifledope.config.ble.ConnState
import com.rifledope.config.vm.AppViewModel

@Composable
fun DevicesScreen(vm: AppViewModel, onScan: () -> Unit) {
    val conn by vm.connState.collectAsState()
    val scan by vm.scan.collectAsState()
    val status by vm.status.collectAsState()
    val connectedName by vm.connectedName.collectAsState()

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onScan, enabled = conn != ConnState.CONNECTING) {
                Text(if (conn == ConnState.SCANNING) "Scanning…" else "Scan")
            }
            if (conn == ConnState.CONNECTED) {
                OutlinedButton(onClick = { vm.readFromBoard() }) { Text("Load from board") }
                OutlinedButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
            }
        }

        if (conn == ConnState.CONNECTED) {
            Card(
                Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(connectedName ?: "Board", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    val s = status
                    if (s != null) {
                        Text("Firmware ${s.fw}   Unit ${s.unit}   Team ${s.team}")
                        Text(if (s.linked) "Partner LINKED  (heading ${s.partnerHdg}°)" else "Partner: waiting",
                            color = if (s.linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.battery in 0..100) Text("Battery ${s.battery}%")
                    } else {
                        Text("Reading status…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { vm.refreshStatus() }) { Text("Refresh") }
                }
            }
        } else {
            Text("Tap Scan, then tap your board (RifleDOPE-1 / RifleDOPE-2).",
                Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn {
                items(scan) { d ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { vm.connect(d.address) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text("${d.address}   ${d.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
