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

class VoiceRecordService : Service() {

    private var sr: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private val sessionText = StringBuilder()
    private var readyVibrated = false

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
        readyVibrated = false
        sessionText.setLength(0)

        stopSR()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("SpeechRecognizer no disponible")
            cleanup()
            return
        }

        sr = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        muteStreams()
        startListening()
    }

    private fun startListening() {
        if (!isRunning || isStopping) return
        val rec = sr ?: return
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
        try {
            rec.startListening(i)
            Log.d(TAG, "startListening ok")
        } catch (t: Throwable) {
            Log.w(TAG, "startListening threw", t)
            main.postDelayed({ if (isRunning && !isStopping) startListening() }, 200)
        }
    }

    private fun stopAndFinalize() {
        Log.d(TAG, "stopAndFinalize")
        isStopping = true
        vibrate()
        try { sr?.stopListening() } catch (_: Throwable) {}
        main.postDelayed({ if (isRunning && !delivered) deliverAccumulated(withToast = false) }, 2500)
    }

    private fun continueListening() {
        if (!isRunning || isStopping) {
            if (isStopping) deliverAccumulated(withToast = false)
            return
        }
        main.postDelayed({ startListening() }, 60)
    }

    private fun deliverAccumulated(withToast: Boolean) {
        val text = sessionText.toString().trim()
        if (text.isBlank()) {
            if (withToast) toast("No te escuche")
            cleanup()
            return
        }
        deliverText(text)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {
                if (!readyVibrated) {
                    readyVibrated = true
                    vibrate()
                }
            }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(errorCode: Int) {
            Log.w(TAG, "SpeechRecognizer error=$errorCode")
            if (isStopping) {
                deliverAccumulated(withToast = false)
                return
            }
            if (!isRunning) return
            when (errorCode) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    continueListening()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    continueListening()
                else -> {
                    stopSR()
                    toast("Error de reconocimiento: $errorCode")
                    cleanup()
                }
            }
        }

        override fun onPartialResults(p: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (raw.isNotBlank()) {
                if (sessionText.isNotEmpty() && sessionText.last() != ' ' &&
                    !sessionText.endsWith("\n") && !raw.startsWith(" ") && !raw.startsWith("\n")
                ) {
                    sessionText.append(' ')
                }
                sessionText.append(raw.trim())
                Log.d(TAG, "onResults acumulado=${sessionText.length}")
            }
            if (isStopping || !isRunning) {
                deliverAccumulated(withToast = sessionText.isEmpty())
            } else {
                continueListening()
            }
        }

        override fun onEvent(p0: Int, p1: Bundle?) {}
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
        restoreStreams()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSR() {
        try { sr?.destroy() } catch (_: Throwable) {}
        sr = null
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

    private var prevMusicVol = -1

    private var mutedByUs = false

    private fun muteStreams() {
        if (mutedByUs) return
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            prevMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            mutedByUs = true
        } catch (_: Throwable) {}
    }

    private fun restoreStreams() {
        if (!mutedByUs) return
        mutedByUs = false
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            if (prevMusicVol >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, prevMusicVol, 0)
            prevMusicVol = -1
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        restoreStreams()
        stopSR()
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
