package com.nemotron.voiceime.health

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient

/**
 * HealthSetupActivity: pantalla transparente que solicita los permisos de
 * Health Connect (usando el contrato oficial) y arranca el servicio de transferencia.
 */
class HealthSetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HealthSetupActivity"
    }

    private val healthClient by lazy { HealthConnectClient.getOrCreate(this, HealthConnectManager.PROVIDER_PACKAGE) }

    private val permissionLauncher =
        registerForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(HealthConnectManager.PROVIDER_PACKAGE)
        ) { granted: Set<String> ->
            Log.d(TAG, "Permisos concedidos: $granted")
            if (granted.isNotEmpty()) {
                HealthTransferService.start(this)
                Toast.makeText(this, "Health Connect activado. Datos se envian al NAS.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Sin permisos de Health Connect.", Toast.LENGTH_LONG).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = runCatching {
            kotlinx.coroutines.runBlocking { healthClient.permissionController.getGrantedPermissions() }
        }.getOrDefault(setOf())
        val missing = HealthConnectManager.READ_PERMISSIONS - granted
        if (missing.isEmpty()) {
            Log.d(TAG, "Todos los permisos ya concedidos, iniciando servicio")
            HealthTransferService.start(this)
            finish()
            return
        }
        permissionLauncher.launch(missing)
    }
}