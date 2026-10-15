package com.rifledope.config.model

import com.rifledope.config.ballistics.SolveInput

/** Everything the phone holds for one rifle/load, and pushes to a board. */
data class Profile(
    var name: String = "6.5 CM 145 Match",
    var unit: Int = 1,              // board unit number 1/2
    var team: Int = 7,
    var mv: Int = 2830,             // fps
    var bc: Double = 0.315,
    var bcModel: String = "G7",     // G7 / G1
    var zero: Int = 100,            // yd
    var sightHt: Double = 1.80,     // in
    var twist: Double = 8.0,        // in/rev
    var tempF: Int = 59,
    var pressInHg: Double = 29.92,
    var altFt: Int = 0,
    var humPct: Int = 50,
    var windDef: Int = 10,          // default full-value wind, mph (WIND page default)
    var units: String = "mil",
    var base: Int = 100,
    var step: Int = 50,
    var rows: Int = 23,
) {
    fun solveInput() = SolveInput(
        model = bcModel, bc = bc, mvFps = mv.toDouble(), sightHeightIn = sightHt,
        zeroYd = zero, tempF = tempF.toDouble(), pressInHg = pressInHg, humPct = humPct.toDouble(),
        windMph = windDef.toDouble(), base = base, step = step, n = rows,
    )
}

/** Live status read back from a board (STATUS / ST reply). */
data class BoardStatus(
    val fw: String = "",
    val unit: Int = 0,
    val team: Int = 0,
    val linked: Boolean = false,
    val partnerHdg: Int = -1,
    val battery: Int = -1,
)
