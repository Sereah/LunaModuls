plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("androidLibraryPublish") {
            id = "lunacattus.android.library.publish"
            implementationClass = "AndroidLibraryPublishConventionPlugin"
        }
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}
