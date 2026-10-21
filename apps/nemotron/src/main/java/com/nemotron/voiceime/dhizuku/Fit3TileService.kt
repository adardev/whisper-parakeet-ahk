package com.nemotron.voiceime.dhizuku

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.util.Log
import androidx.core.content.ContextCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.guard.DndKeepAliveService
import java.util.concurrent.atomic.AtomicBoolean

/** Sync Health before switching off, then disconnect Fit3 and freeze Health. */
class Fit3TileService : AppFreezeTileService() {
    override val targetPackage: String = SAMSUNG_HEALTH_PACKAGE
    override val tileLabel: String = "Fit3"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile

    private val mainHandler = Handler(Looper.getMainLooper())

    // A temporarily disconnected watch must not turn an enabled Health tile
    // into another "connect" action when the user is trying to switch it off.
    override fun isTileFrozen(): Boolean = ShizukuManager.isAppHidden(targetPackage)

    override fun onStartListening() {
        if (operationInProgress.get()) setTileState(Tile.STATE_UNAVAILABLE)
        else super.onStartListening()
    }

    override fun onClick() {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku no está autorizado; no se cambia la conexión Fit3")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Falta BLUETOOTH_CONNECT; no se cambia la conexión Fit3")
            return
        }
        if (!operationInProgress.compareAndSet(false, true)) return
        setTileState(Tile.STATE_UNAVAILABLE)

        Thread({
            try {
                val device = findFit3Device()
                if (device == null) {
                    Log.e(TAG, "No se encontró Galaxy Fit3 emparejada; no se modifica Bluetooth")
                    return@Thread
                }

                val disabled = ShizukuManager.getDisabledPackages()
                    ?: throw IllegalStateException("No se pudo consultar el estado de Samsung Health")
                if (targetPackage !in disabled) {
                    // Preserve exercise data before asking Samsung's own plugin
                    // to disconnect the watch. Never kill the plugin process.
                    Log.i(TAG, "Fit3 conectada; subiendo Samsung Health antes de desconectar")
                    if (!com.nemotron.voiceime.health.HealthTransferService.startAndWait(applicationContext)) {
                        Log.e(TAG, "Falló la sincronización; se conserva la conexión Fit3")
                        return@Thread
                    }

                    val wasConnected = isFit3Connected(device.address)
                    val onlyFit3Connected = wasConnected && isFit3TheOnlyConnectedDevice(device.address)
                    val sent = !wasConnected || sendWatchRequest(DISCONNECT_ACTION, device.address)
                    if (!sent) {
                        Log.e(TAG, "Samsung Wearable no aceptó la solicitud de desconexión")
                        return@Thread
                    }
                    val disconnected = waitForConnection(device.address, connected = false, timeoutMs = 8_000L)
                    if (!disconnected) {
                        if (!onlyFit3Connected) {
                            Log.e(TAG, "Fit3 sigue conectada y hay otros dispositivos; Bluetooth se deja encendido")
                            return@Thread
                        }
                        Log.w(TAG, "Samsung no soltó la Fit3; al ser el único dispositivo, se apagará Bluetooth")
                    } else {
                        Log.i(TAG, "Fit3 desconectada por Samsung sin deshabilitar paquetes")
                    }

                    if (onlyFit3Connected && isBluetoothEnabled()) {
                        val result = ShizukuManager.execShellFresh(
                            arrayOf("cmd", "bluetooth_manager", "disable"), timeoutMs = 8_000L
                        )
                        val bluetoothOff = waitForBluetooth(enabled = false, timeoutMs = 8_000L)
                        val linkDown = waitForConnection(device.address, connected = false, timeoutMs = 8_000L)
                        Log.i(TAG, "Fit3 única: Bluetooth apagado=$bluetoothOff, enlace desconectado=$linkDown: $result")
                        if (!bluetoothOff || !linkDown) {
                            Log.e(TAG, "No se confirmó el apagado y la desconexión de la Fit3")
                            return@Thread
                        }
                    } else {
                        Log.i(TAG, "Había otro dispositivo Bluetooth conectado; Bluetooth permanece encendido")
                    }
                    if (!ShizukuManager.hideApp(targetPackage)) {
                        Log.e(TAG, "No se confirmó que Samsung Health quedara deshabilitado")
                        return@Thread
                    }
                    ShizukuManager.stopApp(targetPackage)
                    Log.i(TAG, "Apagado completado: sincronización terminada, Fit3 desconectada, Samsung Health deshabilitado")
                } else {
                    if (!ShizukuManager.unhideApp(targetPackage) ||
                        !ShizukuManager.unstopApp(targetPackage)) {
                        Log.e(TAG, "No se pudo habilitar Samsung Health para conectar Fit3")
                        return@Thread
                    }
                    if (!isBluetoothEnabled()) {
                        val enabled = ShizukuManager.execShellFresh(
                            arrayOf("cmd", "bluetooth_manager", "enable"), timeoutMs = 8_000L
                        )
                        Log.i(TAG, "Bluetooth encendido para reconectar Fit3: $enabled")
                        if (!waitForBluetooth(enabled = true, timeoutMs = 8_000L)) {
                            Log.e(TAG, "Bluetooth no quedó encendido; no se solicita conexión")
                            return@Thread
                        }
                    }
                    val sent = sendWatchRequest(CONNECT_ACTION, device.address)
                    if (!sent) {
                        Log.e(TAG, "Samsung Wearable no aceptó la solicitud de conexión")
                        return@Thread
                    }
                    val connected = waitForConnection(device.address, connected = true, timeoutMs = 15_000L)
                    Log.i(TAG, "Resultado de reconexión de Fit3: conectado=$connected")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falló el control de conexión Bluetooth de Fit3", t)
            } finally {
                operationInProgress.set(false)
                DndKeepAliveService.update(applicationContext)
                refreshState()
            }
        }, "Nemotron-Fit3-Bluetooth").start()
    }

    private fun findFit3Device() = runCatching {
        val adapter = bluetoothManager()?.adapter
            ?: throw IllegalStateException("BluetoothAdapter no disponible")
        val preferences = getSharedPreferences(FIT3_PREFERENCES, MODE_PRIVATE)
        val bondedFit3 = adapter.bondedDevices
            ?.firstOrNull { it.name?.contains("Galaxy Fit3", ignoreCase = true) == true }
        val address = bondedFit3?.address
            ?: preferences.getString(FIT3_ADDRESS_KEY, null)
            ?: KNOWN_FIT3_ADDRESS

        if (bondedFit3 != null) {
            preferences.edit().putString(FIT3_ADDRESS_KEY, address).apply()
        } else {
            Log.i(TAG, "Bluetooth apagado o Fit3 fuera de la lista emparejada; usando la dirección guardada")
        }
        adapter.getRemoteDevice(address)
    }.onFailure { Log.e(TAG, "No se pudo obtener la dirección de Galaxy Fit3", it) }.getOrNull()

    private fun bluetoothManager() = getSystemService(BluetoothManager::class.java)

    private fun isBluetoothEnabled(): Boolean = runCatching {
        bluetoothManager()?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun isFit3Connected(address: String? = null): Boolean = runCatching {
        val connected = bluetoothManager()?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
        connected.any { device ->
            if (address != null) device.address.equals(address, ignoreCase = true)
            else device.name?.contains("Galaxy Fit3", ignoreCase = true) == true ||
                findFit3Device()?.address?.equals(device.address, ignoreCase = true) == true
        }
    }.onFailure { Log.e(TAG, "No se pudo consultar la conexión GATT de Fit3", it) }.getOrDefault(false)

    /** Only switch off the adapter when Fit3 is the sole live Bluetooth device. */
    private fun isFit3TheOnlyConnectedDevice(fit3Address: String): Boolean = runCatching {
        val manager = bluetoothManager() ?: return false
        val adapter = manager.adapter ?: return false
        val gattDevices = manager.getConnectedDevices(BluetoothProfile.GATT)
        if (gattDevices.none { it.address.equals(fit3Address, ignoreCase = true) }) return false
        if (gattDevices.any { !it.address.equals(fit3Address, ignoreCase = true) }) return false

        // Fit3 uses GATT. Any connected classic or audio profile means another
        // device is using Bluetooth, so leave the adapter on.
        val otherProfiles = listOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            4, // HID_HOST
            5, // PAN
            BluetoothProfile.HEARING_AID,
            22 // LE_AUDIO on Android versions that expose it.
        )
        otherProfiles.all { profile ->
            runCatching { adapter.getProfileConnectionState(profile) }
                .getOrDefault(BluetoothProfile.STATE_DISCONNECTED) == BluetoothProfile.STATE_DISCONNECTED
        }
    }.onFailure { Log.e(TAG, "No se pudo verificar si Fit3 era el único dispositivo conectado", it) }
        .getOrDefault(false)

    private fun sendWatchRequest(action: String, address: String): Boolean {
        // Use the Host Manager receiver for both directions. Its CONNECT
        // action calls SAPHolder.connectDevice(address) directly; the
        // dashboard receiver only queues manageConnectionInfo and can return
        // success without actually reconnecting the watch.
        val component = HOST_MANAGER_RECEIVER
        val command = arrayListOf(
            "am", "broadcast", "--user", "0", "-a", action,
            "-n", "$SAMSUNG_WEARABLE_PLUGIN/$component",
            "--es", "device_address", address,
            "--es", "request_app_package_name", packageName
        )
        if (action == DISCONNECT_ACTION) command.addAll(listOf("--ei", "reason", "1"))
        val output = ShizukuManager.execShellFresh(command.toTypedArray(), timeoutMs = 8_000L)
        Log.i(TAG, "Solicitud Samsung Wearable action=$action address=$address: $output")
        return output?.contains("Broadcast completed") == true &&
            !output.contains("Permission Denial", ignoreCase = true)
    }

    private fun waitForConnection(address: String, connected: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isFit3Connected(address) == connected) return true
            Thread.sleep(350L)
        }
        return isFit3Connected(address) == connected
    }

    private fun waitForBluetooth(enabled: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isBluetoothEnabled() == enabled) return true
            Thread.sleep(250L)
        }
        return isBluetoothEnabled() == enabled
    }

    private fun setTileState(state: Int) = mainHandler.post {
        qsTile?.let { tile -> tile.label = tileLabel; tile.state = state; tile.updateTile() }
    }

    private fun refreshState() {
        val disabled = ShizukuManager.getDisabledPackages()
        mainHandler.post {
            qsTile?.let { tile ->
                tile.label = tileLabel
                tile.icon = createTileIcon()
                tile.state = when {
                    disabled == null -> Tile.STATE_UNAVAILABLE
                    targetPackage in disabled -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                tile.updateTile()
            }
        }
    }

    companion object {
        private val operationInProgress = AtomicBoolean(false)
        private const val TAG = "Fit3BluetoothTile"
        private const val FIT3_PREFERENCES = "fit3_tile_preferences"
        private const val FIT3_ADDRESS_KEY = "paired_fit3_address"
        private const val KNOWN_FIT3_ADDRESS = "74:19:0A:49:64:DD"
        private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val SAMSUNG_WEARABLE_PLUGIN = "com.samsung.wearable.fit3plugin"
        private const val DISCONNECT_ACTION = "com.samsung.android.watchmanager.ACTION_HM_REQUEST_DISCONNECT"
        private const val CONNECT_ACTION = "com.samsung.android.watchmanager.ACTION_HM_REQUEST_CONNECT"
        private const val HOST_MANAGER_RECEIVER =
            "com.samsung.wearable.hostmanager.connection.SAPHolder\$ExternalDisconnectListener"
    }
}
