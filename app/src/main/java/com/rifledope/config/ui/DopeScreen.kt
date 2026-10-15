package com.rifledope.config.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rifledope.config.ballistics.DopeRow
import com.rifledope.config.vm.AppViewModel
import java.util.Locale

@Composable
fun DopeScreen(vm: AppViewModel) {
    val dope by vm.dope.collectAsState()
    val active by vm.active.collectAsState()
    val busy by vm.busy.collectAsState()
    val moa = active.units.equals("moa", true)
    val unitLbl = if (moa) "MOA" else "MIL"
    fun conv(mil: Double) = if (moa) mil * 3.43775 else mil

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Cell("YARD", 1f, header = true)
            Cell("ELEV $unitLbl", 1.2f, header = true)
            Cell("WIND/10", 1.2f, header = true)
            Cell("TOF", 1f, header = true)
        }
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(dope) { r: DopeRow ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Cell("${r.yd}", 1f)
                    Cell(String.format(Locale.US, "%.2f", conv(r.elevMil)), 1.2f,
                        color = MaterialTheme.colorScheme.primary)
                    Cell(String.format(Locale.US, "%.2f", conv(r.windMil)), 1.2f,
                        color = MaterialTheme.colorScheme.secondary)
                    Cell(String.format(Locale.US, "%.2f", r.tof), 1f)
                }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Button(
            onClick = { vm.pushToBoard() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (busy) "Sending…" else "Send to board") }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String, weight: Float, header: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Start,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else color,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
    )
}
