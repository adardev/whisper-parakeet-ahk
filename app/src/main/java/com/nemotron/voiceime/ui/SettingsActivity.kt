package com.nemotron.voiceime.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

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
            updateStatusSummary()
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
            requireContext().let { ctx -> SecureStore.syncFromPrefs(ctx, prefs) }
            if (key == "api_key") updateStatusSummary()
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
