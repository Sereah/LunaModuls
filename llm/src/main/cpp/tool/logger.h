#pragma once

#include <android/log.h>
#include "ggml/include/ggml.h"

#ifndef LOG_TAG
#define LOG_TAG "LlmSdkNative"
#endif

#ifndef LOG_MIN_LEVEL
#ifdef NDEBUG
#define LOG_MIN_LEVEL ANDROID_LOG_INFO
#else
#define LOG_MIN_LEVEL ANDROID_LOG_VERBOSE
#endif
#endif

static inline int AiShouldLog(int prio) {
#if __ANDROID_API__ >= 30
    return __android_log_is_loggable(prio, LOG_TAG, LOG_MIN_LEVEL);
#else
    (void)prio;
    return 1;
#endif
}

#if LOG_MIN_LEVEL <= ANDROID_LOG_VERBOSE
#define LOGV(...) do { if (AiShouldLog(ANDROID_LOG_VERBOSE)) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__); } while (0)
#else
#define LOGV(...) ((void)0)
#endif

#if LOG_MIN_LEVEL <= ANDROID_LOG_DEBUG
#define LOGD(...) do { if (AiShouldLog(ANDROID_LOG_DEBUG)) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); } while (0)
#else
#define LOGD(...) ((void)0)
#endif

#define LOGI(...)   do { if (AiShouldLog(ANDROID_LOG_INFO )) __android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__); } while (0)
#define LOGW(...)   do { if (AiShouldLog(ANDROID_LOG_WARN )) __android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__); } while (0)
#define LOGE(...)   do { if (AiShouldLog(ANDROID_LOG_ERROR)) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); } while (0)

static inline int AndroidLogPrioFromGgml(enum ggml_log_level level) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            return ANDROID_LOG_ERROR;
        case GGML_LOG_LEVEL_WARN:
            return ANDROID_LOG_WARN;
        case GGML_LOG_LEVEL_INFO:
            return ANDROID_LOG_INFO;
        case GGML_LOG_LEVEL_DEBUG:
            return ANDROID_LOG_DEBUG;
        default:
            return ANDROID_LOG_DEFAULT;
    }
}

static inline void AndroidLogCallback(enum ggml_log_level level,
                                      const char *text,
                                      void * /*user*/) {
    const int prio = AndroidLogPrioFromGgml(level);
    if (!AiShouldLog(prio)) return;
    __android_log_write(prio, LOG_TAG, text);
}

