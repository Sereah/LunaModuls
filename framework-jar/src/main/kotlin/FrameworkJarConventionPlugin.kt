import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class FrameworkJarExtension {
    var version: String? = null
    var custom: Boolean = false
}

class FrameworkJarConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<FrameworkJarExtension>("frameworkJar")

        project.afterEvaluate {
            val frameworkVersion = extension.version ?: "12"
            val isCustom = extension.custom
            val suffix = if (isCustom) "-custom" else ""

            // Resolvable configuration — resolved to actual files for classpath use
            val frameworkConfig = project.configurations.create("frameworkJarDeps") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

            // Resolve the bundle POM which transitively brings in all companions
            val bundleDep = "com.lunacattus.android:android-framework-bundle$suffix:$frameworkVersion"
            project.dependencies {
                add("compileOnly", bundleDep)
                if (project.plugins.hasPlugin("com.google.devtools.ksp")) {
                    add("ksp", bundleDep)
                }
                add(frameworkConfig.name, bundleDep)
            }

            // Print resolved JARs after resolution
            frameworkConfig.incoming.afterResolve {
                val artifacts = it.artifacts.artifacts
                project.logger.lifecycle("[framework-jar] API $frameworkVersion (${artifacts.size} files):")
                artifacts.sortedBy { a -> a.file.name }.forEach { a ->
                    project.logger.lifecycle("  └─ ${a.file.name}")
                }
            }

            // Configure bootstrapClasspath for JavaCompile
            project.tasks.withType<JavaCompile>().configureEach {
                val originalClasspath = options.bootstrapClasspath?.files ?: emptySet()
                options.bootstrapClasspath = project.files(frameworkConfig, originalClasspath)
            }

            // Configure libraries for KotlinCompile
            project.tasks.withType<KotlinCompile>().configureEach {
                val currentLibraries = libraries
                libraries.setFrom(frameworkConfig, currentLibraries)
            }
        }
    }
}
