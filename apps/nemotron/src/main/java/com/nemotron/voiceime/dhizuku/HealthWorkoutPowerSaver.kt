package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Context
import android.app.NotificationManager
import android.app.Notification
import android.bluetooth.BluetoothDevice
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nemotron.voiceime.guard.DndKeepAliveService
import java.text.Normalizer

/** Temporarily disables Battery Saver only while Samsung Health reports an active workout. */
object HealthWorkoutPowerSaver {
    private const val TAG = "HealthWorkoutSaver"
    private const val PREFS = "health_workout_power_saver"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTIVE_KEYS = "active_notification_keys"
    private const val KEY_RESTORE_SAVER = "restore_saver_after_workout"
    private const val HEALTH_PACKAGE = "com.sec.android.app.shealth"
    private val ROUTE_WORKOUT_TITLES = setOf(
        "walk", "walking", "outdoor walk", "outdoor walking",
        "run", "running", "outdoor run", "outdoor running", "trail run", "trail running",
        "hike", "hiking",
        "bike", "biking", "cycling", "bicycle", "bicycling", "outdoor cycling",
        "road cycling", "mountain biking",
        "caminar", "caminata", "correr", "carrera", "senderismo", "excursionismo",
        "bicicleta", "ciclismo", "ciclismo de carretera", "bicicleta de montana",
        "ciclismo de montana"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Saves the user's preference; notification access is granted only while Fit3 is connected. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        val previous = isEnabled(context)
        if (!enabled) {
            finishWorkout(context, forceRestore = true)
            if (prefs(context).getBoolean(KEY_RESTORE_SAVER, false)) return false
        }
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        val synced = syncForFit3State(context)
        if (!synced) prefs(context).edit().putBoolean(KEY_ENABLED, previous).apply()
        return synced
    }

    /** Start the listener only for an opted-in user with a live Fit3 connection. */
    @Synchronized
    fun syncForFit3State(context: Context, fit3Connected: Boolean? = null): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        val connected = fit3Connected ?: DndKeepAliveService.isFit3Connected(context)
        val shouldListen = isEnabled(context) && connected
        if (!shouldListen) {
            val p = prefs(context)
            val hasWorkout = p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty().isNotEmpty() ||
                p.getBoolean(KEY_RESTORE_SAVER, false)
            if (hasWorkout) {
                finishWorkout(context, forceRestore = true)
                if (p.getBoolean(KEY_RESTORE_SAVER, false)) return false
            }
        }

        val component = ComponentName(context, HealthWorkoutPowerSaverListener::class.java)
        val flattened = "${component.packageName}/${component.className}"
        val nm = context.getSystemService(NotificationManager::class.java)
        val allowedBefore = if (android.os.Build.VERSION.SDK_INT >= 27) {
            nm.isNotificationListenerAccessGranted(component)
        } else false
        if (allowedBefore == shouldListen) return true

        val command = if (shouldListen) "allow_listener" else "disallow_listener"
        ShizukuManager.execShellFresh(arrayOf("cmd", "notification", command, flattened))
        val allowed = if (android.os.Build.VERSION.SDK_INT >= 27) {
            nm.isNotificationListenerAccessGranted(component)
        } else true
        if (allowed != shouldListen) {
            Log.e(TAG, "Android no confirmó acceso a notificaciones=$shouldListen para $flattened")
            return false
        }
        Log.i(TAG, "Listener de entrenamientos activo=$shouldListen; Fit3 conectada=$connected; preferencia=${isEnabled(context)}")
        return true
    }

    fun isFit3Device(context: Context, device: BluetoothDevice?): Boolean {
        if (device == null || ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) return false
        val savedAddress = context.getSharedPreferences("fit3_tile_preferences", Context.MODE_PRIVATE)
            .getString("paired_fit3_address", "74:19:0A:49:64:DD")
        return device.address.equals(savedAddress, ignoreCase = true) ||
            device.name?.contains("Galaxy Fit3", ignoreCase = true) == true
    }

    fun isWorkoutNotification(
        context: Context,
        packageName: String,
        channelName: CharSequence?,
        workoutTitle: CharSequence?,
        ongoing: Boolean = true
    ): Boolean {
        if (!isEnabled(context) || packageName != HEALTH_PACKAGE || !ongoing) return false
        val name = channelName?.toString()?.trim()?.lowercase().orEmpty()
        val isWorkoutChannel = name == "ongoing workouts" || name == "entrenamientos en curso" ||
            name == "entrenamientos activos" || name == "ejercicios en curso"
        if (!isWorkoutChannel) return false

        val normalizedTitle = Normalizer.normalize(workoutTitle?.toString().orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()
            .lowercase()
        return normalizedTitle in ROUTE_WORKOUT_TITLES && DndKeepAliveService.isFit3Connected(context)
    }

    fun isWorkoutFinished(notification: Notification): Boolean {
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val content = "$title $text".lowercase()
        return listOf(
            "workout finished", "workout complete", "exercise finished", "exercise complete",
            "entrenamiento finalizado", "entrenamiento terminado", "ejercicio finalizado",
            "ejercicio terminado"
        ).any(content::contains)
    }

    @Synchronized
    fun notificationPosted(context: Context, key: String) {
        if (!isEnabled(context) || key.isBlank()) return
        val p = prefs(context)
        val keys = p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty().toMutableSet()
        if (!keys.add(key)) return

        val hadSession = keys.size > 1 || p.getBoolean(KEY_RESTORE_SAVER, false) ||
            p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty().isNotEmpty()
        if (!hadSession) {
            val saverWasOn = readSaverEnabled(context) ?: run {
                Log.e(TAG, "No pude leer el estado de Ahorro de energía; no cambiaré nada")
                return
            }
            if (saverWasOn && !setSaverEnabled(context, false)) {
                Log.e(TAG, "No pude apagar Ahorro de energía para el entrenamiento")
                return
            }
            p.edit().putBoolean(KEY_RESTORE_SAVER, saverWasOn).apply()
            Log.i(TAG, "Entrenamiento iniciado; ahorro estaba encendido=$saverWasOn")
        }
        p.edit().putStringSet(KEY_ACTIVE_KEYS, keys).apply()
    }

    @Synchronized
    fun notificationRemoved(context: Context, key: String) {
        if (key.isBlank()) return
        val p = prefs(context)
        val keys = p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty().toMutableSet()
        if (!keys.remove(key)) return
        p.edit().putStringSet(KEY_ACTIVE_KEYS, keys).apply()
        if (keys.isEmpty()) finishWorkout(context)
    }

    fun isTrackedNotification(context: Context, key: String): Boolean =
        key.isNotBlank() && key in prefs(context).getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty()

    /** Reconciles state after listener reconnects, including missed removal callbacks. */
    @Synchronized
    fun reconcile(context: Context, liveKeys: Set<String>) {
        if (!isEnabled(context)) return
        val p = prefs(context)
        val oldKeys = p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty()
        if (liveKeys.isEmpty()) {
            if (oldKeys.isNotEmpty() || p.getBoolean(KEY_RESTORE_SAVER, false)) {
                p.edit().putStringSet(KEY_ACTIVE_KEYS, emptySet()).apply()
                finishWorkout(context)
            }
        } else {
            if (oldKeys.isEmpty() && !p.getBoolean(KEY_RESTORE_SAVER, false)) {
                notificationPosted(context, liveKeys.first())
            } else {
                val merged = oldKeys.toMutableSet().apply { addAll(liveKeys) }
                p.edit().putStringSet(KEY_ACTIVE_KEYS, merged).apply()
            }
        }
    }

    @Synchronized
    private fun finishWorkout(context: Context, forceRestore: Boolean = false) {
        val p = prefs(context)
        if (!forceRestore && p.getStringSet(KEY_ACTIVE_KEYS, emptySet()).orEmpty().isNotEmpty()) return
        val restore = p.getBoolean(KEY_RESTORE_SAVER, false)
        if (restore) {
            val current = readSaverEnabled(context)
            if (current == false && !setSaverEnabled(context, true)) {
                Log.e(TAG, "Entrenamiento terminó, pero Shizuku no pudo restaurar Ahorro")
                return
            }
            Log.i(TAG, "Entrenamiento terminado; Ahorro de energía restaurado")
        } else {
            Log.i(TAG, "Entrenamiento terminado; Ahorro de energía ya estaba apagado")
        }
        p.edit().remove(KEY_RESTORE_SAVER).putStringSet(KEY_ACTIVE_KEYS, emptySet()).apply()
    }

    private fun readSaverEnabled(context: Context): Boolean? {
        if (!ShizukuManager.hasPermission()) return null
        return when (ShizukuManager.execShellFresh(arrayOf("settings", "get", "global", "low_power"))?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun setSaverEnabled(context: Context, enabled: Boolean): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        val mode = if (enabled) "1" else "0"
        ShizukuManager.execShellFresh(arrayOf("settings", "put", "global", "low_power", mode))
        return readSaverEnabled(context) == enabled
    }
}
