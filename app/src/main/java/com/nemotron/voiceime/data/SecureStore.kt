package com.nemotron.voiceime.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/**
 * Almacenamiento seguro de preferencias.
 * API key (NVIDIA NIM), modelo, locale, y lista de apps congeladas.
 */
object SecureStore {

    private const val FILE_NAME = "nemotron_secure_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_LOCALE = "dict_locale"
    private const val KEY_FROZEN_APPS = "frozen_apps"

    private const val DEFAULT_MODEL = "nvidia/nemotron-3-nano-30b-a3b"
    private const val DEFAULT_LOCALE = "es_ES"
    private val DEFAULT_SYSTEM_PROMPT = buildString {
        append("Eres un transcriptor de voz a texto. ")
        append("El usuario dicta en su idioma. ")
        append("Devuelve SOLO la transcripción limpia, sin comillas, sin markdown, sin explicaciones. ")
        append("Corrige puntuación y mayúsculas automáticamente. ")
        append("No añadas texto extra ni comentaries lo que haces.")
    }

    private fun prefs(ctx: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            FILE_NAME,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        ctx.getSharedPreferences("${FILE_NAME}_plain", Context.MODE_PRIVATE)
    }

    fun getApiKey(ctx: Context): String =
        prefs(ctx).getString(KEY_API_KEY, "").orEmpty()

    fun setApiKey(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    fun getModel(ctx: Context): String =
        prefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun getSystemPrompt(ctx: Context): String =
        prefs(ctx).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
            ?: DEFAULT_SYSTEM_PROMPT

    fun getLocale(ctx: Context): String =
        prefs(ctx).getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE

    // ── Frozen apps (Dhizuku hide/unfreeze) ────────────────────────────

    fun getFrozenApps(ctx: Context): Set<String> {
        val json = prefs(ctx).getString(KEY_FROZEN_APPS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun setFrozenApps(ctx: Context, apps: Set<String>) {
        val json = JSONArray(apps.toList()).toString()
        prefs(ctx).edit().putString(KEY_FROZEN_APPS, json).apply()
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

    /** Devuelve etiquetas legibles de las apps congeladas (para QS tile). */
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

    /** Sincroniza API key desde SharedPreferences públicas del SettingsFragment. */
    fun syncFromPrefs(ctx: Context, publicPrefs: SharedPreferences) {
        val ak = publicPrefs.getString(KEY_API_KEY, null)
        if (ak != null) setApiKey(ctx, ak)
    }
}
