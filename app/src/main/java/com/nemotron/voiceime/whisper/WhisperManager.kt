package com.nemotron.voiceime.whisper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class WhisperManager(private val context: Context) {

    private var ctxPtr: Long = 0
    private val modelDir = File(context.filesDir, "whisper_models")
    private val EXPECTED_SIZE = 77691713L // ggml-tiny.bin

    val isInitialized: Boolean get() = ctxPtr != 0L

    fun init(): Boolean {
        return try {
            val modelFile = ensureModel()
            if (modelFile == null) {
                Log.e(TAG, "Model not available")
                return false
            }
            Log.d(TAG, "Loading model: ${modelFile.absolutePath} size=${modelFile.length()}")
            ctxPtr = WhisperNative.init(modelFile.absolutePath)
            if (ctxPtr == 0L) {
                Log.e(TAG, "whisper_init returned null")
                return false
            }
            Log.d(TAG, "Model loaded successfully ctxPtr=$ctxPtr")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            false
        }
    }

    fun transcribe(pcmData: FloatArray, language: String = "en"): String? {
        if (ctxPtr == 0L) return null
        return try {
            Log.d(TAG, "Transcribing ${pcmData.size} samples, lang=$language")
            val ret = WhisperNative.full(ctxPtr, pcmData, language)
            if (ret != 0) {
                Log.e(TAG, "Transcribe failed: $ret")
                return null
            }
            val text = WhisperNative.getText(ctxPtr)
            Log.d(TAG, "Result: $text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Transcribe error", e)
            null
        }
    }

    fun release() {
        if (ctxPtr != 0L) {
            WhisperNative.free(ctxPtr)
            ctxPtr = 0
        }
    }

    private fun ensureModel(): File? {
        modelDir.mkdirs()
        val modelFile = File(modelDir, "ggml-tiny.bin")

        if (modelFile.exists() && modelFile.length() == EXPECTED_SIZE) {
            return modelFile
        }

        if (modelFile.exists()) {
            modelFile.delete()
        }

        return try {
            Log.d(TAG, "Copying model from assets...")
            context.assets.open("whisper/ggml-tiny.bin").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (modelFile.length() != EXPECTED_SIZE) {
                Log.e(TAG, "Model copy incomplete: ${modelFile.length()} != $EXPECTED_SIZE")
                modelFile.delete()
                return null
            }
            Log.d(TAG, "Model copied successfully: ${modelFile.length()} bytes")
            modelFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model", e)
            modelFile.delete()
            null
        }
    }

    companion object {
        private const val TAG = "WhisperManager"
    }
}
