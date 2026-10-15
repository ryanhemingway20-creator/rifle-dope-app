package com.rifledope.config.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rifledope.config.ballistics.CsvImport
import com.rifledope.config.ballistics.DopeRow
import com.rifledope.config.vm.AppViewModel
import java.util.Locale

@Composable
fun DopeScreen(vm: AppViewModel) {
    val dope by vm.dope.collectAsState()
    val active by vm.active.collectAsState()
    val busy by vm.busy.collectAsState()
    val imported by vm.imported.collectAsState()
    val pending by vm.pendingImport.collectAsState()
    val moa = active.units.equals("moa", true)
    val unitLbl = if (moa) "MOA" else "MIL"
    fun conv(mil: Double) = if (moa) mil * 3.43775 else mil

    // Pick a CSV the shooter exported himself. OpenDocument gives a persistable read Uri; "*/*"
    // because CSV files get labelled a dozen different MIME types across Android file providers.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importCsvUri(it) }
    }

    Column(Modifier.fillMaxWidth()) {
        imported?.let { imp ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "Imported DOPE (${imp.rows.size} rows from your CSV)",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.clearImport() }) { Text("Use computed") }
                }
            }
        }
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
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) { Text("Import GeoBallistics CSV") }
        Button(
            onClick = { vm.pushToBoard() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (busy) "Sending…" else "Send to board") }
    }

    pending?.let { imp ->
        ImportReviewDialog(
            imp = imp,
            unitLbl = unitLbl,
            conv = ::conv,
            onApply = { vm.applyImport() },
            onCancel = { vm.cancelImport() },
        )
    }
}

/** Shows the detected columns/units + a preview so the shooter confirms the parse before it lands. */
@Composable
private fun ImportReviewDialog(
    imp: CsvImport,
    unitLbl: String,
    conv: (Double) -> Double,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import this DOPE?") },
        text = {
            Column {
                Line("Range column", "${imp.rangeCol} (${imp.rangeUnit})")
                Line("Elevation column", "${imp.elevCol} (${imp.elevUnit})")
                Line("Wind column", imp.windCol?.let { "$it (${imp.windUnit}, ${fmt(imp.windMph)} mph)" } ?: "none")
                Line("Time of flight", imp.tofCol ?: "computed")
                Line("Rows on grid", "${imp.rows.size} @ ${imp.base}/${imp.step} yd, to ${imp.srcMaxRangeYd} yd")
                if (imp.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    imp.warnings.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Preview ($unitLbl):", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                previewRows(imp.rows).forEach { r ->
                    Text(
                        "${r.yd} yd   " +
                            String.format(Locale.US, "%.2f", conv(r.elevMil)) + " / " +
                            String.format(Locale.US, "%.2f", conv(r.windMil)) +
                            "   t" + String.format(Locale.US, "%.2f", r.tof),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Use these") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** First few + last row of the imported grid, so a long table doesn't overflow the dialog. */
private fun previewRows(rows: List<DopeRow>): List<DopeRow> =
    if (rows.size <= 5) rows else rows.take(4) + rows.last()

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
