package com.samsung.android.honeyboard.nemotron;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.inputmethod.InputConnection;
import android.inputmethodservice.InputMethodService;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/** In-process dictation bridge for Samsung's already-registered HoneyBoard IME service. */
public final class NemotronInlineDictation {
    private static final long DOUBLE_SPACE_WINDOW_MS = 750L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static InputConnection connection;
    private static SpeechRecognizer recognizer;
    private static final StringBuilder recognized = new StringBuilder();
    private static long lastSpaceUptime;
    private static boolean listening;
    private static boolean stopping;
    private static boolean delivered;
    private static int generation;

    private NemotronInlineDictation() {}

    public static void bindService(InputMethodService ime) {
        service = new WeakReference<>(ime);
    }

    /** Called before HoneyBoard commits text; returns true only when consuming the second space. */
    public static synchronized boolean onSpaceCommit(InputConnection input, CharSequence text) {
        if (text == null || text.length() == 0) {
            lastSpaceUptime = 0L;
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                lastSpaceUptime = 0L;
                return false;
            }
        }

        long now = SystemClock.uptimeMillis();
        boolean paired = lastSpaceUptime != 0L && now - lastSpaceUptime <= DOUBLE_SPACE_WINDOW_MS;
        if (text.length() == 1 && !paired) {
            lastSpaceUptime = now;
            return false;
        }

        lastSpaceUptime = 0L;
        if (paired) {
            try { input.deleteSurroundingText(1, 0); } catch (Throwable ignored) {}
        }
        toggle(input);
        return true;
    }

    private static void toggle(InputConnection input) {
        InputMethodService ime = service.get();
        if (ime == null) return;
        if (listening) {
            stopping = true;
            try { if (recognizer != null) recognizer.stopListening(); } catch (Throwable ignored) {}
            final int token = generation;
            MAIN.postDelayed(() -> finish(token), 1800L);
            return;
        }
        if (ime.checkSelfPermission("android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + ime.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ime.startActivity(intent);
            } catch (Throwable ignored) {}
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ime)) return;
        connection = input;
        recognized.setLength(0);
        stopping = false;
        delivered = false;
        listening = true;
        generation++;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ime);
            recognizer.setRecognitionListener(listener);
            showWaveform(true);
            startListening(generation);
        } catch (Throwable error) {
            cleanup();
        }
    }

    private static void startListening(int token) {
        if (!listening || stopping || token != generation || recognizer == null) return;
        Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-MX");
        request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { recognizer.startListening(request); }
        catch (Throwable error) {
            MAIN.postDelayed(() -> startListening(token), 200L);
        }
    }

    private static final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) { NemotronKeyboardWaveform.setLevel(rmsdB); }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
        @Override public void onError(int error) {
            if (!listening) return;
            if (stopping) finish(generation);
            else if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || error == SpeechRecognizer.ERROR_CLIENT) {
                MAIN.postDelayed(() -> startListening(generation), 80L);
            } else {
                cleanup();
            }
        }
        @Override public void onResults(Bundle results) {
            ArrayList<String> items = results == null ? null
                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String phrase = items == null || items.isEmpty() ? "" : items.get(0).trim();
            if (!phrase.isEmpty()) {
                if (recognized.length() > 0 && recognized.charAt(recognized.length() - 1) != ' ') recognized.append(' ');
                recognized.append(phrase);
            }
            if (stopping) finish(generation);
            else MAIN.postDelayed(() -> startListening(generation), 80L);
        }
    };

    private static void finish(int token) {
        if (token != generation || delivered) return;
        delivered = true;
        String text = recognized.toString().trim().toLowerCase(Locale.ROOT);
        InputConnection target = connection;
        if (!text.isEmpty() && target != null) {
            try { target.commitText(text + " ", 1); } catch (Throwable ignored) {}
        } else {
            // No toast: the spacebar waveform is the recording feedback.
        }
        cleanup();
    }

    private static void cleanup() {
        MAIN.removeCallbacksAndMessages(null);
        try { if (recognizer != null) recognizer.destroy(); } catch (Throwable ignored) {}
        NemotronKeyboardWaveform.setRecording(false);
        recognizer = null;
        connection = null;
        recognized.setLength(0);
        listening = false;
        stopping = false;
        delivered = false;
    }

    private static void showWaveform(boolean recording) {
        InputMethodService ime = service.get();
        if (ime == null) return;
        try {
            android.view.Window window = ime.getWindow() == null ? null : ime.getWindow().getWindow();
            if (window == null) return;
            android.view.View keyboard = window.getDecorView();
            NemotronKeyboardWaveform.bind(keyboard, keyboard.getWidth() / 2f);
            NemotronKeyboardWaveform.setRecording(recording);
        } catch (Throwable ignored) {}
    }
}
