package com.rifledope.config.ballistics

/** Standard G1 / G7 drag coefficient tables (Mach -> Cd), BRL standard projectiles. */
object DragTables {
    val G7: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.00, 0.1198), doubleArrayOf(0.05, 0.1197), doubleArrayOf(0.10, 0.1196),
        doubleArrayOf(0.15, 0.1194), doubleArrayOf(0.20, 0.1193), doubleArrayOf(0.25, 0.1194),
        doubleArrayOf(0.30, 0.1194), doubleArrayOf(0.35, 0.1193), doubleArrayOf(0.40, 0.1193),
        doubleArrayOf(0.45, 0.1193), doubleArrayOf(0.50, 0.1194), doubleArrayOf(0.55, 0.1193),
        doubleArrayOf(0.60, 0.1194), doubleArrayOf(0.65, 0.1197), doubleArrayOf(0.70, 0.1202),
        doubleArrayOf(0.725, 0.1207), doubleArrayOf(0.75, 0.1215), doubleArrayOf(0.775, 0.1226),
        doubleArrayOf(0.80, 0.1242), doubleArrayOf(0.825, 0.1266), doubleArrayOf(0.85, 0.1306),
        doubleArrayOf(0.875, 0.1368), doubleArrayOf(0.90, 0.1464), doubleArrayOf(0.925, 0.1660),
        doubleArrayOf(0.95, 0.2054), doubleArrayOf(0.975, 0.2993), doubleArrayOf(1.0, 0.3803),
        doubleArrayOf(1.025, 0.4015), doubleArrayOf(1.05, 0.4043), doubleArrayOf(1.075, 0.4034),
        doubleArrayOf(1.10, 0.4014), doubleArrayOf(1.125, 0.3987), doubleArrayOf(1.15, 0.3955),
        doubleArrayOf(1.20, 0.3884), doubleArrayOf(1.25, 0.3810), doubleArrayOf(1.30, 0.3732),
        doubleArrayOf(1.35, 0.3657), doubleArrayOf(1.40, 0.3580), doubleArrayOf(1.50, 0.3440),
        doubleArrayOf(1.55, 0.3376), doubleArrayOf(1.60, 0.3315), doubleArrayOf(1.65, 0.3260),
        doubleArrayOf(1.70, 0.3209), doubleArrayOf(1.75, 0.3160), doubleArrayOf(1.80, 0.3117),
        doubleArrayOf(1.85, 0.3078), doubleArrayOf(1.90, 0.3042), doubleArrayOf(1.95, 0.3010),
        doubleArrayOf(2.00, 0.2980), doubleArrayOf(2.05, 0.2951), doubleArrayOf(2.10, 0.2922),
        doubleArrayOf(2.15, 0.2892), doubleArrayOf(2.20, 0.2864), doubleArrayOf(2.25, 0.2835),
        doubleArrayOf(2.30, 0.2807), doubleArrayOf(2.35, 0.2779), doubleArrayOf(2.40, 0.2752),
        doubleArrayOf(2.45, 0.2725), doubleArrayOf(2.50, 0.2697), doubleArrayOf(2.60, 0.2643),
        doubleArrayOf(2.70, 0.2592), doubleArrayOf(2.80, 0.2545), doubleArrayOf(2.90, 0.2502),
        doubleArrayOf(3.00, 0.2463),
    )

    val G1: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.00, 0.2629), doubleArrayOf(0.05, 0.2558), doubleArrayOf(0.10, 0.2487),
        doubleArrayOf(0.15, 0.2413), doubleArrayOf(0.20, 0.2344), doubleArrayOf(0.25, 0.2278),
        doubleArrayOf(0.30, 0.2214), doubleArrayOf(0.35, 0.2155), doubleArrayOf(0.40, 0.2104),
        doubleArrayOf(0.45, 0.2061), doubleArrayOf(0.50, 0.2032), doubleArrayOf(0.55, 0.2020),
        doubleArrayOf(0.60, 0.2034), doubleArrayOf(0.70, 0.2165), doubleArrayOf(0.725, 0.2230),
        doubleArrayOf(0.75, 0.2313), doubleArrayOf(0.775, 0.2417), doubleArrayOf(0.80, 0.2546),
        doubleArrayOf(0.825, 0.2706), doubleArrayOf(0.85, 0.2901), doubleArrayOf(0.875, 0.3136),
        doubleArrayOf(0.90, 0.3415), doubleArrayOf(0.925, 0.3734), doubleArrayOf(0.95, 0.4084),
        doubleArrayOf(0.975, 0.4448), doubleArrayOf(1.0, 0.4805), doubleArrayOf(1.025, 0.5136),
        doubleArrayOf(1.05, 0.5427), doubleArrayOf(1.075, 0.5677), doubleArrayOf(1.10, 0.5883),
        doubleArrayOf(1.125, 0.6053), doubleArrayOf(1.15, 0.6191), doubleArrayOf(1.20, 0.6393),
        doubleArrayOf(1.25, 0.6518), doubleArrayOf(1.30, 0.6589), doubleArrayOf(1.35, 0.6621),
        doubleArrayOf(1.40, 0.6625), doubleArrayOf(1.50, 0.6573), doubleArrayOf(1.60, 0.6472),
        doubleArrayOf(1.70, 0.6342), doubleArrayOf(1.80, 0.6200), doubleArrayOf(1.90, 0.6054),
        doubleArrayOf(2.00, 0.5909), doubleArrayOf(2.10, 0.5769), doubleArrayOf(2.20, 0.5635),
        doubleArrayOf(2.30, 0.5508), doubleArrayOf(2.40, 0.5389), doubleArrayOf(2.50, 0.5278),
        doubleArrayOf(2.60, 0.5175), doubleArrayOf(2.70, 0.5080), doubleArrayOf(2.80, 0.4992),
        doubleArrayOf(2.90, 0.4911), doubleArrayOf(3.00, 0.4836),
    )

    fun cd(table: Array<DoubleArray>, mach: Double): Double {
        if (mach <= table.first()[0]) return table.first()[1]
        if (mach >= table.last()[0]) return table.last()[1]
        for (i in 0 until table.size - 1) {
            val m0 = table[i][0]; val m1 = table[i + 1][0]
            if (mach in m0..m1) {
                val c0 = table[i][1]; val c1 = table[i + 1][1]
                return c0 + (c1 - c0) * (mach - m0) / (m1 - m0)
            }
        }
        return table.last()[1]
    }
}
