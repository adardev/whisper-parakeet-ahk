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
            setupAddictionGuardPreference()
            updateStatusSummary()
            setupHealthPrefs()
            setupAodTest()
        }

        private fun setupAddictionGuardPreference() {
            val ctx = context ?: return
            val guard = preferenceScreen.findPreference<androidx.preference.SwitchPreferenceCompat>(
                "addiction_guard_enabled"
            ) ?: return
            guard.isChecked = SecureStore.isAddictionGuardEnabled(ctx)
            guard.onPreferenceChangeListener =
                androidx.preference.Preference.OnPreferenceChangeListener { _, value ->
                    val enabled = value as? Boolean ?: return@OnPreferenceChangeListener false
                    SecureStore.setAddictionGuardEnabled(ctx, enabled)
                    com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(ctx)
                    true
                }
        }

        private fun setupAodTest() {
            val ctx = context ?: return
            listOf(
                "night_test_enable" to true,
                "night_test_disable" to false
            ).forEach { (key, enable) ->
                preferenceScreen.findPreference<androidx.preference.Preference>(key)
                    ?.setOnPreferenceClickListener { preference ->
                    if (android.os.Build.VERSION.SDK_INT >= 31 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                            android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 2308)
                        return@setOnPreferenceClickListener true
                    }
                    if (!com.nemotron.voiceime.dhizuku.ShizukuManager.hasPermission()) {
                        com.nemotron.voiceime.dhizuku.ShizukuManager.requestPermission()
                        android.widget.Toast.makeText(
                            ctx,
                            "Concede el permiso de Shizuku y vuelve a pulsar.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@setOnPreferenceClickListener true
                    }

                    val controls = listOf("night_test_enable", "night_test_disable").mapNotNull {
                        preferenceScreen.findPreference<androidx.preference.Preference>(it)
                    }
                    controls.forEach { it.isEnabled = false }
                    Thread {
                        com.nemotron.voiceime.dhizuku.NightRoutineActions.run(ctx.applicationContext, enable)
                        activity?.runOnUiThread {
                            controls.forEach { it.isEnabled = true }
                        }
                    }.start()
                    true
                }
            }
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
