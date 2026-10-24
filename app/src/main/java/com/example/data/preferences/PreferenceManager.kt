package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class AppThemeMode(val title: String, val description: String) {
    SYSTEM("System default", "Follows device light/dark appearance"),
    LIGHT("Fixed Light", "Consistent clean light appearance"),
    DARK("Fixed Dark", "Consistent sleek dark appearance")
}

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "clipnest_preferences"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_AUTO_CAPTURE = "auto_capture_on_resume"
        private const val KEY_PRIVACY_DISMISSED = "privacy_banner_dismissed"
        private const val KEY_LAST_CLEANUP = "last_cleanup_timestamp"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "app_dynamic_color"

        const val RETENTION_NEVER = -1
        const val RETENTION_1_DAY = 1
        const val RETENTION_7_DAYS = 7
        const val RETENTION_30_DAYS = 30
    }

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, RETENTION_7_DAYS)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    var autoCaptureOnResume: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPTURE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPTURE, value).apply()

    var isPrivacyBannerDismissed: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_DISMISSED, value).apply()

    var lastCleanupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CLEANUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CLEANUP, value).apply()

    var themeMode: AppThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            return try {
                AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name)
            } catch (_: Exception) {
                AppThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
}
