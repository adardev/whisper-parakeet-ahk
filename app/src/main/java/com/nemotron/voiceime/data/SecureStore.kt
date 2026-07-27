package com.nemotron.voiceime.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

object SecureStore {

    private const val SECURE_FILE = "nemotron_secure_prefs"
    private const val PLAIN_FILE = "nemotron_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_LOCALE = "dict_locale"
    private const val KEY_FROZEN_APPS = "frozen_apps"
    private const val KEY_AUTO_FREEZE_APPS = "auto_freeze_apps"
    private const val KEY_AUTO_FREEZE_ENABLED = "auto_freeze_enabled"

    private const val DEFAULT_MODEL = "nvidia/nemotron-3-nano-30b-a3b"
    private const val DEFAULT_LOCALE = "es_ES"
    private val DEFAULT_SYSTEM_PROMPT = buildString {
        append("Eres un transcriptor de voz a texto. ")
        append("El usuario dicta en su idioma. ")
        append("Devuelve SOLO la transcripción limpia, sin comillas, sin markdown, sin explicaciones. ")
        append("Corrige puntuación y mayúsculas automáticamente. ")
        append("No añadas texto extra ni comentaries lo que haces.")
    }

    private fun securePrefs(ctx: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, SECURE_FILE, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        ctx.getSharedPreferences("${SECURE_FILE}_plain", Context.MODE_PRIVATE)
    }

    private fun plainPrefs(ctx: Context): SharedPreferences {
        val prefs = ctx.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("migrated", false)) {
            migrateFromOld(prefs, ctx)
        }
        return prefs
    }

    private fun migrateFromOld(newPrefs: SharedPreferences, ctx: Context) {
        try {
            val mk = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val old = EncryptedSharedPreferences.create(
                ctx, SECURE_FILE, mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val editor = newPrefs.edit()
            old.all?.forEach { (k, v) ->
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                }
            }
            editor.putBoolean("migrated", true).apply()
        } catch (_: Throwable) {
            try {
                val old = ctx.getSharedPreferences("${SECURE_FILE}_plain", Context.MODE_PRIVATE)
                val editor = newPrefs.edit()
                old.all?.forEach { (k, v) ->
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                    }
                }
                editor.putBoolean("migrated", true).apply()
            } catch (_: Throwable) {}
        }
    }

    fun getApiKey(ctx: Context): String =
        securePrefs(ctx).getString(KEY_API_KEY, "").orEmpty()

    fun setApiKey(ctx: Context, value: String) {
        securePrefs(ctx).edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    fun getModel(ctx: Context): String =
        plainPrefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun getSystemPrompt(ctx: Context): String =
        plainPrefs(ctx).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
            ?: DEFAULT_SYSTEM_PROMPT

    fun getLocale(ctx: Context): String =
        plainPrefs(ctx).getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE

    // ── Frozen apps (tile list) ─────────────────────────────────────────

    fun getFrozenApps(ctx: Context): Set<String> {
        val json = plainPrefs(ctx).getString(KEY_FROZEN_APPS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun setFrozenApps(ctx: Context, apps: Set<String>) {
        val json = JSONArray(apps.toList()).toString()
        plainPrefs(ctx).edit().putString(KEY_FROZEN_APPS, json).apply()
    }

    fun addFrozenApp(ctx: Context, pkg: String) {
        val current = getFrozenApps(ctx).toMutableSet()
        current.add(pkg)
        setFrozenApps(ctx, current)
    }

    fun removeFrozenApp(ctx: Context, pkg: String) {
        val current = getFrozenApps(ctx).toMutableSet()
        current.remove(pkg)
        setFrozenApps(ctx, current)
    }

    fun isAppFrozen(ctx: Context, pkg: String): Boolean =
        getFrozenApps(ctx).contains(pkg)

    fun getFrozenAppLabels(ctx: Context): List<String> {
        val pm = ctx.packageManager
        return getFrozenApps(ctx).map { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }

    // ── Auto-freeze apps (screen off/on list) ──────────────────────────

    fun getAutoFreezeApps(ctx: Context): Set<String> {
        val json = plainPrefs(ctx).getString(KEY_AUTO_FREEZE_APPS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun setAutoFreezeApps(ctx: Context, apps: Set<String>) {
        val json = JSONArray(apps.toList()).toString()
        plainPrefs(ctx).edit().putString(KEY_AUTO_FREEZE_APPS, json).apply()
    }

    fun isAutoFreezeEnabled(ctx: Context): Boolean =
        plainPrefs(ctx).getBoolean(KEY_AUTO_FREEZE_ENABLED, false)

    fun setAutoFreezeEnabled(ctx: Context, enabled: Boolean) {
        plainPrefs(ctx).edit().putBoolean(KEY_AUTO_FREEZE_ENABLED, enabled).apply()
    }

    fun getAutoFreezeAppLabels(ctx: Context): List<String> {
        val pm = ctx.packageManager
        return getAutoFreezeApps(ctx).map { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }

    fun syncFromPrefs(ctx: Context, publicPrefs: SharedPreferences) {
        val ak = publicPrefs.getString(KEY_API_KEY, null)
        if (ak != null) setApiKey(ctx, ak)
    }
}
