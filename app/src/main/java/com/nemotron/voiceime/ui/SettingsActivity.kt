package com.nemotron.voiceime.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.guard.AddictionGuard

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            syncGuardSwitch()
            updateStatusSummary()
            setupHealthPrefs()
        }

        private fun setupHealthPrefs() {
            val ctx = context ?: return
            preferenceScreen.findPreference<androidx.preference.Preference>("health_setup")
                ?.setOnPreferenceClickListener {
                    startActivity(android.content.Intent(ctx, com.nemotron.voiceime.health.HealthSetupActivity::class.java))
                    true
                }
            preferenceScreen.findPreference<androidx.preference.Preference>("health_send_now")
                ?.setOnPreferenceClickListener {
                    val url = preferenceScreen.sharedPreferences
                        ?.getString("health_webhook_url", "http://192.168.0.2:9090/webhook")
                        ?: "http://192.168.0.2:9090/webhook"
                    com.nemotron.voiceime.health.HealthTransferService.setWebhookUrl(url)
                    com.nemotron.voiceime.health.HealthTransferService.start(ctx)
                    android.widget.Toast.makeText(ctx, "Enviando datos de salud al NAS...", android.widget.Toast.LENGTH_LONG).show()
                    true
                }
        }

        private fun syncGuardSwitch() {
            val ctx = context ?: return
            val enabled = SecureStore.isAddictionGuardEnabled(ctx)
            preferenceScreen.sharedPreferences
                ?.edit()?.putBoolean("addiction_guard_enabled", enabled)?.apply()
            preferenceScreen.findPreference<androidx.preference.SwitchPreferenceCompat>(
                "addiction_guard_enabled"
            )?.isChecked = enabled
            val dndLock = SecureStore.isDndLockEnabled(ctx)
            preferenceScreen.sharedPreferences
                ?.edit()?.putBoolean("dnd_lock_enabled", dndLock)?.apply()
            preferenceScreen.findPreference<androidx.preference.SwitchPreferenceCompat>(
                "dnd_lock_enabled"
            )?.isChecked = dndLock
        }

        override fun onResume() {
            super.onResume()
            updateStatusSummary()
            preferenceScreen.sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onPause() {
            preferenceScreen.sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(listener)
            super.onPause()
        }

        private val listener = { prefs: android.content.SharedPreferences, key: String? ->
            val ctx = requireContext()
            SecureStore.syncFromPrefs(ctx, prefs)
            when (key) {
                "api_key" -> updateStatusSummary()
                "addiction_guard_enabled" -> {
                    val enabled = prefs.getBoolean("addiction_guard_enabled", false)
                    SecureStore.setAddictionGuardEnabled(ctx, enabled)
                    AddictionGuard.applyEnabled(ctx)
                    com.nemotron.voiceime.guard.DndKeepAliveService.update(ctx)
                    if (enabled) {
                        android.widget.Toast.makeText(
                            ctx,
                            if (AddictionGuard.isA11yActive(ctx)) {
                                "Guard activo (sin gasto de batería)"
                            } else {
                                "Concede acceso: Ajustes → Accesibilidad → Nemotron Guard"
                            },
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                "dnd_lock_enabled" -> {
                    val enabled = prefs.getBoolean("dnd_lock_enabled", false)
                    SecureStore.setDndLockEnabled(ctx, enabled)
                    com.nemotron.voiceime.guard.DndKeepAliveService.update(ctx)
                    android.widget.Toast.makeText(
                        ctx,
                        if (enabled) {
                            "Se bloqueará la pantalla al activar No Molestar"
                        } else {
                            "No Molestar ya no bloqueará la pantalla"
                        },
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                "health_webhook_url" -> {
                    val url = prefs.getString("health_webhook_url", "http://192.168.0.2:9090/webhook")
                    com.nemotron.voiceime.health.HealthTransferService.setWebhookUrl(url ?: "http://192.168.0.2:9090/webhook")
                }
            }
        }

        private fun updateStatusSummary() {
            val ctx = context ?: return
            val status = preferenceScreen.findPreference<androidx.preference.Preference>("status")
            status?.summary = if (SecureStore.getApiKey(ctx).isNotBlank()) {
                "✓ API key configurada (${SecureStore.getModel(ctx).substringAfterLast('/')})"
            } else {
                "✗ API key no configurada — consíguela en build.nvidia.com"
            }
        }
    }
}
