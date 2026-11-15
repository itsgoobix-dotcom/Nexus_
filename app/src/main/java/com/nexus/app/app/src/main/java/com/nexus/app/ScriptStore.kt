package com.nexus.app

import android.content.Context

object ScriptStore {
    private fun prefs(c: Context) = c.getSharedPreferences("scripts", Context.MODE_PRIVATE)

    fun save(c: Context, name: String, code: String) {
        prefs(c).edit().putString(name, code).apply()
    }

    fun names(c: Context): List<String> = prefs(c).all.keys.sorted()

    fun get(c: Context, name: String): String? = prefs(c).getString(name, null)

    fun loadAll(c: Context) {
        for ((name, code) in prefs(c).all) {
            if (code is String) ScriptEngine.run(code, name)
        }
    }
}
