package com.rifledope.config.ballistics

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** One computed dope row. elev/wind are in MILS at full-value 10mph 90deg; tof seconds. */
data class DopeRow(val yd: Int, val elevMil: Double, val windMil: Double, val tof: Double)

data class SolveInput(
    val model: String,       // "G7" or "G1"
    val bc: Double,          // ballistic coefficient (lb/in^2)
    val mvFps: Double,       // muzzle velocity, fps
    val sightHeightIn: Double,
    val zeroYd: Int,
    val tempF: Double,
    val pressInHg: Double,
    val humPct: Double,
    val windMph: Double,     // the reference wind the wind column is built at (full value, 90deg)
    val base: Int,
    val step: Int,
    val n: Int,
)

/**
 * G1/G7 point-mass trajectory solver. Validated against py_ballisticcalc-derived
 * reference dope for 6.5 CM 145gr G7 0.315 @ 2830 (max elevation error < 0.005 mil).
 */
object Solver {
    private const val C = 2.092e-4            // drag constant (BC in lb/in^2, v in fps)
    private const val GRAV = 32.174           // ft/s^2
    private const val STD_TEMP_F = 59.0
    private const val STD_PRESS_INHG = 29.92
    private const val DT = 0.0005

    private fun densityRatio(tempF: Double, pressInHg: Double, humPct: Double): Double {
        val tR = tempF + 459.67
        val tStdR = STD_TEMP_F + 459.67
        var dr = (pressInHg / STD_PRESS_INHG) * (tStdR / tR)
        val tC = (tempF - 32.0) * 5.0 / 9.0
        val svpMm = 4.5841 * exp((18.687 - tC / 234.5) * tC / (257.14 + tC))
        val svpInHg = svpMm / 25.4
        val vp = (humPct / 100.0) * svpInHg
        if (pressInHg > 0) dr *= (1.0 - 0.378 * vp / pressInHg)
        return dr
    }

    private fun speedOfSound(tempF: Double): Double = 49.0223 * sqrt(tempF + 459.67)

    // returns map range_yd -> Triple(pathHeightInches rel LOS, tof, windageInches)
    private fun trajectory(
        table: Array<DoubleArray>, bc: Double, mv: Double, sightHeightIn: Double, launchRad: Double,
        tempF: Double, pressInHg: Double, humPct: Double, windMph: Double, windAngleDeg: Double,
        ranges: List<Int>,
    ): Map<Int, Triple<Double, Double, Double>> {
        val dens = densityRatio(tempF, pressInHg, humPct)
        val sos = speedOfSound(tempF)
        val windFps = windMph * 1.46667
        val cross = windFps * sin(Math.toRadians(windAngleDeg))
        val rangeW = windFps * cos(Math.toRadians(windAngleDeg))
        var x = 0.0; var y = -sightHeightIn / 12.0; var z = 0.0
        var vx = mv * cos(launchRad); var vy = mv * sin(launchRad); var vz = 0.0
        var t = 0.0
        val targets = ranges.sorted()
        val out = HashMap<Int, Triple<Double, Double, Double>>()
        var ti = 0
        val maxX = targets.last() * 3.0 + 10
        while (x < maxX && ti < targets.size) {
            val rvx = vx - rangeW; val rvy = vy; val rvz = vz - cross
            val v = sqrt(rvx * rvx + rvy * rvy + rvz * rvz)
            val mach = v / sos
            val cd = DragTables.cd(table, mach)
            val k = C * dens * cd * v / bc
            val ax = -k * rvx
            val ay = -k * rvy - GRAV
            val az = -k * rvz
            val nx = x + vx * DT + 0.5 * ax * DT * DT
            val ny = y + vy * DT + 0.5 * ay * DT * DT
            val nz = z + vz * DT + 0.5 * az * DT * DT
            vx += ax * DT; vy += ay * DT; vz += az * DT
            while (ti < targets.size && nx >= targets[ti] * 3.0) {
                val xr = targets[ti] * 3.0
                val frac = if (nx != x) (xr - x) / (nx - x) else 0.0
                val yr = y + (ny - y) * frac
                val zr = z + (nz - z) * frac
                val tr = t + DT * frac
                out[targets[ti]] = Triple(yr * 12.0, tr, zr * 12.0)
                ti++
            }
            x = nx; y = ny; z = nz; t += DT
        }
        return out
    }

    private fun solveZero(
        table: Array<DoubleArray>, bc: Double, mv: Double, sh: Double, zeroYd: Int,
        tempF: Double, pressInHg: Double, humPct: Double,
    ): Double {
        var lo = -0.02; var hi = 0.05
        repeat(60) {
            val mid = (lo + hi) / 2
            val h = trajectory(table, bc, mv, sh, mid, tempF, pressInHg, humPct, 0.0, 0.0, listOf(zeroYd))[zeroYd]?.first ?: 0.0
            if (h > 0) hi = mid else lo = mid
        }
        return (lo + hi) / 2
    }

    fun solve(inp: SolveInput): List<DopeRow> {
        val table = if (inp.model.equals("G1", true)) DragTables.G1 else DragTables.G7
        val ranges = (0 until inp.n).map { inp.base + inp.step * it }.filter { it > 0 }
        if (ranges.isEmpty()) return emptyList()
        val ang = solveZero(table, inp.bc, inp.mvFps, inp.sightHeightIn, inp.zeroYd, inp.tempF, inp.pressInHg, inp.humPct)
        // The board's wind column is the full-value 10 mph 90deg reference; a pure crosswind
        // does not change elevation, so one run gives both come-up and wind hold.
        val tr = trajectory(table, inp.bc, inp.mvFps, inp.sightHeightIn, ang, inp.tempF, inp.pressInHg, inp.humPct, 10.0, 90.0, ranges)
        return ranges.map { r ->
            val cell = tr[r]
            if (cell == null) DopeRow(r, 0.0, 0.0, 0.0)
            else {
                val rngIn = r * 36.0
                val elev = if (rngIn > 0) -cell.first / rngIn * 1000.0 else 0.0
                val wind10 = if (rngIn > 0) kotlin.math.abs(cell.third) / rngIn * 1000.0 else 0.0
                DopeRow(r, elev, wind10, cell.second)
            }
        }
    }
}
