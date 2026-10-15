package com.rifledope.config.ballistics

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Result of importing a GeoBallistics-style CSV. [rows] are already resampled onto the
 * board's base/step yard grid and converted to MILS, ready to send. The detected column
 * names + units are surfaced so the shooter can confirm the parse before anything goes
 * to the board.
 */
data class CsvImport(
    val rows: List<DopeRow>,
    val base: Int,
    val step: Int,
    val rangeCol: String,
    val elevCol: String,
    val windCol: String?,
    val tofCol: String?,
    val rangeUnit: String,     // "yd" | "m"
    val elevUnit: String,      // "mil" | "moa" | "in" | "cm"
    val windUnit: String,
    val windMph: Double,       // wind speed the CSV wind column is at (scaled to full-value 10 mph)
    val srcMaxRangeYd: Int,
    val warnings: List<String>,
)

class CsvImportException(message: String) : Exception(message)

/**
 * Imports a GeoBallistics-style CSV export into the board's MILS dope tables.
 *
 * GeoBallistics doesn't publish its CSV schema and units vary between exports, so this is
 * deliberately DEFENSIVE: it finds the header row, auto-detects the range / elevation /
 * wind / time-of-flight columns by name, auto-detects each unit (yards or meters; mils,
 * MOA, inches or cm), converts everything to the firmware's MILS tables, and RESAMPLES
 * onto the board's base/step yard grid by linear interpolation (clamped at both ends, the
 * same way the firmware looks up dope). The caller shows the detected columns + a preview
 * so the shooter can confirm before sending. Nothing here reverse-engineers the app; it
 * only reads a file the user exported themselves.
 */
object CsvDopeImport {
    private const val MOA_PER_MIL = 3.43775
    private const val METERS_TO_YD = 1.09361

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    private fun isNumeric(s: String) = s.trim().toDoubleOrNull() != null

    private fun pickDelimiter(line: String): Char =
        listOf(',', ';', '\t').maxByOrNull { d -> line.count { it == d } } ?: ','

    private fun splitLine(line: String, delim: Char): List<String> =
        line.split(delim).map { it.trim().trim('"') }

    private fun detectCol(headers: List<String>, keys: List<String>): Int {
        headers.forEachIndexed { i, h -> val n = norm(h); if (keys.any { it in n }) return i }
        return -1
    }

    private fun rangeUnitOf(header: String): String {
        val n = norm(header); val low = header.lowercase()
        if ("yard" in n || Regex("\\byd\\b").containsMatchIn(low) || "yd" in n) return "yd"
        if ("meter" in n || "metre" in n || Regex("\\(m\\)|\\bm\\b").containsMatchIn(low)) return "m"
        return "yd"
    }

    private fun angUnitOf(header: String): String {
        val n = norm(header); val low = header.lowercase()
        if ("moa" in n) return "moa"
        if ("mil" in n || "mrad" in n) return "mil"
        if ("cm" in n) return "cm"
        if ("inch" in n || Regex("\\bin\\b").containsMatchIn(low) || n.endsWith("in")) return "in"
        return "mil"
    }

    private fun windMphOf(header: String, default: Int): Double {
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*mph").find(header.lowercase())
        return m?.groupValues?.get(1)?.toDoubleOrNull() ?: default.toDouble()
    }

    /** Any angular/linear drop value -> MILS of come-up (always positive). */
    private fun toMil(value: Double, unit: String, rangeYd: Int): Double = when (unit) {
        "mil" -> abs(value)
        "moa" -> abs(value) / MOA_PER_MIL
        else -> {
            val inches = if (unit == "cm") abs(value) / 2.54 else abs(value)
            if (rangeYd > 0) inches / (rangeYd * 0.036) else 0.0   // 1 mil subtends range_yd*0.036 in
        }
    }

    fun parse(
        text: String,
        windDef: Int,
        base: Int,
        step: Int,
        maxRows: Int,
        solverTof: (Int) -> Double,
    ): CsvImport {
        val warnings = ArrayList<String>()
        val lines = text.split('\n', '\r').filter { it.isNotBlank() }
        if (lines.isEmpty()) throw CsvImportException("The file is empty.")
        val delim = pickDelimiter(lines.first())
        val cells = lines.map { splitLine(it, delim) }

        val hdrIdx = cells.indexOfFirst { r -> r.count { it.isNotEmpty() && !isNumeric(it) } >= 2 }
        if (hdrIdx < 0) {
            throw CsvImportException("No header row with named columns was found. Export the CSV with a header row (Range, Elevation, Wind).")
        }
        val headers = cells[hdrIdx]
        val ri = detectCol(headers, listOf("range", "yard", "yd", "dist", "meter"))
        val ei = detectCol(headers, listOf("elev", "comeup", "come", "drop", "holdover", "updata", "dial"))
        val wi = detectCol(headers, listOf("wind", "drift", "windage"))
        val ti = detectCol(headers, listOf("tof", "timeofflight", "flighttime", "time"))
        if (ri < 0) throw CsvImportException("Couldn't find a Range column in the header.")
        if (ei < 0) throw CsvImportException("Couldn't find an Elevation / Drop column in the header.")

        val runit = rangeUnitOf(headers[ri])
        val eunit = angUnitOf(headers[ei])
        val wunit = if (wi >= 0) angUnitOf(headers[wi]) else "mil"
        val wmph = if (wi >= 0) windMphOf(headers[wi], windDef) else windDef.toDouble()
        if (wi < 0) warnings.add("No wind column found - wind holds set to 0.")
        if (ti < 0) warnings.add("No time-of-flight column - TOF filled from the computed solve (LEAD uses it).")
        if (wmph <= 0) throw CsvImportException("Wind speed read as $wmph mph - can't scale to the board's 10 mph column.")

        val rr = ArrayList<Int>(); val ee = ArrayList<Double>()
        val ww = ArrayList<Double?>(); val tt = ArrayList<Double?>()
        for (idx in (hdrIdx + 1) until cells.size) {
            val row = cells[idx]
            if (ri >= row.size || ei >= row.size) continue
            var rng = row[ri].toDoubleOrNull() ?: continue
            if (runit == "m") rng *= METERS_TO_YD
            val rngI = rng.roundToInt()
            if (rngI <= 0) continue
            val ev = row[ei].toDoubleOrNull() ?: continue
            rr.add(rngI); ee.add(toMil(ev, eunit, rngI))
            ww.add(if (wi in 0 until row.size) row[wi].toDoubleOrNull()?.let { toMil(it, wunit, rngI) * (10.0 / wmph) } else null)
            tt.add(if (ti in 0 until row.size) row[ti].toDoubleOrNull() else null)
        }
        if (rr.size < 2) throw CsvImportException("Found fewer than 2 data rows under the header. Check the file.")

        // sort the parallel arrays by range
        val order = rr.indices.sortedBy { rr[it] }
        val xr = order.map { rr[it] }
        val xe = order.map { ee[it] }
        val xw = order.map { ww[it] }
        val xt = order.map { tt[it] }
        val maxR = xr.last()

        fun interp(ys: List<Double?>): (Int) -> Double? {
            val pts = xr.indices.mapNotNull { k -> ys[k]?.let { xr[k] to it } }
            return fn@{ x: Int ->
                if (pts.isEmpty()) return@fn null
                if (x <= pts.first().first) return@fn pts.first().second
                if (x >= pts.last().first) return@fn pts.last().second
                for (j in 1 until pts.size) if (x <= pts[j].first) {
                    val (x0, y0) = pts[j - 1]; val (x1, y1) = pts[j]
                    return@fn y0 + (y1 - y0) * (x - x0) / (x1 - x0).toDouble()
                }
                pts.last().second
            }
        }
        val fe = interp(xe); val fw = interp(xw); val ftof = interp(xt)

        val out = ArrayList<DopeRow>()
        var n = 0
        while (true) {
            val r = base + step * n
            if (r > maxR || n >= maxRows) break
            if (r > 0) out.add(DopeRow(r, fe(r) ?: 0.0, fw(r) ?: 0.0, ftof(r) ?: solverTof(r)))
            n++
        }
        if (out.isEmpty()) throw CsvImportException("Couldn't build any rows on the $base/$step yd grid from this file.")

        return CsvImport(
            rows = out, base = base, step = step,
            rangeCol = headers[ri], elevCol = headers[ei],
            windCol = if (wi >= 0) headers[wi] else null,
            tofCol = if (ti >= 0) headers[ti] else null,
            rangeUnit = runit, elevUnit = eunit, windUnit = wunit, windMph = wmph,
            srcMaxRangeYd = maxR, warnings = warnings,
        )
    }
}
