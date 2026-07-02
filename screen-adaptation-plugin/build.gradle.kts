import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.Properties
import java.util.UUID

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    `maven-publish`
    signing
}

group = "com.lunacattus.android"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradlePlugin {
    plugins {
        register("screenAdaptation") {
            id = "com.lunacattus.android.screen-adaptation"
            implementationClass = "com.lunacattus.plugin.ScreenAdaptationPlugin"
        }
    }
}

// ========== Credential resolution ==========
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> this.load(stream) }
    }
}

fun publishProperty(key: String): String? =
    project.findProperty(key)?.toString()
        ?: localProperties.getProperty(key)
        ?: System.getenv(key.uppercase().replace(".", "_"))

// ========== Dependencies for plugin compilation ==========
dependencies {
    compileOnly(gradleApi())
}

fun MavenPublication.configurePom(title: String, desc: String) {
    pom {
        name.set(title)
        description.set(desc)
        url.set("https://github.com/Sereah/LunaModules")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Sereah")
                name.set("Glacien")
                email.set("galcien.zhou@outlook.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Sereah/LunaModules.git")
            developerConnection.set("scm:git:ssh://github.com/Sereah/LunaModules.git")
            url.set("https://github.com/Sereah/LunaModules")
        }
    }
}

publishing {
    publications {
        // 1. Plugin JAR (auto-created by java-gradle-plugin as 'pluginMaven')
        all {
            if (this is MavenPublication && name == "pluginMaven") {
                configurePom(
                    "Screen Adaptation Plugin",
                    "Gradle plugin for generating Android multi-screen adaptation dimens.xml resources"
                )
            }
        }

        // 2. Plugin marker POM — metadata required by Maven Central
        all {
            if (this is MavenPublication && name.contains("PluginMarker")) {
                pom.withXml {
                    asNode().apply {
                        appendNode("name", "Screen Adaptation Plugin")
                        appendNode("description", "Gradle plugin marker for com.lunacattus.android.screen-adaptation")
                        appendNode("url", "https://github.com/Sereah/LunaModules")
                        appendNode("licenses").appendNode("license").apply {
                            appendNode("name", "The Apache License, Version 2.0")
                            appendNode("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                        appendNode("developers").appendNode("developer").apply {
                            appendNode("id", "Sereah")
                            appendNode("name", "Glacien")
                            appendNode("email", "galcien.zhou@outlook.com")
                        }
                        appendNode("scm").apply {
                            appendNode("connection", "scm:git:github.com/Sereah/LunaModules.git")
                            appendNode("developerConnection", "scm:git:ssh://github.com/Sereah/LunaModules.git")
                            appendNode("url", "https://github.com/Sereah/LunaModules")
                        }
                    }
                }
            }
        }
    }

    // Local bundle repository for staged publishing
    repositories {
        maven {
            name = "localBundle"
            url = uri(layout.buildDirectory.dir("temp-repo"))
        }
    }
}

// ========== Signing ==========
afterEvaluate {
    val signingKey = publishProperty("signing.secretKey")
    val signingPassword = publishProperty("signing.password")
    val signingKeyId = publishProperty("signing.keyId")

    if (signingKey != null && signingPassword != null) {
        signing {
            val decodedKey = try {
                if (!signingKey.contains(" ") && !signingKey.contains("\n") && !signingKey.startsWith("---")) {
                    String(Base64.getDecoder().decode(signingKey))
                } else {
                    signingKey.replace("\\n", "\n")
                }
            } catch (_: Exception) {
                signingKey.replace("\\n", "\n")
            }

            if (signingKeyId != null) useInMemoryPgpKeys(signingKeyId, decodedKey, signingPassword)
            else useInMemoryPgpKeys(decodedKey, signingPassword)

            publishing.publications.forEach { sign(it) }
        }

        // Ensure signing completes before publishing
        tasks.configureEach(object : Action<Task> {
            override fun execute(t: Task) {
                if (t.name.startsWith("publish") && t.name.endsWith("PublicationToMavenLocal")) {
                    t.dependsOn(tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") })
                }
                if (t.name.startsWith("publish") && t.name.endsWith("PublicationToLocalBundleRepository")) {
                    t.dependsOn(tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") })
                }
            }
        })
    }
}

// ========== Bundle generation and upload ==========
tasks.register<Zip>("zipDeploymentBundle") {
    group = "publishing"
    description = "Packages plugin publications into a ZIP for Central Portal upload"
    archiveFileName.set("deployment-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/bundle"))
    from(layout.buildDirectory.dir("temp-repo"))
    dependsOn(tasks.matching {
        val n = it.name
        n.startsWith("publish") && n.endsWith("PublicationToLocalBundleRepository") &&
        n.removePrefix("publish").removeSuffix("PublicationToLocalBundleRepository")
            .let { pubPart -> pubPart == "PluginMaven" || pubPart.contains("PluginMarker") }
    })
}

val publishVersion = project.version.toString()
val portalUsername = publishProperty("ossrhUsername")
val portalPassword = publishProperty("ossrhPassword")

tasks.register("publishToCentralPortal") {
    group = "publishing"
    description = "Uploads the deployment bundle to Sonatype Central Portal"
    dependsOn("zipDeploymentBundle")

    inputs.property("portalUser", portalUsername ?: "")
    inputs.property("portalPwd", portalPassword ?: "")
    inputs.property("portalVersion", publishVersion)

    val bundleDir = layout.buildDirectory.dir("outputs/bundle").get().asFile.absolutePath
    inputs.property("bundleDir", bundleDir)

    doLast {
        val props = inputs.properties
        val tokenUser = props["portalUser"] as String?
        val tokenPwd = props["portalPwd"] as String?
        val ver = props["portalVersion"] as String?
        val bundleDirStr = props["bundleDir"] as String
        val bundleFile = File(bundleDirStr, "deployment-bundle.zip")

        if (tokenUser.isNullOrBlank() || tokenPwd.isNullOrBlank()) {
            throw RuntimeException("Missing ossrhUsername or ossrhPassword in local.properties")
        }
        if (!bundleFile.exists()) throw RuntimeException("Bundle not found: ${bundleFile.path}")

        println("Uploading ${bundleFile.name} to Central Portal...")

        val boundary = "Boundary" + UUID.randomUUID().toString().replace("-", "")
        val auth = Base64.getEncoder().encodeToString("$tokenUser:$tokenPwd".toByteArray())

        val byteStream = ByteArrayOutputStream()
        val writer = byteStream.writer(Charsets.UTF_8)
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        writer.write(twoHyphens + boundary + lineEnd)
        writer.write("Content-Disposition: form-data; name=\"bundle\"; filename=\"${bundleFile.name}\"$lineEnd")
        writer.write("Content-Type: application/zip$lineEnd")
        writer.write(lineEnd)
        writer.flush()
        byteStream.write(bundleFile.readBytes())
        writer.write(lineEnd)
        writer.write(twoHyphens + boundary + twoHyphens + lineEnd)
        writer.flush()

        val uploadUri = URI.create(
            "https://central.sonatype.com/api/v1/publisher/upload" +
                    "?name=screen-adaptation-plugin-${ver}" +
                    "&publishingType=USER_MANAGED"
        )

        val request = HttpRequest.newBuilder()
            .uri(uploadUri)
            .header("Authorization", "Bearer $auth")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(byteStream.toByteArray()))
            .build()

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() in 200..299) {
            println("Upload successful! Deployment ID: ${response.body()}")
            println("Check status at: https://central.sonatype.com/deployments")
        } else {
            println("Upload failed with status ${response.statusCode()}")
            println("Response: ${response.body()}")
            throw RuntimeException("Upload failed: ${response.body()}")
        }
    }
}
