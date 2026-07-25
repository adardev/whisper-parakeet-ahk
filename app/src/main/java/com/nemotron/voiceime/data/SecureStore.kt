package com.nemotron.voiceime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Acceso a SharedPreferences encriptadas (AES256) para almacenar la API key
 * de NVIDIA NIM de forma segura. Reemplaza a EncryptedSharedPreferences.
 */
object SecureStore {

    private const val FILE_NAME = "nemotron_secure_prefs"
    private const val APPLE_KEY = "api_key"          // ciphertext
    private const val KEY_MODEL = "model"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_LOCALE = "dict_locale"

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
        // Fallback: prefs sin encriptar (previene crasheo en dispositivos raros)
        ctx.getSharedPreferences("${FILE_NAME}_plain", Context.MODE_PRIVATE)
    }

    fun getApiKey(ctx: Context): String =
        prefs(ctx).getString(APPLE_KEY, "").orEmpty()

    fun setApiKey(ctx: Context, value: String) {
        prefs(ctx).edit().putString(APPLE_KEY, value.trim()).apply()
    }

    fun getModel(ctx: Context): String =
        prefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun getSystemPrompt(ctx: Context): String =
        prefs(ctx).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
            ?: DEFAULT_SYSTEM_PROMPT

    fun getLocale(ctx: Context): String =
        prefs(ctx).getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE

    /** Las PreferencesFragment tambien看不见escriben aqui, sincroniza con API key visible prefs. */
    fun syncFromPrefs(ctx: Context, publicPrefs: SharedPreferences) {
        val ak = publicPrefs.getString(APPLE_KEY, null)
        if (ak != null) setApiKey(ctx, ak)
    }
}
