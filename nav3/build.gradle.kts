plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("lunacattus.android.library.publish")
}

lunaPublish {
    artifactId.set("nav3")
    artifactVersion.set("1.0.0")
    artifactName.set("Luna Navigation3 Utilities")
    artifactDescription.set("Navigation3 utilities for Luna")
}

android {
    namespace = "com.lunacattus.nav3"
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
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.savedstate.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.material3.adaptive.navigation.suite)
}
