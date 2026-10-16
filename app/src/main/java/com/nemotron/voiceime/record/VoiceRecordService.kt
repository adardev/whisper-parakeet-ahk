package com.nemotron.voiceime.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
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
        requestedRecordingState = true
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
        if (keyboardInputConnection != null) KeyboardDictationFeedback.setRecording(true)
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
            val loc = if (keyboardInputConnection != null) "es_MX" else SecureStore.getLocale(this@VoiceRecordService)
            try {
                val parts = loc.split("_")
                val l = java.util.Locale(parts.getOrNull(0) ?: "es", parts.getOrNull(1) ?: "")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, l.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, l.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false)
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
        requestedRecordingState = false
        isStopping = true
        KeyboardDictationFeedback.setRecording(false)
        if (keyboardInputConnection == null) vibrate()
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
                    if (keyboardInputConnection == null) vibrate()
                }
            }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) { KeyboardDictationFeedback.setLevel(v) }
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
        val insertedIntoKeyboard = try {
            keyboardInputConnection?.commitText("$finalText ", 1) == true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not insert through HoneyBoard input connection", error)
            false
        }
        if (insertedIntoKeyboard) {
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
        KeyboardDictationFeedback.setRecording(false)
        requestedRecordingState = false
        isRunning = false
        isProcessing = false
        stopSR()
        restoreStreams()
        keyboardInputConnection = null
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
        if (keyboardInputConnection != null) {
            Log.w(TAG, s)
            return
        }
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
        KeyboardDictationFeedback.setRecording(false)
        keyboardInputConnection = null
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

        @Volatile private var keyboardInputConnection: InputConnection? = null
        @Volatile private var honeyBoardKeyboard = java.lang.ref.WeakReference<View>(null)
        @Volatile private var honeyBoardAppContext: Context? = null
        private const val DOUBLE_SPACE_WINDOW_MS = 750L
        @Volatile private var lastSpaceCommitUptime = 0L
        @Volatile private var requestedRecordingState: Boolean? = null

        @JvmStatic
        fun bindHoneyBoardKeyboard(keyboard: View) {
            honeyBoardKeyboard = java.lang.ref.WeakReference(keyboard)
        }

        @JvmStatic
        fun bindHoneyBoardService(context: Context) {
            honeyBoardAppContext = context.applicationContext
            (context as? InputMethodService)?.window?.window?.decorView?.let(::bindHoneyBoardKeyboard)
        }

        /** Observe HoneyBoard's semantic text callback; software keys don't emit hardware KeyEvents. */
        @JvmStatic
        @Synchronized
        fun onHoneyBoardSpaceCommit(inputConnection: InputConnection, text: CharSequence): Boolean {
            // HoneyBoard sometimes batches two rapid space-key commits into one CharSequence.
            // Only a commit made entirely of literal spaces can participate in the gesture.
            if (text.isEmpty() || text.any { it != ' ' }) {
                lastSpaceCommitUptime = 0L
                return false
            }

            val now = SystemClock.uptimeMillis()
            val previousSpace = lastSpaceCommitUptime
            val paired = previousSpace != 0L && now - previousSpace <= DOUBLE_SPACE_WINDOW_MS
            if (text.length == 1 && !paired) {
                lastSpaceCommitUptime = now
                Log.d(TAG, "Space key commit 1/2")
                return false
            }

            val keyboard = honeyBoardKeyboard.get()
            val context = keyboard?.context ?: honeyBoardAppContext ?: run {
                lastSpaceCommitUptime = 0L
                Log.w(TAG, "Ignoring double-space: HoneyBoard service context is not bound")
                return false
            }

            lastSpaceCommitUptime = 0L
            // The pending first space is already in the editor. Remove it when the
            // second callback arrives; batched spaces have not been committed yet.
            if (paired) {
                try {
                    inputConnection.deleteSurroundingText(1, 0)
                } catch (error: Throwable) {
                    Log.w(TAG, "Could not remove first space from editor", error)
                }
            }
            Log.i(TAG, "Double-space detected (chars=${text.length}, paired=$paired)")
            toggleFromIme(context, inputConnection, keyboard)
            return true
        }

        @JvmStatic
        fun toggleFromIme(context: Context, inputConnection: InputConnection?, keyboard: View?) {
            keyboard?.let { KeyboardDictationFeedback.bind(it, it.width / 2f) }
            keyboardInputConnection = inputConnection
            lastSpaceCommitUptime = 0L
            val start = !(requestedRecordingState ?: isRunning)
            requestedRecordingState = start
            val action = if (start) ACTION_START else ACTION_STOP
            Log.i(TAG, "Dictation toggle requested: ${if (start) "start" else "stop"}; running=$isRunning")
            val intent = Intent(context, VoiceRecordService::class.java).setAction(action)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
                KeyboardDictationFeedback.pulse(context)
            } catch (error: Throwable) {
                Log.e(TAG, "Could not toggle dictation from HoneyBoard", error)
            }
        }

        @JvmStatic
        fun toggleFromIme(context: Context, inputConnection: InputConnection?, keyboard: View, event: MotionEvent) {
            toggleFromIme(context, inputConnection, keyboard)
        }

        @Volatile var isRunning: Boolean = false
        @Volatile var isProcessing: Boolean = false
        @Volatile var isStopping: Boolean = false
    }
}
