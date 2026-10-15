package com.rifledope.config.data

import android.content.Context
import com.rifledope.config.model.Profile
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's rifle/load profiles (and which one is active) in SharedPreferences. */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rifle_profiles", Context.MODE_PRIVATE)

    private fun toJson(p: Profile) = JSONObject().apply {
        put("name", p.name); put("unit", p.unit); put("team", p.team)
        put("mv", p.mv); put("bc", p.bc); put("bcModel", p.bcModel)
        put("zero", p.zero); put("sightHt", p.sightHt); put("twist", p.twist)
        put("tempF", p.tempF); put("pressInHg", p.pressInHg); put("altFt", p.altFt)
        put("humPct", p.humPct); put("windDef", p.windDef); put("units", p.units)
        put("base", p.base); put("step", p.step); put("rows", p.rows)
    }

    private fun fromJson(o: JSONObject) = Profile(
        name = o.optString("name", "Rifle"),
        unit = o.optInt("unit", 1), team = o.optInt("team", 7),
        mv = o.optInt("mv", 2830), bc = o.optDouble("bc", 0.315), bcModel = o.optString("bcModel", "G7"),
        zero = o.optInt("zero", 100), sightHt = o.optDouble("sightHt", 1.8), twist = o.optDouble("twist", 8.0),
        tempF = o.optInt("tempF", 59), pressInHg = o.optDouble("pressInHg", 29.92),
        altFt = o.optInt("altFt", 0), humPct = o.optInt("humPct", 50),
        windDef = o.optInt("windDef", 10), units = o.optString("units", "mil"),
        base = o.optInt("base", 100), step = o.optInt("step", 50), rows = o.optInt("rows", 23),
    )

    fun load(): MutableList<Profile> {
        val raw = prefs.getString("json", null) ?: return mutableListOf(Profile())
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Profile>(arr.length())
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
            if (out.isEmpty()) mutableListOf(Profile()) else out
        } catch (_: Exception) { mutableListOf(Profile()) }
    }

    fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("json", arr.toString()).apply()
    }

    fun activeIndex(): Int = prefs.getInt("active", 0)
    fun setActiveIndex(i: Int) = prefs.edit().putInt("active", i).apply()
}
