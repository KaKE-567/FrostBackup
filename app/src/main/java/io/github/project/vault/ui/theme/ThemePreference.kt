package io.github.project.vault.ui.theme

import android.content.Context

enum class ThemeMode(val title: String) {
    SYSTEM("Follow System"),
    LIGHT("Light (White Mode)"),
    DARK("Dark Mode")
}

object ThemePreference {
    private const val PREFS_NAME = "vault_settings_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
