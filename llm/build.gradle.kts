plugins {
    alias(libs.plugins.android.library)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("llm")
    artifactVersion.set("1.0.2")
    artifactName.set("Luna llm Utilities")
    artifactDescription.set("Llm utilities for Luna")
}

android {
    namespace = "com.lunacattus.llm"
    ndkVersion = "29.0.13113456"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // 通用编译与调试配置
                arguments += "-DCMAKE_BUILD_TYPE=Release" // 指定编译模式为 Release（发行版）
                arguments += "-DCMAKE_MESSAGE_LOG_LEVEL=DEBUG" // 设置 CMake 配置阶段的日志级别为 DEBUG
                arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON" // 开启冗长编译模式

                // 基础组件与依赖配置
                arguments += "-DBUILD_SHARED_LIBS=ON" // 强制将目标编译为动态链接库
                arguments += "-DLLAMA_BUILD_APP=OFF" // 关闭 llama.cpp 自带的命令行可执行程序的编译
                arguments += "-DLLAMA_BUILD_COMMON=ON" // 开启 llama.cpp common 基础公共库的编译
                arguments += "-DLLAMA_OPENSSL=OFF" // 关闭 OpenSSL 依赖

                // GGML/LLM 推理核心性能配置
                arguments += "-DGGML_NATIVE=OFF" // 关闭原生本地优化（-march=native）。
                arguments += "-DGGML_BACKEND_DL=ON" // 开启动态加载后端驱动（Dynamic Loading）。
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON" // 开启 CPU 所有指令集变体的支持。
                arguments += "-DGGML_LLAMAFILE=OFF" // 关闭 llamafile 格式相关的支持
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.27.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.13.0")
}
