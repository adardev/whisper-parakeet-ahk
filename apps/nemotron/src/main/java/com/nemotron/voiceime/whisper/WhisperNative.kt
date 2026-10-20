package com.nemotron.voiceime.whisper

object WhisperNative {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun init(modelPath: String): Long
    external fun free(ctxPtr: Long)
    external fun full(ctxPtr: Long, audioData: FloatArray, language: String): Int
    external fun getText(ctxPtr: Long): String
}
