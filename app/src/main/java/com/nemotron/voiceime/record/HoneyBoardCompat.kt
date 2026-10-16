package com.nemotron.voiceime.record

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log

/** Compatibility for Samsung HoneyBoard settings that are hidden from regular app UIDs. */
object HoneyBoardCompat {
    private const val TAG = "HoneyBoardCompat"

    @JvmStatic
    fun getSecureInt(resolver: ContentResolver, name: String, defaultValue: Int): Int = try {
        Settings.Secure.getInt(resolver, name, defaultValue)
    } catch (error: SecurityException) {
        Log.d(TAG, "Using default for Samsung-only secure setting: $name", error)
        defaultValue
    }
}
