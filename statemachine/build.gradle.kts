plugins {
    alias(libs.plugins.android.library)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("statemachine")
    artifactVersion.set("1.0.0")
    artifactName.set("Luna StateMachine Utilities")
    artifactDescription.set("Utilities for Luna StateMachine")
}

android {
    namespace = "com.lunacattus.statemachine"
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
    implementation(libs.annotation.jvm)
    implementation(libs.kotlinx.coroutines.core)
}