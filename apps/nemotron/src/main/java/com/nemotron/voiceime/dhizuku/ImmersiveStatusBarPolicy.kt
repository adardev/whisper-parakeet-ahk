package com.nemotron.voiceime.dhizuku

import android.content.Context

/** Hide status bars system-wide; patched One UI Home shows it only on the normal home screen. */
object ImmersiveStatusBarPolicy {
    const val POLICY_CONTROL = "policy_control"
    const val ENABLED_VALUE = "immersive.status=apps"
    const val HOME_VISIBLE_VALUE = "immersive.status=apps,-com.sec.android.app.launcher"

    private const val PREFS = "immersive_status_policy"
    private const val KEY_HAS_PREVIOUS = "has_previous_policy_control"
    private const val KEY_PREVIOUS = "previous_policy_control"

    fun readCurrent(): String? {
        if (!RootShellManager.hasPermission()) return null
        return RootShellManager.execShellFresh(
            arrayOf("settings", "get", "global", POLICY_CONTROL)
        )?.trim()
    }

    fun isEnabled(context: Context): Boolean {
        val current = readCurrent()
        return if (current != null) current == ENABLED_VALUE || current == HOME_VISIBLE_VALUE
        else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("last_enabled", false)
    }

    /** Changes only the persistent system setting; it starts no service or background worker. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!RootShellManager.hasPermission()) return false
        val current = readCurrent() ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (enabled) {
            if (current == ENABLED_VALUE || current == HOME_VISIBLE_VALUE) {
                prefs.edit().putBoolean("last_enabled", true).apply()
                return true
            }
            if (!prefs.getBoolean(KEY_HAS_PREVIOUS, false)) {
                prefs.edit()
                    .putBoolean(KEY_HAS_PREVIOUS, true)
                    .putString(KEY_PREVIOUS, current)
                    .apply()
            }
            val result = RootShellManager.execShellFresh(
                arrayOf("settings", "put", "global", POLICY_CONTROL, ENABLED_VALUE)
            ) ?: return false
            val success = result.isNotBlank() && readCurrent() == ENABLED_VALUE
            if (success) prefs.edit().putBoolean("last_enabled", true).apply()
            return success
        }

        if (current != ENABLED_VALUE && current != HOME_VISIBLE_VALUE) {
            prefs.edit().putBoolean("last_enabled", false).apply()
            return true
        }

        val hasPrevious = prefs.getBoolean(KEY_HAS_PREVIOUS, false)
        val previous = prefs.getString(KEY_PREVIOUS, "null") ?: "null"
        val command = if (!hasPrevious || previous == "null") {
            arrayOf("settings", "delete", "global", POLICY_CONTROL)
        } else {
            arrayOf("settings", "put", "global", POLICY_CONTROL, previous)
        }
        val result = RootShellManager.execShellFresh(command) ?: return false
        val expected = if (!hasPrevious) "null" else previous
        val success = result.isNotBlank() && readCurrent() == expected
        if (success) {
            prefs.edit()
                .remove(KEY_HAS_PREVIOUS)
                .remove(KEY_PREVIOUS)
                .putBoolean("last_enabled", false)
                .apply()
        }
        return success
    }
}
