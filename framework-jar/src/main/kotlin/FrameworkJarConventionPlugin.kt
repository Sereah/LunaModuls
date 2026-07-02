import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class FrameworkJarExtension {
    var version: String? = null
    var custom: Boolean = false
}

class FrameworkJarConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("frameworkJar", FrameworkJarExtension::class.java)

        project.afterEvaluate {
            val frameworkVersion = extension.version ?: "12"
            val isCustom = extension.custom
            val suffix = if (isCustom) "-custom" else ""

            // Resolvable configuration — resolved to actual files for classpath use
            val frameworkConfig: Configuration = project.configurations.create("frameworkJarDeps")
            frameworkConfig.setCanBeConsumed(false)
            frameworkConfig.setCanBeResolved(true)

            // Resolve the bundle POM which transitively brings in all companions
            val bundleDep = "com.lunacattus.android:android-framework-bundle$suffix:$frameworkVersion"
            project.dependencies.add("compileOnly", bundleDep)
            if (project.plugins.hasPlugin("com.google.devtools.ksp")) {
                project.dependencies.add("ksp", bundleDep)
            }
            project.dependencies.add(frameworkConfig.name, bundleDep)

            // Configure bootstrapClasspath for JavaCompile
            val javaTasks: TaskCollection<JavaCompile> = project.tasks.withType(JavaCompile::class.java)
            javaTasks.configureEach(object : Action<JavaCompile> {
                override fun execute(task: JavaCompile) {
                    val originalClasspath = task.options.bootstrapClasspath?.files ?: emptySet()
                    task.options.bootstrapClasspath = project.files(frameworkConfig, originalClasspath)
                }
            })

            // Configure libraries for KotlinCompile
            val kotlinTasks: TaskCollection<KotlinCompile> = project.tasks.withType(KotlinCompile::class.java)
            kotlinTasks.configureEach(object : Action<KotlinCompile> {
                override fun execute(task: KotlinCompile) {
                    val currentLibraries = task.libraries
                    task.libraries.setFrom(frameworkConfig, currentLibraries)
                }
            })
        }
    }
}
