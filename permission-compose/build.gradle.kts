plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("permission-compose")
    artifactVersion.set("1.0.0")
    artifactName.set("Luna permission Utilities")
    artifactDescription.set("Utilities for Luna Permission")
}

android {
    namespace = "com.lunacattus.permission.compose"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.foundation)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
}