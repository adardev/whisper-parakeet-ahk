package com.nemotron.voiceime.health

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    // Keep this SDK client alive until the foreground service
                    // finishes reading and uploading the snapshot.
                    val uploaded = withContext(Dispatchers.IO) {
                        HealthTransferService.startAndWait(this@HealthSetupActivity)
                    }
                    Log.d(TAG, "Samsung Health Firebase upload finished=$uploaded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Samsung Health no disponible", e)
            } finally { finish() }
        }
    }
}
