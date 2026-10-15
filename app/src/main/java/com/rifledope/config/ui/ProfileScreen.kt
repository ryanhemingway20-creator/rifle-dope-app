package com.rifledope.config.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rifledope.config.vm.AppViewModel

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val profiles by vm.profiles.collectAsState()
    val activeIdx by vm.activeIdx.collectAsState()
    val epoch by vm.editEpoch.collectAsState()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LazyRow(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles.size) { i ->
                FilterChip(selected = i == activeIdx, onClick = { vm.selectProfile(i) },
                    label = { Text(profiles[i].name.take(16)) })
            }
            item {
                OutlinedButton(onClick = { vm.addProfile() }) { Text("+ New") }
            }
        }

        // Re-key the editable fields whenever the active profile is swapped wholesale.
        key(epoch) {
            val p = vm.active.value
            var name by remember { mutableStateOf(p.name) }
            var unit by remember { mutableStateOf(p.unit.toString()) }
            var team by remember { mutableStateOf(p.team.toString()) }
            var mv by remember { mutableStateOf(p.mv.toString()) }
            var bc by remember { mutableStateOf(p.bc.toString()) }
            var model by remember { mutableStateOf(p.bcModel) }
            var zero by remember { mutableStateOf(p.zero.toString()) }
            var sh by remember { mutableStateOf(p.sightHt.toString()) }
            var twist by remember { mutableStateOf(p.twist.toString()) }
            var tempF by remember { mutableStateOf(p.tempF.toString()) }
            var press by remember { mutableStateOf(p.pressInHg.toString()) }
            var alt by remember { mutableStateOf(p.altFt.toString()) }
            var hum by remember { mutableStateOf(p.humPct.toString()) }
            var windDef by remember { mutableStateOf(p.windDef.toString()) }
            var units by remember { mutableStateOf(p.units) }
            var base by remember { mutableStateOf(p.base.toString()) }
            var step by remember { mutableStateOf(p.step.toString()) }
            var rows by remember { mutableStateOf(p.rows.toString()) }

            SectionCard("Rifle / load") {
                FieldRow("Name", name, { name = it; vm.updateActive { x -> x.copy(name = it) } }, number = false)
                FieldRow("Muzzle vel", mv, { mv = it; vm.updateActive { x -> x.copy(mv = it.toIntSafe(x.mv)) } }, decimal = false, suffix = "fps")
                FieldRow("BC", bc, { bc = it; vm.updateActive { x -> x.copy(bc = it.toDoubleSafe(x.bc)) } })
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(model == "G7", { model = "G7"; vm.updateActive { it.copy(bcModel = "G7") } }, { Text("G7") })
                    FilterChip(model == "G1", { model = "G1"; vm.updateActive { it.copy(bcModel = "G1") } }, { Text("G1") })
                }
                FieldRow("Zero", zero, { zero = it; vm.updateActive { x -> x.copy(zero = it.toIntSafe(x.zero)) } }, decimal = false, suffix = "yd")
                FieldRow("Sight height", sh, { sh = it; vm.updateActive { x -> x.copy(sightHt = it.toDoubleSafe(x.sightHt)) } }, suffix = "in")
                FieldRow("Twist", twist, { twist = it; vm.updateActive { x -> x.copy(twist = it.toDoubleSafe(x.twist)) } }, suffix = "in")
            }

            SectionCard("Conditions (where the dope was trued)") {
                FieldRow("Temp", tempF, { tempF = it; vm.updateActive { x -> x.copy(tempF = it.toIntSafe(x.tempF)) } }, decimal = false, suffix = "°F")
                FieldRow("Pressure", press, { press = it; vm.updateActive { x -> x.copy(pressInHg = it.toDoubleSafe(x.pressInHg)) } }, suffix = "inHg")
                FieldRow("Altitude", alt, { alt = it; vm.updateActive { x -> x.copy(altFt = it.toIntSafe(x.altFt)) } }, decimal = false, suffix = "ft")
                FieldRow("Humidity", hum, { hum = it; vm.updateActive { x -> x.copy(humPct = it.toIntSafe(x.humPct)) } }, decimal = false, suffix = "%")
                FieldRow("Default wind", windDef, { windDef = it; vm.updateActive { x -> x.copy(windDef = it.toIntSafe(x.windDef)) } }, decimal = false, suffix = "mph")
            }

            SectionCard("Dope table") {
                FieldRow("First range", base, { base = it; vm.updateActive { x -> x.copy(base = it.toIntSafe(x.base)) } }, decimal = false, suffix = "yd")
                FieldRow("Step", step, { step = it; vm.updateActive { x -> x.copy(step = it.toIntSafe(x.step)) } }, decimal = false, suffix = "yd")
                FieldRow("Rows", rows, { rows = it; vm.updateActive { x -> x.copy(rows = it.toIntSafe(x.rows).coerceIn(1, 48)) } }, decimal = false)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(units == "mil", { units = "mil"; vm.updateActive { it.copy(units = "mil") } }, { Text("MIL") })
                    FilterChip(units == "moa", { units = "moa"; vm.updateActive { it.copy(units = "moa") } }, { Text("MOA") })
                }
            }

            SectionCard("Board identity") {
                FieldRow("This unit (1 or 2)", unit, { unit = it; vm.updateActive { x -> x.copy(unit = it.toIntSafe(x.unit).coerceIn(1, 2)) } }, decimal = false)
                FieldRow("Team", team, { team = it; vm.updateActive { x -> x.copy(team = it.toIntSafe(x.team)) } }, decimal = false)
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.deleteActive() }) { Text("Delete profile") }
                Button(onClick = { vm.pushToBoard() }) { Text("Send to board") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
