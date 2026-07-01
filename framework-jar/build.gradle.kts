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
    `java-library`
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

// ========== Credential resolution (same pattern as build-logic) ==========
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
    compileOnly(libs.kotlin.gradlePlugin)
}

// ========== Framework JAR publishing — auto-discovered from frameworkLibs/ ==========
//
// Files follow the naming convention:
//   {type}-{version}.jar           →  standard variant
//   {type}-custom-{version}.jar    →  custom variant
//
// For each version, a bundle POM (android-framework-bundle) is auto-generated
// listing all discovered JARs as dependencies. The consumer plugin resolves
// this bundle to get everything — no companion list needed on either side.

data class JarEntry(val type: String, val version: String, val isCustom: Boolean, val file: File)

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
        // Capture project values as local variables for configuration cache compatibility
        val pg = project.group.toString()
        val pn = project.name
        val pv = project.version.toString()

        // 1. Plugin JAR — used via buildscript { classpath("...") }
        create<MavenPublication>("pluginJar") {
            groupId = pg
            artifactId = pn
            version = pv
            from(components["java"])
            configurePom("Framework Jar Plugin", "Gradle plugin for compiling against AOSP framework JARs")
        }

        // 2. Plugin marker artifact — allows plugins { id("...") version "..." } DSL
        create<MavenPublication>("pluginMarker") {
            groupId = "com.lunacattus.android.framework.jar"
            artifactId = "com.lunacattus.android.framework.jar.gradle.plugin"
            version = pv

            pom.withXml {
                asNode().apply {
                    appendNode("name", "Framework Jar Plugin")
                    appendNode("description", "Gradle plugin marker for lunacattus.android.framework.jar")
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
                    appendNode("dependencies").appendNode("dependency").apply {
                        appendNode("groupId", pg)
                        appendNode("artifactId", pn)
                        appendNode("version", pv)
                        appendNode("scope", "runtime")
                    }
                }
            }
        }

        // 3. Framework JAR files — auto-discovered from frameworkLibs/
        //
        //   {type}-{version}.jar           →  {type}-framework:{version}        (individual JAR)
        //   {type}-custom-{version}.jar    →  {type}-framework-custom:{version}  (individual JAR)
        //   (auto)                         →  android-framework-bundle:{version}  (POM listing all)
        //
        //   Special case: "framework" type → artifact ID "android-framework"
        //
        val frameworkDir = file("frameworkLibs")
        val standardRegex = Regex("""^([a-zA-Z]+)-(\d+)\.jar$""")
        val customRegex = Regex("""^([a-zA-Z]+)-custom-(\d+)\.jar$""")

        // Discover all JARs and group by version
        val allEntries = frameworkDir.listFiles().orEmpty()
            .filter { it.extension == "jar" }
            .sortedBy { it.name }
            .mapNotNull { jarFile ->
                customRegex.matchEntire(jarFile.name)?.let {
                    JarEntry(it.groupValues[1], it.groupValues[2], true, jarFile)
                } ?: standardRegex.matchEntire(jarFile.name)?.let {
                    JarEntry(it.groupValues[1], it.groupValues[2], false, jarFile)
                }
            }

        val byVersion = allEntries.groupBy { it.version }

        // Create individual publications for each JAR
        allEntries.forEach { jar ->
            val artSuffix = if (jar.isCustom) "-custom" else ""
            val artId = if (jar.type == "framework") "android-framework$artSuffix" else "${jar.type}-framework$artSuffix"
            val pubName = "${jar.type}${jar.version}${if (jar.isCustom) "Custom" else ""}"
            val displayType = jar.type.replaceFirstChar { it.uppercase() }

            // Empty sources/javadoc JARs for Maven Central compliance
            val sourcesTask = tasks.register("emptySources${pubName}", Jar::class) {
                archiveBaseName.set(artId)
                archiveVersion.set(jar.version)
                archiveClassifier.set("sources")
            }
            val javadocTask = tasks.register("emptyJavadoc${pubName}", Jar::class) {
                archiveBaseName.set(artId)
                archiveVersion.set(jar.version)
                archiveClassifier.set("javadoc")
            }

            create<MavenPublication>(pubName) {
                groupId = pg
                artifactId = artId
                version = jar.version
                artifact(jar.file)
                artifact(sourcesTask)
                artifact(javadocTask)
                configurePom(
                    "Android $displayType Framework JAR (API ${jar.version}${if (jar.isCustom) " custom" else ""})",
                    "AOSP ${jar.type} framework JAR for API level ${jar.version}${if (jar.isCustom) " (custom build)" else ""}"
                )
            }
        }

        // Create bundle POM per version
        //   android-framework-bundle:{v}        — only standard jars
        //   android-framework-bundle-custom:{v} — all jars (custom if available, else standard)
        //     (only published if version has at least one custom file)
        byVersion.forEach { (ver, entries) ->
            val standardOnly = entries.filter { !it.isCustom }
            val hasCustom = entries.any { it.isCustom }

            fun addBundle(artSuffix: String, jars: List<JarEntry>) {
                val pubName = "bundle${ver}${if (artSuffix == "-custom") "Custom" else ""}"
                create<MavenPublication>(pubName) {
                    groupId = pg
                    artifactId = "android-framework-bundle$artSuffix"
                    version = ver

                    pom.withXml {
                        asNode().apply {
                            appendNode("name", "Android Framework Bundle (API $ver${if (artSuffix == "-custom") " custom" else ""})")
                            appendNode("description", "Complete AOSP framework bundle for API level $ver")
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

                            val deps = appendNode("dependencies")
                            jars.forEach { jar ->
                                val aid = if (jar.type == "framework") "android-framework$artSuffix" else "${jar.type}-framework$artSuffix"
                                deps.appendNode("dependency").apply {
                                    appendNode("groupId", pg)
                                    appendNode("artifactId", aid)
                                    appendNode("version", ver)
                                    appendNode("scope", "compile")
                                }
                            }
                        }
                    }
                }
            }

            // Standard bundle (always)
            addBundle("", standardOnly)

            // Custom bundle (only if any custom jar exists for this version)
            if (hasCustom) {
                val types = entries.map { it.type }.distinct()
                val customBestEffort = types.map { type ->
                    entries.find { it.isCustom && it.type == type }
                        ?: entries.find { !it.isCustom && it.type == type }!!
                }
                addBundle("-custom", customBestEffort)
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
    }
}

// ========== Bundle generation and upload ==========
tasks.register<Zip>("zipDeploymentBundle") {
    group = "publishing"
    description = "Packages all publications into a ZIP for Central Portal upload"
    archiveFileName.set("deployment-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/bundle"))
    from(layout.buildDirectory.dir("temp-repo"))
    dependsOn(tasks.withType<AbstractPublishToMaven>())
}

val publishVersion = project.version.toString()
val portalUsername = publishProperty("ossrhUsername")
val portalPassword = publishProperty("ossrhPassword")

tasks.register("publishToCentralPortal") {
    group = "publishing"
    description = "Uploads the deployment bundle to Sonatype Central Portal"
    dependsOn("zipDeploymentBundle")

    // Store values as task inputs — serializable by Gradle, no script reference in doLast
    inputs.property("portalUser", portalUsername ?: "")
    inputs.property("portalPwd", portalPassword ?: "")
    inputs.property("portalVersion", publishVersion)

    // Resolve bundle dir at configuration time — store as string for config cache safety
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
                    "?name=framework-jar-${ver}" +
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
