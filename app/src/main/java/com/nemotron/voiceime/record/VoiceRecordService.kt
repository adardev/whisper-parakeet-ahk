package com.nemotron.voiceime.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.ShizukuManager
import com.nemotron.voiceime.net.NemotronStreamClient

class VoiceRecordService : Service() {

    private var sr: SpeechRecognizer? = null
    private var client: NemotronStreamClient? = null
    private var accumulated = StringBuilder()
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopAndFinalize()
        }
        return START_STICKY
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording")
        try {
            startForeground(NOTIF_ID, buildNotification("Grabando… toca para parar"))
        } catch (_: SecurityException) {
            startForeground(NOTIF_ID, buildNotification("Grabando… toca para parar"), 0)
        }
        if (!hasNetwork()) {
            toast("Sin conexión a internet")
            cleanup()
            return
        }
        isRunning = true
        delivered = false
        isProcessing = false
        isStopping = false
        accumulated = StringBuilder()

        stopSR()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("SpeechRecognizer no disponible")
            cleanup()
            return
        }
        val k = SecureStore.getApiKey(this)
        if (k.isBlank()) {
            toast("Configura tu API key en la app")
            cleanup()
            return
        }

        sr = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val loc = SecureStore.getLocale(this@VoiceRecordService)
            try {
                val parts = loc.split("_")
                val l = java.util.Locale(parts.getOrNull(0) ?: "es", parts.getOrNull(1) ?: "")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, l.toLanguageTag())
            } catch (_: Throwable) {}
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        muteStreams()
        vibrate()
        sr?.startListening(i)
    }

    private fun stopAndFinalize() {
        Log.d(TAG, "stopAndFinalize")
        isStopping = true
        vibrate()
        sr?.stopListening()
        main.postDelayed({ if (isRunning) cleanup() }, 2000)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(errorCode: Int) {
            Log.w(TAG, "SpeechRecognizer error=$errorCode")
            if (isStopping) return
            if (errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                if (!isRunning) return
                restartListening()
                return
            }
            stopSR()
            toast("Error de reconocimiento: $errorCode")
            cleanup()
        }

        override fun onPartialResults(p: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            stopSR()
            if (raw.isBlank()) {
                toast("No te escuche")
                cleanup()
                return
            }
            sendToNemotron(raw)
        }

        override fun onEvent(p0: Int, p1: Bundle?) {}
    }

    private fun sendToNemotron(text: String) {
        if (!hasNetwork()) {
            toast("Sin conexión a internet")
            cleanup()
            return
        }
        isProcessing = true
        isRunning = false
        updateNotif("Procesando…")

        val k = SecureStore.getApiKey(this)
        val c = client ?: NemotronStreamClient(k).also { client = it }
        accumulated = StringBuilder()

        c.stream(
            userText = text,
            model = SecureStore.getModel(this),
            system = SecureStore.getSystemPrompt(this),
            onToken = { tok -> accumulated.append(tok) },
            onComplete = { final ->
                main.post { deliverText(final.ifBlank { accumulated.toString() }) }
            },
            onError = { t ->
                main.post {
                    toast("Error: ${t.message}")
                    cleanup()
                }
            }
        )
    }

    private var delivered = false

    private fun deliverText(text: String) {
        if (delivered) return
        delivered = true
        val finalText = text.trim()
        if (finalText.isBlank()) {
            cleanup()
            return
        }
        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("nemotron", finalText)
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        cb.setPrimaryClip(clip)
        main.postDelayed({
            ShizukuManager.pasteText(this, finalText)
            cleanup()
        }, 100)
    }

    private fun cleanup() {
        isRunning = false
        isProcessing = false
        stopSR()
        client?.cancel()
        restoreStreams()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSR() {
        try { sr?.destroy() } catch (_: Throwable) {}
        sr = null
    }

    private fun restartListening() {
        try { sr?.destroy() } catch (_: Throwable) {}
        sr = null
        if (!isRunning) return
        sr = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val loc = SecureStore.getLocale(this@VoiceRecordService)
            try {
                val parts = loc.split("_")
                val l = java.util.Locale(parts.getOrNull(0) ?: "es", parts.getOrNull(1) ?: "")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, l.toLanguageTag())
            } catch (_: Throwable) {}
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr?.startListening(i)
        Log.d(TAG, "restarted listening")
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, "Nemotron", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setContentTitle("Nemotron")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun toast(s: String) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(40, 80))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(40, 80))
            }
        } catch (_: Throwable) {}
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private var prevNotifVol = -1
    private var prevSystemVol = -1
    private var prevMusicVol = -1
    private var prevAlarmVol = -1
    private var prevDtmfVol = -1
    private var prevAccessVol = -1

    private fun muteStreams() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            prevNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            prevSystemVol = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            prevMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            prevAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            prevDtmfVol = am.getStreamVolume(AudioManager.STREAM_DTMF)
            prevAccessVol = am.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_DTMF, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, 0, 0)
        } catch (_: Throwable) {}
    }

    private fun restoreStreams() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            if (prevNotifVol >= 0) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, prevNotifVol, 0)
            if (prevSystemVol >= 0) am.setStreamVolume(AudioManager.STREAM_SYSTEM, prevSystemVol, 0)
            if (prevMusicVol >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, prevMusicVol, 0)
            if (prevAlarmVol >= 0) am.setStreamVolume(AudioManager.STREAM_ALARM, prevAlarmVol, 0)
            if (prevDtmfVol >= 0) am.setStreamVolume(AudioManager.STREAM_DTMF, prevDtmfVol, 0)
            if (prevAccessVol >= 0) am.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, prevAccessVol, 0)
            prevNotifVol = -1; prevSystemVol = -1; prevMusicVol = -1; prevAlarmVol = -1; prevDtmfVol = -1; prevAccessVol = -1
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        restoreStreams()
        stopSR()
        client?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoiceRecordService"
        private const val CH = "nemotron_record"
        private const val NOTIF_ID = 9011
        const val ACTION_START = "com.nemotron.voiceime.START"
        const val ACTION_STOP = "com.nemotron.voiceime.STOP"

        @Volatile var isRunning: Boolean = false
        @Volatile var isProcessing: Boolean = false
        @Volatile var isStopping: Boolean = false
    }
}
