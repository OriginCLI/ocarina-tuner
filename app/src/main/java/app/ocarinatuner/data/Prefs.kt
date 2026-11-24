package app.ocarinatuner.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var a4: Float
        get() = sp.getFloat("a4", 440f)
        set(v) = sp.edit().putFloat("a4", v).apply()

    /** Input level below which readings are hidden, in dBFS. */
    var gateDb: Float
        get() = sp.getFloat("gateDb", -50f)
        set(v) = sp.edit().putFloat("gateDb", v).apply()

    var tolerance: Float
        get() = sp.getFloat("tolerance", 10f)
        set(v) = sp.edit().putFloat("tolerance", v).apply()

    var showDebug: Boolean
        get() = sp.getBoolean("debugOverlay", false)
        set(v) = sp.edit().putBoolean("debugOverlay", v).apply()

    var skinId: String?
        get() = sp.getString("skin", null)
        set(v) = sp.edit().putString("skin", v).apply()
}
