import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.Properties
import java.util.UUID

/**
 * Extension for configuring Luna module publishing information.
 * This provides DSL suggestions and documentation in build.gradle.kts.
 */
interface LunaPublishExtension {
    /** The artifact ID of the library (e.g., "common"). Defaults to module name. */
    val artifactId: Property<String>
    /** The version of the artifact (e.g., "1.0.0"). Defaults to project version. */
    val artifactVersion: Property<String>
    /** Human-readable name of the library (e.g., "Luna Common Utilities"). */
    val artifactName: Property<String>
    /** Detailed description of what the library does. */
    val artifactDescription: Property<String>
}

class AndroidLibraryPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Register the custom extension so users can use 'lunaPublish { ... }'
            val lunaPublish = extensions.create<LunaPublishExtension>("lunaPublish")

            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { localProperties.load(it) }
            }

            fun getPublishProperty(key: String): String? {
                return project.findProperty(key)?.toString()
                    ?: localProperties.getProperty(key)
                    ?: System.getenv(key.uppercase().replace(".", "_"))
            }

            with(pluginManager) {
                apply("maven-publish")
                apply("signing")
            }

            // Create a local repository to generate the full bundle with checksums
            val tempRepoDir = layout.buildDirectory.dir("temp-repo")

            // Task to generate a bundle for manual upload or API upload
            val zipBundle = tasks.register<Zip>("zipDeploymentBundle") {
                group = "publishing"
                description = "Generates a ZIP bundle for Central Portal upload"
                archiveFileName.set("deployment-bundle.zip")
                destinationDirectory.set(layout.buildDirectory.dir("outputs/bundle"))

                // We'll zip the entire temp repo
                from(tempRepoDir)

                // Ensure the publishing task runs first
                dependsOn("publishReleasePublicationToLocalBundleRepository")
            }

            // Register the upload task
            val publishTask = tasks.register("publishToCentralPortal") {
                group = "publishing"
                description = "One-click upload to Sonatype Central Portal via API"
                dependsOn(zipBundle)
            }

            // Configure Android publishing immediately when the plugin is applied
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    publishing {
                        singleVariant("release") {
                            withSourcesJar()
                            withJavadocJar()
                        }
                    }
                }
            }

            afterEvaluate {
                // Read from extension with fallbacks
                val artId = lunaPublish.artifactId.getOrElse(project.name)
                val artVersion = lunaPublish.artifactVersion.getOrElse(project.version.toString())
                val artName = lunaPublish.artifactName.getOrElse(artId)
                val artDescription = lunaPublish.artifactDescription.getOrElse("")

                val tokenUser = getPublishProperty("ossrhUsername")
                val tokenPwd = getPublishProperty("ossrhPassword")
                val projectName = project.name
                val bundleFileProvider = zipBundle.flatMap { it.archiveFile }

                publishTask.configure {
                    doLast {
                        if (tokenUser == null || tokenPwd == null) {
                            throw RuntimeException("Missing ossrhUsername or ossrhPassword (User Token) in local.properties")
                        }

                        val bundleFile = bundleFileProvider.get().asFile
                        println("Uploading ${bundleFile.name} to Central Portal...")

                        val boundary = "Boundary" + UUID.randomUUID().toString().replace("-", "")
                        val auth = Base64.getEncoder().encodeToString("$tokenUser:$tokenPwd".toByteArray())

                        val byteStream = java.io.ByteArrayOutputStream()
                        val writer = byteStream.writer(Charsets.UTF_8)

                        val lineEnd = "\r\n"
                        val twoHyphens = "--"

                        // Part: bundle
                        writer.write(twoHyphens + boundary + lineEnd)
                        writer.write("Content-Disposition: form-data; name=\"bundle\"; filename=\"${bundleFile.name}\"$lineEnd")
                        writer.write("Content-Type: application/zip$lineEnd")
                        writer.write(lineEnd)
                        writer.flush()

                        byteStream.write(bundleFile.readBytes())

                        writer.write(lineEnd)
                        writer.write(twoHyphens + boundary + twoHyphens + lineEnd)
                        writer.flush()

                        // Query parameters for name and publishingType
                        val uploadUri = URI.create(
                            "https://central.sonatype.com/api/v1/publisher/upload" +
                                    "?name=${projectName}-$artVersion" +
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

                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            groupId = "com.lunacattus.android"
                            artifactId = artId
                            version = artVersion

                            components.findByName("release")?.let { from(it) }

                            pom {
                                name.set(artName)
                                description.set(artDescription)
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
                                    connection.set("scm:git:github.com/Sereah/LunaModules.git")
                                    developerConnection.set("scm:git:ssh://github.com/Sereah/LunaModules.git")
                                    url.set("https://github.com/Sereah/LunaModules")
                                }
                            }
                        }
                    }

                    repositories {
                        maven {
                            name = "localBundle"
                            url = uri(tempRepoDir)
                        }
                    }
                }

                val signingKey = getPublishProperty("signing.secretKey")
                val signingPassword = getPublishProperty("signing.password")
                val signingKeyId = getPublishProperty("signing.keyId")

                if (signingKey != null && signingPassword != null) {
                    extensions.configure<SigningExtension> {
                        val decodedKey = try {
                            if (!signingKey.contains(" ") && !signingKey.contains("\n") && !signingKey.startsWith("---")) {
                                String(Base64.getDecoder().decode(signingKey))
                            } else {
                                signingKey.replace("\\n", "\n")
                            }
                        } catch (_: Exception) {
                            signingKey.replace("\\n", "\n")
                        }

                        if (signingKeyId != null) {
                            useInMemoryPgpKeys(signingKeyId, decodedKey, signingPassword)
                        } else {
                            useInMemoryPgpKeys(decodedKey, signingPassword)
                        }
                        sign(extensions.getByType<PublishingExtension>().publications["release"])
                    }
                }
            }
        }
    }
}
