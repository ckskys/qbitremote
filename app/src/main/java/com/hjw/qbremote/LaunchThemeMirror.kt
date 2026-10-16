package com.hjw.qbremote

import androidx.core.content.edit
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * Synchronous copy of "does the app currently use its dark palette".
 *
 * The real theme setting lives in DataStore, which can't be read before the first
 * frame. Without this, the launch window/splash followed the *system* day/night
 * mode, so a phone in light mode showed a white window before the app's dark UI
 * (and vice versa).
 */
internal object LaunchThemeMirror {
    private const val PREFS = "qb_launch_theme"
    private const val KEY_DARK = "dark"
    private const val KEY_APPLIED_SYSTEM_MODE = "applied_system_night_mode"

    /** Call before super.onCreate so the window background matches the app theme. */
    fun applyBeforeCreate(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dark = prefs.getBoolean(KEY_DARK, true) // AppTheme.DARK is the default
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Lets the system-drawn splash (shown before our process runs) use the
            // matching night resources on the next cold start.
            val mode = if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            if (prefs.getInt(KEY_APPLIED_SYSTEM_MODE, -1) != mode) {
                activity.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
                prefs.edit { putInt(KEY_APPLIED_SYSTEM_MODE, mode) }
            }
        }
    }

    /** Records the palette the UI resolved to; picked up on the next launch. */
    fun save(context: Context, dark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DARK) && prefs.getBoolean(KEY_DARK, true) == dark) return
        prefs.edit { putBoolean(KEY_DARK, dark) }
    }
}
