package com.rifledope.config.ballistics

import kotlin.math.roundToInt

/**
 * Result of importing a GeoBallistics-style CSV. [rows] are already resampled onto the board's
 * base/step yard grid and converted to MILS, ready to send. The detected column names + units and
 * the wind/TOF source are surfaced so the shooter can confirm the parse before anything goes to
 * the board.
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
    val windComputed: Boolean, // true: wind holds came from the solver, not the CSV
    val tofComputed: Boolean,  // true: TOF came from the solver, not the CSV
    val srcMaxRangeYd: Int,
    val warnings: List<String>,
)

class CsvImportException(message: String) : Exception(message)

/**
 * Imports a GeoBallistics-style CSV export into the board's MILS dope tables.
 *
 * GeoBallistics doesn't publish its CSV schema and exports vary, so this is deliberately
 * DEFENSIVE. It skips the metadata preamble (Rifle/Position/Wind/Pressure... rows) by taking the
 * first row that has BOTH a range and an elevation column by name; auto-detects the range /
 * elevation / wind / time-of-flight columns and units (yards or meters; mils, MOA, inches or cm);
 * parses GeoBallistics' direction-prefixed values ("U 0.2" up, "D" down, "R 0.3" right, "L" left);
 * converts elevation to the firmware's MILS; and RESAMPLES onto the board's base/step yard grid by
 * clamped linear interpolation (the same way the firmware looks up dope).
 *
 * ELEVATION is taken from the file. WIND is intentionally NOT taken from the file: GeoBallistics
 * exports wind holds for the exact wind vector you entered (e.g. 10.6 mph @ 40.5 deg), not the
 * 10 mph full-value crosswind the board's WIND column stores, so importing it as-is would be
 * silently wrong once the board scales it. Wind (and TOF, which GeoBallistics doesn't export) are
 * filled from the on-phone solve, and the caller shows this + a preview so the shooter can confirm.
 * Nothing here reverse-engineers the app; it only reads a file the user exported themselves.
 */
object CsvDopeImport {
    private const val MOA_PER_MIL = 3.43775
    private const val METERS_TO_YD = 1.09361

    private val RANGE_KEYS = listOf("range", "yard", "yd", "dist", "meter")
    private val ELEV_KEYS = listOf("elev", "comeup", "come", "drop", "holdover", "updata", "dial")
    private val WIND_KEYS = listOf("wind", "drift", "windage")
    private val TOF_KEYS = listOf("tof", "timeofflight", "flighttime", "time")

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun splitLine(line: String, delim: Char): List<String> =
        line.split(delim).map { it.trim().trim('"') }

    private fun detectCol(headers: List<String>, keys: List<String>): Int {
        headers.forEachIndexed { i, h -> val n = norm(h); if (keys.any { it in n }) return i }
        return -1
    }

    /** GeoBallistics values carry a direction letter: "U 0.2"/"D 0.1" (up/down, signed) and
     *  "R 0.3"/"L 0.2" (right/left). Returns the signed number; a plain number keeps its own sign. */
    private fun numOf(cell: String): Double? {
        val s = cell.trim()
        if (s.isEmpty()) return null
        val m = Regex("^([UDLRudlr])\\s*([-+]?[0-9]*\\.?[0-9]+)$").find(s)
        if (m != null) {
            val sign = if (m.groupValues[1].uppercase() in listOf("D", "L")) -1.0 else 1.0
            return (m.groupValues[2].toDoubleOrNull() ?: return null) * sign
        }
        return s.toDoubleOrNull()
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

    /** True if the header actually names an angular/linear unit, so a defaulted "mil" gets flagged. */
    private fun angLabeled(header: String): Boolean {
        val n = norm(header); val low = header.lowercase()
        return "moa" in n || "mil" in n || "mrad" in n || "cm" in n ||
            "inch" in n || Regex("\\bin\\b").containsMatchIn(low) || n.endsWith("in")
    }

    /** Signed angular/linear come-up -> MILS (sign preserved: up/right positive, down/left negative). */
    private fun toMil(value: Double, unit: String, rangeYd: Int): Double = when (unit) {
        "mil" -> value
        "moa" -> value / MOA_PER_MIL
        else -> {
            val inches = if (unit == "cm") value / 2.54 else value
            if (rangeYd > 0) inches / (rangeYd * 0.036) else 0.0   // 1 mil subtends range_yd*0.036 in
        }
    }

    /** Clamped linear interpolation over (x,y) points, the way the firmware looks up dope. */
    private fun interp(xs: List<Int>, ys: List<Double>): (Int) -> Double? {
        val pts = xs.indices.map { xs[it] to ys[it] }.sortedBy { it.first }
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

    fun parse(
        text: String,
        base: Int,
        step: Int,
        maxRows: Int,
        solved: List<DopeRow>,
    ): CsvImport {
        val warnings = ArrayList<String>()
        val lines = text.split('\n', '\r').filter { it.isNotBlank() }
        if (lines.isEmpty()) throw CsvImportException("The file is empty.")
        // Pick the delimiter that appears most across the whole file (robust to the preamble).
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> lines.sumOf { line -> line.count { it == d } } } ?: ','
        val cells = lines.map { splitLine(it, delim) }

        // Header = the first row that has BOTH a range and an elevation column by name. This skips
        // GeoBallistics' metadata preamble (Rifle/Position/Wind/Pressure... rows), which also have
        // comma-separated non-numeric cells.
        var hdrIdx = -1
        for (i in cells.indices) {
            if (detectCol(cells[i], RANGE_KEYS) >= 0 && detectCol(cells[i], ELEV_KEYS) >= 0) { hdrIdx = i; break }
        }
        if (hdrIdx < 0) throw CsvImportException(
            "No header row with Range and Elevation columns was found. Export the ballistics table with column headers.")
        val headers = cells[hdrIdx]
        val ri = detectCol(headers, RANGE_KEYS)
        val ei = detectCol(headers, ELEV_KEYS)
        val wi = detectCol(headers, WIND_KEYS)
        val ti = detectCol(headers, TOF_KEYS)

        val runit = rangeUnitOf(headers[ri])
        val eunit = angUnitOf(headers[ei])
        if (!angLabeled(headers[ei])) warnings.add(
            "Elevation column \"" + headers[ei] + "\" has no unit label - assumed MIL. " +
                "If your dope is in MOA or inches it will read wrong here; check the preview.")

        // Elevation from the file; wind + TOF from the solver (see the class note on wind).
        val rr = ArrayList<Int>(); val ee = ArrayList<Double>()
        for (idx in (hdrIdx + 1) until cells.size) {
            val row = cells[idx]
            if (ri >= row.size || ei >= row.size) continue
            var rng = numOf(row[ri]) ?: continue
            if (runit == "m") rng *= METERS_TO_YD
            val rngI = rng.roundToInt()
            if (rngI <= 0) continue
            val ev = numOf(row[ei]) ?: continue
            rr.add(rngI); ee.add(toMil(ev, eunit, rngI))
        }
        if (rr.size < 2) throw CsvImportException(
            "Found fewer than 2 data rows under the header. Check the file.")

        val fe = interp(rr, ee)
        val fw = interp(solved.map { it.yd }, solved.map { it.windMil })
        val ft = interp(solved.map { it.yd }, solved.map { it.tof })
        val maxR = rr.max()

        val windMeta = cells.take(hdrIdx)
            .firstOrNull { it.isNotEmpty() && norm(it[0]) == "wind" && it.size > 1 }?.get(1)?.trim()
        warnings.add(
            "Wind holds are COMPUTED, not from the file" +
                (if (windMeta != null) " (your export's wind was $windMeta)" else "") +
                ": GeoBallistics exports wind for the exact conditions you entered, not the 10 mph " +
                "full-value crosswind the board's WIND column needs. For true wind dope, re-export " +
                "with a 10 mph 90-degree wind and tell me.")
        warnings.add("Time of flight is computed (GeoBallistics doesn't export it); LEAD uses it.")

        val out = ArrayList<DopeRow>()
        var n = 0
        while (true) {
            val r = base + step * n
            if (r > maxR || n >= maxRows) break
            if (r > 0) out.add(DopeRow(r, fe(r) ?: 0.0, fw(r) ?: 0.0, ft(r) ?: 0.0))
            n++
        }
        if (out.isEmpty()) throw CsvImportException(
            "Couldn't build any rows on the $base/$step yd grid from this file.")
        if (maxR < base + step * (maxRows - 1)) warnings.add(
            "Your export reaches $maxR yd, so the board gets dope to $maxR yd; farther ranges have none.")

        return CsvImport(
            rows = out, base = base, step = step,
            rangeCol = headers[ri], elevCol = headers[ei],
            windCol = if (wi >= 0) headers[wi] else null,
            tofCol = if (ti >= 0) headers[ti] else null,
            rangeUnit = runit, elevUnit = eunit,
            windComputed = true, tofComputed = ti < 0,
            srcMaxRangeYd = maxR, warnings = warnings,
        )
    }
}
