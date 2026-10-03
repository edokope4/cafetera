package com.cafetera

import android.content.Context

object CoffeeReady {
    private const val PREFS = "cafetera"
    private const val KEY = "coffee_ready"

    fun mark(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().putBoolean(KEY, false).apply()
    }

    fun isMarked(context: Context): Boolean {
        return prefs(context).getBoolean(KEY, false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
