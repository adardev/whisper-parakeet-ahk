package com.nemotron.voiceime.dhizuku

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/** Shared, read-only check for the paired Galaxy Fit3 GATT link. */
object Fit3ConnectionState {
    private const val TAG = "Fit3Connection"
    private const val PREFS = "fit3_tile_preferences"
    private const val ADDRESS_KEY = "paired_fit3_address"
    private const val DEFAULT_ADDRESS = "74:19:0A:49:64:DD"

    fun isConnected(context: Context): Boolean = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) return false
        val address = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ADDRESS_KEY, DEFAULT_ADDRESS) ?: DEFAULT_ADDRESS
        val connected = context.getSystemService(BluetoothManager::class.java)
            ?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
        connected.any { device ->
            device.address.equals(address, ignoreCase = true) ||
                device.name?.contains("Galaxy Fit3", ignoreCase = true) == true
        }
    }.onFailure { Log.w(TAG, "No se pudo consultar la conexión GATT de Fit3", it) }
        .getOrDefault(false)
}
