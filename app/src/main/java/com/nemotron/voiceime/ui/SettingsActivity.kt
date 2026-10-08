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
            preferenceScreen.findPreference<androidx.preference.EditTextPreference>("firebase_api_key")
                ?.text = SecureStore.getFirebaseApiKey(ctx)
            preferenceScreen.findPreference<androidx.preference.Preference>("health_setup")
                ?.setOnPreferenceClickListener {
                    if (SecureStore.getFirebaseApiKey(ctx).isBlank()) {
                        android.widget.Toast.makeText(
                            ctx, "Configura Firebase API key y Database URL primero.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@setOnPreferenceClickListener true
                    }
                    startActivity(android.content.Intent(ctx, com.nemotron.voiceime.health.HealthSetupActivity::class.java))
                    true
                }
            preferenceScreen.findPreference<androidx.preference.Preference>("health_send_now")
                ?.setOnPreferenceClickListener {
                    if (SecureStore.getFirebaseApiKey(ctx).isBlank()) {
                        android.widget.Toast.makeText(
                            ctx, "Configura Firebase API key y Database URL primero.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@setOnPreferenceClickListener true
                    }
                    com.nemotron.voiceime.health.HealthTransferService.start(ctx)
                    android.widget.Toast.makeText(ctx, "Uploading Samsung Health data to Firebase...", android.widget.Toast.LENGTH_LONG).show()
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
                                "Guard active (no battery drain)"
                            } else {
                                "Grant access: Settings → Accessibility → Nemotron Guard"
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
                        "Screen will lock when DND is enabled"
                    } else {
                        "DND will no longer lock the screen"
                    },
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                "firebase_api_key" -> {
                    SecureStore.setFirebaseApiKey(ctx, prefs.getString(key, "").orEmpty())
                }
            }
        }

        private fun updateStatusSummary() {
            val ctx = context ?: return
            val status = preferenceScreen.findPreference<androidx.preference.Preference>("status")
            status?.summary = "Direct Google transcription (no AI). Ready to dictate."
        }
    }
}
