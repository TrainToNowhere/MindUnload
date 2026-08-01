// JNI bridge for on-device speech recognition with whisper.cpp.
//
// Deliberately narrow: load a model, transcribe one buffer of 16 kHz mono float
// samples, free the model. Everything else (recording, resampling, threading policy)
// stays on the Kotlin side.

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <sys/sysinfo.h>

#include "whisper.h"

#define TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_app_mindunload_ai_WhisperLib_initContext(
        JNIEnv *env, jobject thiz, jstring model_path) {
    (void) thiz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);

    struct whisper_context_params params = whisper_context_default_params();
    // No GPU backend is compiled in; asking for one only produces warnings.
    params.use_gpu = false;

    struct whisper_context *context = whisper_init_from_file_with_params(path, params);
    (*env)->ReleaseStringUTFChars(env, model_path, path);

    if (context == NULL) {
        LOGW("failed to load model");
        return 0;
    }
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_app_mindunload_ai_WhisperLib_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env;
    (void) thiz;
    if (context_ptr != 0) {
        whisper_free((struct whisper_context *) context_ptr);
    }
}

/**
 * Transcribes 16 kHz mono samples in [-1, 1].
 * language: ISO code ("de", "en") or "auto" for detection.
 * Returns the joined text, or NULL when inference failed.
 */
JNIEXPORT jstring JNICALL
Java_com_app_mindunload_ai_WhisperLib_transcribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language) {
    (void) thiz;
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    if (context == NULL) return NULL;

    jfloat *samples = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize sample_count = (*env)->GetArrayLength(env, audio_data);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;                     // transcribe, never translate
    params.language = lang;
    params.detect_language = (strcmp(lang, "auto") == 0);
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;
    // Voice messages are dictation, not prose: suppressing the non-speech tokens keeps
    // "[BLANK_AUDIO]" and friends out of the transcript.
    params.suppress_nst = true;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, samples, sample_count);

    (*env)->ReleaseFloatArrayElements(env, audio_data, samples, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, language, lang);

    if (result != 0) {
        LOGW("whisper_full failed: %d", result);
        return NULL;
    }

    const int segments = whisper_full_n_segments(context);
    size_t total = 1;
    for (int i = 0; i < segments; i++) {
        total += strlen(whisper_full_get_segment_text(context, i));
    }
    char *text = (char *) calloc(total, sizeof(char));
    if (text == NULL) return NULL;
    for (int i = 0; i < segments; i++) {
        strcat(text, whisper_full_get_segment_text(context, i));
    }

    jstring out = (*env)->NewStringUTF(env, text);
    free(text);
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_app_mindunload_ai_WhisperLib_systemInfo(JNIEnv *env, jobject thiz) {
    (void) thiz;
    return (*env)->NewStringUTF(env, whisper_print_system_info());
}
