package com.nemotron.voiceime.dhizuku

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.guard.DndKeepAliveService
import java.util.concurrent.atomic.AtomicBoolean

/** Sync Health, disconnect Fit3, then freeze both Health and its Fit3 plugin. */
class Fit3TileService : AppFreezeTileService() {
    override val targetPackage: String = SAMSUNG_HEALTH_PACKAGE
    override val tileLabel: String = "Fit3"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started only to protect the current explicit tile operation from
        // Samsung's process freezer; never restart an old connection action.
        if (intent == null || !operationInProgress.get()) stopSelf(startId)
        return START_NOT_STICKY
    }

    // A temporarily disconnected watch must not turn an enabled Health tile
    // into another "connect" action when the user is trying to switch it off.
    override fun isTileFrozen(): Boolean =
        ShizukuManager.isAppHidden(targetPackage) &&
            ShizukuManager.isAppHidden(SAMSUNG_WEARABLE_PLUGIN) &&
            !isFit3Connected()

    override fun onStartListening() {
        if (operationInProgress.get()) setTileState(Tile.STATE_UNAVAILABLE)
        else super.onStartListening()
    }

    override fun onClick() {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku no está autorizado; no se cambia la conexión Fit3")
            showMessage("Autoriza Nemotron en Shizuku para controlar Fit3")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Falta BLUETOOTH_CONNECT; no se cambia la conexión Fit3")
            showMessage("Concede a Nemotron el permiso de dispositivos cercanos")
            return
        }
        if (!operationInProgress.compareAndSet(false, true)) return
        try {
            // A bound QS service can still be frozen while its worker waits
            // for Bluetooth. Keep it foreground only for this one action.
            startForegroundService(Intent(this, Fit3TileService::class.java))
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(OPERATION_CHANNEL, "Cambios de conexión Fit3", NotificationManager.IMPORTANCE_LOW)
            )
            startForeground(OPERATION_NOTIFICATION, NotificationCompat.Builder(this, OPERATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_fit3_tile)
                .setContentTitle("Fit3")
                .setContentText("Actualizando conexión de la pulsera…")
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .setOngoing(true).build())
        } catch (t: Throwable) {
            operationInProgress.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.e(TAG, "No se pudo mantener la operación Fit3 activa", t)
            showMessage("No se pudo iniciar el cambio de conexión Fit3")
            return
        }
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
                val wasConnected = isFit3Connected(device.address)
                val onlyFit3Connected = wasConnected && isFit3TheOnlyConnectedDevice(device.address)
                if (targetPackage !in disabled || wasConnected) {
                    // If the watch is still connected but the plugin was
                    // disabled by an earlier/partial toggle, restore it long
                    // enough to deliver Samsung's disconnect request.
                    if (wasConnected && SAMSUNG_WEARABLE_PLUGIN in disabled) {
                        check(ShizukuManager.unhideApp(SAMSUNG_WEARABLE_PLUGIN) &&
                            ShizukuManager.unstopApp(SAMSUNG_WEARABLE_PLUGIN)) {
                            "No se pudo activar Fit3 Plugin para desconectar la pulsera"
                        }
                    }
                    // Preserve exercise data before asking Samsung's own plugin
                    // to disconnect the watch; it is paused only after the link
                    // is down and the off operation has completed.
                    Log.i(TAG, "Fit3 conectada; subiendo Samsung Health antes de desconectar")
                    if (targetPackage in disabled) {
                        check(ShizukuManager.unhideApp(targetPackage)) { "No se pudo habilitar Health para sincronizar" }
                    }
                    ShizukuManager.unstopApp(targetPackage)
                    val uploaded = runCatching {
                        com.nemotron.voiceime.health.HealthTransferService.startAndWait(
                            applicationContext, timeoutSeconds = 60L
                        )
                    }.onFailure { Log.e(TAG, "Falló la sincronización previa al apagado", it) }.getOrDefault(false)
                    if (!uploaded) {
                        // Disabling a package preserves its local database.
                        // Network/SDK failures must not trap the user's watch
                        // in an on state or claim that an upload succeeded.
                        runCatching { com.nemotron.voiceime.health.HealthTransferService.stop(applicationContext) }
                        Log.w(TAG, "Subida pendiente; se conservan los datos locales y se continúa la desconexión")
                        showMessage("No se confirmó la subida a Firebase; los datos siguen en Samsung Health")
                    }

                    val sent = !wasConnected || sendWatchRequest(DISCONNECT_ACTION, device.address)
                    if (!sent && !onlyFit3Connected) {
                        Log.e(TAG, "Samsung Wearable no aceptó la solicitud de desconexión")
                        showMessage("Samsung Wearable rechazó la desconexión de Fit3")
                        return@Thread
                    }
                    if (!sent) Log.w(TAG, "Solicitud Samsung rechazada; Fit3 era el único dispositivo, se usará el apagado Bluetooth")
                    val disconnected = waitForConnection(device.address, connected = false, timeoutMs = 8_000L)
                    if (!disconnected) {
                        if (!onlyFit3Connected) {
                            Log.e(TAG, "Fit3 sigue conectada y hay otros dispositivos; Bluetooth se deja encendido")
                            showMessage("Fit3 no confirmó la desconexión; hay otros dispositivos Bluetooth conectados")
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
                            showMessage("No se pudo confirmar la desconexión de Fit3")
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
                    if (!ShizukuManager.hideApp(SAMSUNG_WEARABLE_PLUGIN)) {
                        // Keep the tile usable if the plugin could not be
                        // frozen: restore Health instead of reporting a false
                        // off state with a half-disabled Fit3 stack.
                        ShizukuManager.unhideApp(targetPackage)
                        ShizukuManager.unstopApp(targetPackage)
                        Log.e(TAG, "No se confirmó que Fit3 Plugin quedara deshabilitado")
                        showMessage("No se pudo pausar Fit3 Plugin; Samsung Health quedó activo")
                        return@Thread
                    }
                    ShizukuManager.stopApp(SAMSUNG_WEARABLE_PLUGIN)
                    // stopService cannot release Samsung's self-bound SAP
                    // bearer. With no connected Bluetooth peripherals, disable
                    // its package for the Fit3-off interval so SAP cannot be
                    // restarted in the background. The on path restores it
                    // before enabling Bluetooth or requesting reconnection.
                    if (!hasConnectedBluetoothPeripherals()) {
                        check(ShizukuManager.hideApp(SAMSUNG_ACCESSORY_PACKAGE)) {
                            "No se pudo desactivar Samsung Accessory después de desconectar Fit3"
                        }
                        val accessoryStopped = ShizukuManager.stopApp(SAMSUNG_ACCESSORY_PACKAGE)
                        Log.i(TAG, "Fit3 apagada y sin periféricos: Samsung Accessory desactivado y force-stopped=$accessoryStopped")
                    } else {
                        val sapStopped = ShizukuManager.stopService(SAMSUNG_ACCESSORY_SERVICE)
                        Log.i(TAG, "Fit3 apagada con otro periférico activo: stop SAP solicitado=$sapStopped; paquete conservado")
                    }
                    val frozen = ShizukuManager.getDisabledPackages().orEmpty()
                    check(targetPackage in frozen && SAMSUNG_WEARABLE_PLUGIN in frozen &&
                        (hasConnectedBluetoothPeripherals() || SAMSUNG_ACCESSORY_PACKAGE in frozen)) {
                        "No se confirmó la pausa de Samsung Health, Fit3 Plugin y Samsung Accessory"
                    }
                    Log.i(TAG, "Apagado completado: subidaConfirmada=$uploaded, Fit3 desconectada; SAP pausado cuando no hay otros periféricos")
                } else {
                    // Restore packages before waking Samsung's background
                    // health receiver, which initializes its own HostManager.
                    for (pkg in listOf(SAMSUNG_ACCESSORY_PACKAGE, SAMSUNG_WEARABLE_PLUGIN, targetPackage)) {
                        if (pkg in disabled && !ShizukuManager.unhideApp(pkg)) {
                            Log.e(TAG, "No se pudo habilitar $pkg para conectar Fit3")
                            return@Thread
                        }
                        if (!ShizukuManager.unstopApp(pkg)) {
                            Log.e(TAG, "No se pudo quitar force-stop de $pkg")
                            return@Thread
                        }
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
                    if (!startFit3Connection()) {
                        showMessage("No se pudo iniciar Fit3 Plugin para conectar la pulsera")
                        return@Thread
                    }
                    val connected = waitForConnection(device.address, connected = true, timeoutMs = 25_000L)
                    Log.i(TAG, "Resultado de reconexión de Fit3: conectado=$connected")
                    if (!connected) showMessage("Fit3 sigue sin conexión. Comprueba que la pulsera esté cerca y encendida")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falló el control de conexión Bluetooth de Fit3", t)
                showMessage("No se pudo cambiar Fit3: ${t.message ?: "consulta los registros"}")
            } finally {
                operationInProgress.set(false)
                try {
                    DndKeepAliveService.update(applicationContext)
                    refreshState()
                } finally {
                    mainHandler.post {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
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

    /** True only when no connected GATT, audio, HID, or PAN peripheral needs SAP. */
    private fun hasConnectedBluetoothPeripherals(): Boolean = runCatching {
        val manager = bluetoothManager() ?: return true
        val adapter = manager.adapter ?: return true
        if (manager.getConnectedDevices(BluetoothProfile.GATT).isNotEmpty()) return true
        listOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            4, // HID_HOST
            5, // PAN
            BluetoothProfile.HEARING_AID,
            22 // LE_AUDIO
        ).any { profile ->
            runCatching { adapter.getProfileConnectionState(profile) }
                .getOrDefault(BluetoothProfile.STATE_DISCONNECTED) != BluetoothProfile.STATE_DISCONNECTED
        }
    }.onFailure { Log.e(TAG, "No se pudo comprobar el uso de periféricos Bluetooth", it) }
        .getOrDefault(true)

    private fun sendWatchRequest(action: String, address: String): Boolean {
        // This receiver is protected by Samsung's signature permission.
        // Broadcast completion alone is not a disconnect confirmation: callers
        // must verify the link and apply the sole-device Bluetooth fallback.
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

    private fun startFit3Connection(): Boolean {
        // This SDK capability request is handled by an exported, permissionless
        // receiver in the plugin's main process. HostManagerApplication.onCreate
        // initializes NSHostManager/HMApplication under Samsung's UID, restoring
        // its saved-device SAP connection. No Activity or foreground switch is
        // needed. Broadcast completion is only startup acknowledgement; the
        // caller still verifies the real watch link before reporting success.
        val output = ShizukuManager.execShellFresh(arrayOf(
            "am", "broadcast", "--user", "0", "--receiver-include-background",
            "-a", HEALTH_CAPABILITY_ACTION,
            "-n", "$SAMSUNG_WEARABLE_PLUGIN/$HEALTH_CONNECTION_RECEIVER"
        ), timeoutMs = 10_000L)
        Log.i(TAG, "Arranque en segundo plano Fit3: $output")
        return output?.contains("Broadcast completed") == true &&
            !output.contains("Error", ignoreCase = true) &&
            !output.contains("Permission Denial", ignoreCase = true) &&
            !output.contains("SecurityException")
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
        qsTile?.let { tile ->
            tile.label = tileLabel
            tile.subtitle = if (state == Tile.STATE_UNAVAILABLE) "Sincronizando y cambiando conexión…" else null
            tile.state = state
            tile.updateTile()
        }
    }

    private fun refreshState() {
        val disabled = ShizukuManager.getDisabledPackages()
        val connected = isFit3Connected()
        mainHandler.post {
            qsTile?.let { tile ->
                tile.label = tileLabel
                tile.subtitle = null
                tile.icon = createTileIcon()
                tile.state = when {
                    disabled == null -> Tile.STATE_UNAVAILABLE
                    targetPackage in disabled && SAMSUNG_WEARABLE_PLUGIN in disabled && !connected -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                tile.updateTile()
            }
        }
    }

    private fun showMessage(message: String) = mainHandler.post {
        android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
    }

    companion object {
        private val operationInProgress = AtomicBoolean(false)
        private const val TAG = "Fit3BluetoothTile"
        private const val OPERATION_CHANNEL = "fit3_connection_action"
        private const val OPERATION_NOTIFICATION = 9025
        private const val FIT3_PREFERENCES = "fit3_tile_preferences"
        private const val FIT3_ADDRESS_KEY = "paired_fit3_address"
        private const val KNOWN_FIT3_ADDRESS = "74:19:0A:49:64:DD"
        private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val SAMSUNG_WEARABLE_PLUGIN = "com.samsung.wearable.fit3plugin"
        private const val SAMSUNG_ACCESSORY_PACKAGE = "com.samsung.accessory"
        private const val SAMSUNG_ACCESSORY_SERVICE = "com.samsung.accessory/.services.SAFrameworkService"
        private const val DISCONNECT_ACTION = "com.samsung.android.watchmanager.ACTION_HM_REQUEST_DISCONNECT"
        private const val HEALTH_CAPABILITY_ACTION =
            "com.samsung.android.sdk.healthconnectivity.REQUEST_MANAGER_CAPABILITY"
        private const val HEALTH_CONNECTION_RECEIVER =
            "com.samsung.wearable.providers.sahealthdataprovider.connection.MobileConnectionReceiver"
        private const val HOST_MANAGER_RECEIVER =
            "com.samsung.wearable.hostmanager.connection.SAPHolder\$ExternalDisconnectListener"
    }
}
