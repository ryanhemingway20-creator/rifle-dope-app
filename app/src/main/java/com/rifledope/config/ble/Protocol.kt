package com.rifledope.config.ble

import com.rifledope.config.ballistics.DopeRow
import com.rifledope.config.ballistics.Solver
import com.rifledope.config.model.BoardStatus
import com.rifledope.config.model.Profile
import java.util.Locale

/** Text protocol codec shared with the board (see PROTOCOL.md). EOT-framed key=value. */
object Protocol {
    const val EOT = 0x04.toByte()

    private fun f(v: Double, dp: Int) = String.format(Locale.US, "%.${dp}f", v)

    /** Build the full SET payload (verb + keys + computed dope table), ready to frame+chunk. */
    fun encodeSet(p: Profile, dope: List<DopeRow>): String {
        val sb = StringBuilder()
        sb.append("SET\n")
        sb.append("ver=1\n")
        sb.append("unit=").append(p.unit).append('\n')
        sb.append("team=").append(p.team).append('\n')
        sb.append("load=").append(p.name.take(27)).append('\n')
        sb.append("mv=").append(p.mv).append('\n')
        sb.append("bc=").append(f(p.bc, 3)).append('\n')
        sb.append("bcm=").append(p.bcModel).append('\n')
        sb.append("zero=").append(p.zero).append('\n')
        sb.append("sh=").append(f(p.sightHt, 2)).append('\n')
        sb.append("twist=").append(f(p.twist, 2)).append('\n')
        sb.append("tempf=").append(p.tempF).append('\n')
        sb.append("press=").append(f(p.pressInHg, 2)).append('\n')
        sb.append("alt=").append(p.altFt).append('\n')
        sb.append("hum=").append(p.humPct).append('\n')
        sb.append("winddef=").append(p.windDef).append('\n')
        sb.append("units=").append(p.units).append('\n')
        sb.append("base=").append(p.base).append('\n')
        sb.append("step=").append(p.step).append('\n')
        sb.append("n=").append(dope.size).append('\n')
        sb.append("elev=").append(dope.joinToString(",") { f(it.elevMil, 2) }).append('\n')
        sb.append("wind10=").append(dope.joinToString(",") { f(it.windMil, 2) }).append('\n')
        sb.append("tof=").append(dope.joinToString(",") { f(it.tof, 3) }).append('\n')
        return sb.toString()
    }

    fun computeAndEncodeSet(p: Profile): String = encodeSet(p, Solver.solve(p.solveInput()))

    private fun toMap(body: String): Map<String, String> {
        val m = HashMap<String, String>()
        for (raw in body.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            m[line.substring(0, eq).trim()] = line.substring(eq + 1)
        }
        return m
    }

    /** Parse a CFG body into a Profile (keeps caller's defaults for any missing key). */
    fun parseCfg(body: String, base: Profile): Profile {
        val m = toMap(body)
        return base.copy(
            unit = m["unit"]?.toIntOrNull() ?: base.unit,
            team = m["team"]?.toIntOrNull() ?: base.team,
            name = m["load"] ?: base.name,
            mv = m["mv"]?.toIntOrNull() ?: base.mv,
            bc = m["bc"]?.toDoubleOrNull() ?: base.bc,
            bcModel = m["bcm"] ?: base.bcModel,
            zero = m["zero"]?.toIntOrNull() ?: base.zero,
            sightHt = m["sh"]?.toDoubleOrNull() ?: base.sightHt,
            twist = m["twist"]?.toDoubleOrNull() ?: base.twist,
            tempF = m["tempf"]?.toIntOrNull() ?: base.tempF,
            pressInHg = m["press"]?.toDoubleOrNull() ?: base.pressInHg,
            altFt = m["alt"]?.toIntOrNull() ?: base.altFt,
            humPct = m["hum"]?.toIntOrNull() ?: base.humPct,
            windDef = m["winddef"]?.toIntOrNull() ?: base.windDef,
            units = m["units"] ?: base.units,
            base = m["base"]?.toIntOrNull() ?: base.base,
            step = m["step"]?.toIntOrNull() ?: base.step,
            rows = m["n"]?.toIntOrNull() ?: base.rows,
        )
    }

    fun parseStatus(body: String): BoardStatus {
        val m = toMap(body)
        return BoardStatus(
            fw = m["fw"] ?: "",
            unit = m["unit"]?.toIntOrNull() ?: 0,
            team = m["team"]?.toIntOrNull() ?: 0,
            linked = (m["link"]?.toIntOrNull() ?: 0) == 1,
            partnerHdg = m["phdg"]?.toIntOrNull() ?: -1,
            battery = m["batt"]?.toIntOrNull() ?: -1,
        )
    }
}
