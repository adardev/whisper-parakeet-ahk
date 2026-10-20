#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_nemotron_voiceime_whisper_WhisperNative_init(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = (*env)->GetStringUTFChars(env, modelPath, NULL);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    (*env)->ReleaseStringUTFChars(env, modelPath, path);

    if (ctx) {
        LOGI("Model loaded successfully");
    } else {
        LOGE("Failed to load model");
    }

    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_nemotron_voiceime_whisper_WhisperNative_free(JNIEnv *env, jobject thiz, jlong ctxPtr) {
    struct whisper_context *ctx = (struct whisper_context *)(intptr_t)ctxPtr;
    if (ctx) {
        whisper_free(ctx);
        LOGI("Model freed");
    }
}

JNIEXPORT jint JNICALL
Java_com_nemotron_voiceime_whisper_WhisperNative_full(JNIEnv *env, jobject thiz,
        jlong ctxPtr, jfloatArray audioData, jstring lang) {
    struct whisper_context *ctx = (struct whisper_context *)(intptr_t)ctxPtr;
    if (!ctx) {
        LOGE("Context is null");
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, audioData);
    LOGI("Transcribing %d samples", len);

    if (len == 0) {
        LOGE("Empty audio data");
        return -1;
    }

    jfloat *pcm = (*env)->GetFloatArrayElements(env, audioData, NULL);
    if (!pcm) {
        LOGE("Failed to get float array");
        return -1;
    }

    const char *langStr = (*env)->GetStringUTFChars(env, lang, NULL);
    LOGI("Language: %s", langStr);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.single_segment = false;
    wparams.no_timestamps = true;
    wparams.n_threads = 1;
    wparams.language = langStr;

    LOGI("Calling whisper_full with n_threads=%d, n_samples=%d, lang=%s", wparams.n_threads, len, langStr);
    int ret = whisper_full(ctx, wparams, pcm, len);
    LOGI("whisper_full returned: %d", ret);

    (*env)->ReleaseFloatArrayElements(env, audioData, pcm, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, lang, langStr);
    return ret;
}

JNIEXPORT jstring JNICALL
Java_com_nemotron_voiceime_whisper_WhisperNative_getText(JNIEnv *env, jobject thiz, jlong ctxPtr) {
    struct whisper_context *ctx = (struct whisper_context *)(intptr_t)ctxPtr;
    if (!ctx) return (*env)->NewStringUTF(env, "");

    const int n_segments = whisper_full_n_segments(ctx);
    LOGI("Got %d segments", n_segments);

    if (n_segments <= 0) return (*env)->NewStringUTF(env, "");

    int total_len = 0;
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        total_len += strlen(text);
    }

    char *result = (char *)malloc(total_len + 1);
    result[0] = '\0';
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        strcat(result, text);
    }

    jstring jresult = (*env)->NewStringUTF(env, result);
    free(result);
    return jresult;
}
