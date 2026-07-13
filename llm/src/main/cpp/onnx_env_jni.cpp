#include <jni.h>
#include <cstdlib>
#include <string>
#include "tool/logger.h"

/**
 * 设置 QNN NPU 所需的环境变量，使 FastRPC / DSP 运行时能找到：
 * - libQnnHtpV*Stub.so（CPU 侧 Stub）
 * - libQnnHtpV*Skel.so（DSP/NPU 侧 Skel，Hexagon 字节码）
 * - .cat 签名文件（若存在）
 *
 * 必须在 ONNX Runtime 创建 Session 之前调用。
 *
 * @param nativeLibDir  APK 解压后的 native 库目录绝对路径
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeSetQnnEnv(JNIEnv *env, jclass,
                                                               jstring native_lib_dir, jstring skel_dir) {
    const char *path = env->GetStringUTFChars(native_lib_dir, nullptr);
    const char *skelPath = env->GetStringUTFChars(skel_dir, nullptr);
    LOGI("%s: nativeLibDir=%s, skelDir=%s", __func__, path, skelPath);

    const char *existingAdsp = getenv("ADSP_LIBRARY_PATH");
    std::string adspPath = std::string(skelPath) + ";" + std::string(path);
    if (existingAdsp) {
        adspPath += ';';
        adspPath += existingAdsp;
    }
    int ret1 = setenv("ADSP_LIBRARY_PATH", adspPath.c_str(), 1);

    const char *existingLd = getenv("LD_LIBRARY_PATH");
    std::string ldPath = existingLd ? std::string(path) + ":" + existingLd : std::string(path);
    int ret2 = setenv("LD_LIBRARY_PATH", ldPath.c_str(), 1);

    env->ReleaseStringUTFChars(native_lib_dir, path);
    env->ReleaseStringUTFChars(skel_dir, skelPath);

    if (ret1 != 0) {
        LOGE("%s: setenv ADSP_LIBRARY_PATH 失败 (ret=%d)", __func__, ret1);
        return 1;
    }
    LOGI("%s: QNN 环境已设置 (ADSP_LIBRARY_PATH=%s)", __func__, skelPath);
    return 0;
}
