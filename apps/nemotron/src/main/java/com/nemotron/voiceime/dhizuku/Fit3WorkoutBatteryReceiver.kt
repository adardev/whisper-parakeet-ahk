package com.nemotron.voiceime.dhizuku

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Keeps the Health notification listener unbound while the user's Fit3 is disconnected. */
class Fit3WorkoutBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val connectedOverride = when (action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> false
                    BluetoothAdapter.STATE_ON -> null
                    else -> return
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (!HealthWorkoutPowerSaver.isFit3Device(appContext, device)) return
                action == BluetoothDevice.ACTION_ACL_CONNECTED
            }
            else -> return
        }

        val pendingResult = goAsync()
        Thread({
            try {
                val synced = HealthWorkoutPowerSaver.syncForFit3State(appContext, connectedOverride)
                Log.d(TAG, "Estado de listener sincronizado con Fit3: ok=$synced conectado=$connectedOverride")
            } catch (t: Throwable) {
                Log.e(TAG, "No se pudo sincronizar el listener con el estado Fit3", t)
            } finally {
                pendingResult.finish()
            }
        }, "Nemotron-Fit3-Workout-Listener").start()
    }

    companion object {
        private const val TAG = "Fit3WorkoutBattery"
    }
}
