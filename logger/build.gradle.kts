plugins {
    alias(libs.plugins.android.library)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("logger")
    artifactVersion.set("1.1.0")
    artifactName.set("Luna Log Utilities")
    artifactDescription.set("Logger utilities for Luna")
}

android {
    namespace = "com.lunacattus.logger"
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
}
