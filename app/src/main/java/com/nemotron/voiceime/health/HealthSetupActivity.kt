package com.nemotron.voiceime.health

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Pantalla transparente que solicita los permisos del Samsung Health Data SDK.
 */
class HealthSetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HealthSetupActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                val manager = SamsungHealthManager(this@HealthSetupActivity)
                val granted = manager.grantedPermissions()
                val requested = if (granted.isEmpty()) {
                    // requestPermissions devuelve los permisos aceptados por el usuario.
                    val store = com.samsung.android.sdk.health.data.HealthDataService
                        .getStore(applicationContext)
                    store.requestPermissions(
                        manager.permissionSetForSetup(), this@HealthSetupActivity
                    )
                } else granted
                Log.d(TAG, "Samsung Health permissions: $requested")
                if (requested.isNotEmpty()) {
                    HealthTransferService.start(this@HealthSetupActivity)
                    Toast.makeText(this@HealthSetupActivity, "Samsung Health activado. Datos se suben a Firebase.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Samsung Health no disponible", e)
                Toast.makeText(this@HealthSetupActivity, "Activa Samsung Health y su modo desarrollador.", Toast.LENGTH_LONG).show()
            } finally { finish() }
        }
    }
}
