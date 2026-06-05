plugins {
    alias(libs.plugins.android.library)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("common")
    artifactVersion.set("1.0.0")
    artifactName.set("Luna Common Utilities")
    artifactDescription.set("Core common utilities for Luna modules, supporting safe coroutines and some tools.")
}

android {
    namespace = "com.lunacattus.common"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}