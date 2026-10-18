package com.tmksdm.ritmo

import android.content.Context
import android.content.res.Configuration

/** Вариант оформления приложения. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** Хранение и применение выбранной темы. */
object ThemePrefs {

    private const val PREFS = "ritmo_settings"
    private const val KEY_THEME = "theme_mode"

    fun mode(context: Context): ThemeMode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return when (stored) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }

    /** Ресурс темы, который нужно применить к активности до setContentView(). */
    fun themeResId(context: Context): Int = when (mode(context)) {
        ThemeMode.LIGHT -> R.style.Theme_Ritmo
        ThemeMode.DARK -> R.style.Theme_RitmoDark
        ThemeMode.SYSTEM -> if (isSystemNight(context)) R.style.Theme_RitmoDark else R.style.Theme_Ritmo
    }

    private fun isSystemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
