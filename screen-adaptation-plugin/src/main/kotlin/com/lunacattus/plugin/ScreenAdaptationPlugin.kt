package com.lunacattus.plugin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.Serializable
import java.lang.StringBuilder
import java.util.Locale

/**
 * 目标屏幕参数接口
 * 必须实现 Serializable 以支持 Gradle 的任务配置缓存 (Configuration Cache)
 */
interface TargetScreen : Serializable {
    val widthPx: Int
    val heightPx: Int
    val dpi: Int
}

/**
 * 目标屏幕参数的具体实现类
 */
data class TargetScreenImpl(
    override val widthPx: Int,
    override val heightPx: Int,
    override val dpi: Int
) : TargetScreen

/**
 * 插件配置项 (Extension)
 * 用户在 build.gradle.kts 中通过 screenAdaptation { ... } 块来配置这些属性
 */
abstract class ScreenAdaptationExtension {
    // 设计图宽度（单位：像素 Px）
    abstract val designWidthPx: Property<Int>
    // 设计图高度（单位：像素 Px）
    abstract val designHeightPx: Property<Int>
    // 设计图的 DPI（例如 160 代表 mdpi，320 代表 xhdpi）
    abstract val designDpi: Property<Int>
    
    // 用户手动指定的目标设备屏幕列表
    abstract val targetScreens: ListProperty<TargetScreen>
    
    // 适配基准："width" 基于宽度比例缩放，"height" 基于高度比例缩放
    abstract val baseOn: Property<String>
    // 生成尺寸的最大值（比如生成到 1920，则会自动生成 _1px 到 _1920px）
    // 如果不设置，插件会自动读取设计图宽高的最大值作为上限
    abstract val maxDp: Property<Int>

    // dp 资源的命名格式，支持 {i} 占位符。默认值为 "_{i}px" (生成如 <dimen name="_10px">...)
    abstract val dpNameFormat: Property<String>
    // sp (字体) 资源的命名格式，支持 {i} 占位符。默认值为 "_font_{i}px"
    abstract val spNameFormat: Property<String>

    // 是否开启 ADB 自动探测：开启后插件会自动获取当前连接手机的参数并生成对应的适配文件
    abstract val autoTargetDevice: Property<Boolean>
    
    // 可选：指定外部配置文件（如 CSV 格式）的路径，用于批量引入目标屏幕
    abstract val targetConfigFile: RegularFileProperty
    
    init {
        // 设置默认配置，简化用户的 build.gradle.kts 编写
        baseOn.convention("width")
        dpNameFormat.convention("_{i}px")
        spNameFormat.convention("_font_{i}px")
        autoTargetDevice.convention(false)
    }
    
    /**
     * 提供给 Gradle 脚本的高级 DSL 方法，方便用户直接添加目标屏幕
     * 使用示例：target(1280, 720, 240)
     */
    fun target(widthPx: Int, heightPx: Int, dpi: Int) {
        targetScreens.add(TargetScreenImpl(widthPx, heightPx, dpi))
    }
}

/**
 * 屏幕适配 Gradle 插件主类
 */
class ScreenAdaptationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. 在项目中注册名为 "screenAdaptation" 的配置块
        val extension = project.extensions.create("screenAdaptation", ScreenAdaptationExtension::class.java)
        
        // 2. 指定生成的适配资源存放路径（build 目录下的临时路径，避免污染源码）
        val outputDirectory = project.layout.buildDirectory.dir("generated/res/screenAdaptation")
        
        // 3. 注册名为 "generateDimens" 的 Gradle Task
        val taskProvider = project.tasks.register("generateDimens", GenerateDimensTask::class.java)
        taskProvider.configure(object : Action<GenerateDimensTask> {
            override fun execute(task: GenerateDimensTask) {
                task.group = "screen adaptation"
                task.description = "根据设计图参数自动生成 Android 多屏幕适配 dimens.xml 资源"

                // 将 Extension 中的用户输入传递给 Task 内部
                task.designWidthPx.set(extension.designWidthPx)
                task.designHeightPx.set(extension.designHeightPx)
                task.designDpi.set(extension.designDpi)
                task.targetScreens.set(extension.targetScreens)
                task.baseOn.set(extension.baseOn)
                task.maxDp.set(extension.maxDp)
                task.dpNameFormat.set(extension.dpNameFormat)
                task.spNameFormat.set(extension.spNameFormat)
                task.autoTargetDevice.set(extension.autoTargetDevice)
                task.targetConfigFile.set(extension.targetConfigFile)

                task.outputDir.set(outputDirectory)
                // 获取项目源码中的 res 目录，用于存放通用的 values/dimens.xml
                task.srcResDir.set(project.file("src/main/res"))

                // 核心逻辑：如果开启了 ADB 自动探测，则禁用 Gradle 的增量构建缓存 (UP-TO-DATE)
                // 因为手机连接状态是变化的，必须每次都跑一次检测逻辑
                task.outputs.upToDateWhen { !task.autoTargetDevice.get() }
            }
        })
        
        // 4. 将该任务挂载到 Android 工程的生命周期中
        // 所有的 preBuild（预编译）任务都依赖我们的资源生成任务
        project.tasks.matching(Spec<Task> { it.name == "preBuild" })
            .configureEach(object : Action<Task> {
                override fun execute(t: Task) {
                    t.dependsOn(taskProvider)
                }
            })
        
        /**
         * 5. 资源路径映射逻辑
         * 因为我们的适配文件生成在 build 目录下，Android 默认不会去读取。
         * 我们需要手动把 build/generated/res/screenAdaptation 加到 Android 的 sourceSets 文件夹列表里。
         * 为了兼容 Application 和 Library 模块，我们使用反射来实现通用的 DSL 访问。
         */
        val addResAction = {
            try {
                // 查找名为 "android" 的 Extension（即 android { ... } 块）
                val android = project.extensions.findByName("android")
                if (android != null) {
                    // 反射获取 getSourceSets() 方法
                    val getSourceSets = android.javaClass.methods.first { it.name == "getSourceSets" }
                    val sourceSets = getSourceSets.invoke(android)
                    
                    // 获取名为 "main" 的 SourceSet
                    val getByName = sourceSets.javaClass.methods.first { it.name == "getByName" && it.parameterTypes.size == 1 }
                    val main = getByName.invoke(sourceSets, "main")
                    
                    // 获取 res 资源文件夹集合 (AndroidSourceDirectorySet)
                    val getRes = main.javaClass.methods.first { it.name == "getRes" }
                    val res = getRes.invoke(main)
                    
                    // 调用 res.srcDir() 方法，将我们的 build 输出目录动态加入进来
                    val srcDir = res.javaClass.methods.first { it.name == "srcDir" && it.parameterTypes.size == 1 }
                    srcDir.invoke(res, outputDirectory.get().asFile)
                }
            } catch (e: Exception) {
                // 如果反射失败（通常发生在 AGP 大版本更新后），打印错误并回退到手动配置提醒
                println("ScreenAdaptationPlugin: 无法自动映射生成的资源目录。")
                e.printStackTrace()
            }
        }

        // 监听插件加载：如果是手机 App 或 Android 库，则执行映射逻辑
        project.plugins.withId("com.android.application", object : Action<Plugin<*>> {
            override fun execute(t: Plugin<*>) { addResAction() }
        })
        project.plugins.withId("com.android.library", object : Action<Plugin<*>> {
            override fun execute(t: Plugin<*>) { addResAction() }
        })
    }
}

/**
 * 实际执行资源生成的 Task 类
 */
abstract class GenerateDimensTask : DefaultTask() {

    @get:Input abstract val designWidthPx: Property<Int>
    @get:Input abstract val designHeightPx: Property<Int>
    @get:Input abstract val designDpi: Property<Int>
    @get:Input abstract val targetScreens: ListProperty<TargetScreen>
    @get:Input abstract val baseOn: Property<String>
    @get:Input @get:Optional abstract val maxDp: Property<Int>
    @get:Input abstract val dpNameFormat: Property<String>
    @get:Input abstract val spNameFormat: Property<String>
    @get:Input abstract val autoTargetDevice: Property<Boolean>

    @get:InputFile
    @get:Optional
    abstract val targetConfigFile: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:OutputDirectory abstract val srcResDir: DirectoryProperty

    /**
     * 辅助转换方法：将像素 (Px) 转换为 Android 标准的独立像素 (Dp)
     * 公式：dp = px / (dpi / 160)
     */
    private fun pxToDp(px: Int, dpi: Int): Float = px / (dpi / 160f)

    @TaskAction
    fun generate() {
        println("\n" + "=".repeat(60))
        println("       SCREEN ADAPTATION PLUGIN - GENERATION START")
        println("=".repeat(60))

        val dWidthPx = designWidthPx.get()
        val dHeightPx = designHeightPx.get()
        val dDpi = designDpi.get()
        
        // 将设计图尺寸基准转换为 Dp
        val dWidthDp = pxToDp(dWidthPx, dDpi)
        val dHeightDp = pxToDp(dHeightPx, dDpi)
        
        val isBaseOnWidth = baseOn.get().lowercase() == "width"
        // 自动计算输出维度上限（默认取设计图的最长边）
        val maxD = if (maxDp.getOrElse(0) > 0) maxDp.get() else maxOf(dWidthPx, dHeightPx)
        val dpFormat = dpNameFormat.get()
        val spFormat = spNameFormat.get()
        
        // 汇总最终需要生成的屏幕配置列表
        val finalTargets = targetScreens.get().toMutableList()
        val detectedMsgs = mutableListOf<String>()
        
        // 0. 读取外部配置文件 (CSV) 逻辑
        if (targetConfigFile.isPresent) {
            val file = targetConfigFile.get().asFile
            if (file.exists()) {
                var loadedCount = 0
                file.readLines().forEach { line ->
                    if (line.isNotBlank() && !line.trim().startsWith("#")) {
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            try {
                                val w = parts[0].trim().toInt()
                                val h = parts[1].trim().toInt()
                                val dpi = parts[2].trim().toInt()
                                
                                // 去重判断
                                val exists = finalTargets.any { it.widthPx == w && it.heightPx == h && it.dpi == dpi }
                                if (!exists) {
                                    finalTargets.add(TargetScreenImpl(w, h, dpi))
                                    loadedCount++
                                }
                            } catch (e: Exception) {
                                println("ScreenAdaptationPlugin: Failed to parse line in config file [${line.trim()}]. Please ensure format is width,height,dpi")
                            }
                        }
                    }
                }
                val msg = "Loaded $loadedCount new devices from ${file.name}"
                println(">>> $msg")
                detectedMsgs.add(msg)
            } else {
                val warnMsg = "Config file specified but not found: ${file.absolutePath}"
                println(">>> $warnMsg")
                detectedMsgs.add(warnMsg)
            }
        }
        
        // 1. ADB 自动探测逻辑
        if (autoTargetDevice.get()) {
            try {
                // 调用 ADB 命令获取当前连接的所有开发机信息
                val detectedList = detectAllDeviceMetrics()
                if (detectedList.isEmpty()) {
                    val warn = "Warning: autoTargetDevice is enabled but NO device was detected via ADB."
                    println(">>> $warn")
                    detectedMsgs.add(warn)
                } else {
                    detectedList.forEach { detected ->
                        // 判定此设备是用户手动填写的还是 ADB 额外发现的
                        val isManual = targetScreens.get().any { it.widthPx == detected.widthPx && it.heightPx == detected.heightPx && it.dpi == detected.dpi }
                        val prefix = if (isManual) "Matched manual config" else "New device auto-added"
                        val msg = "[$prefix] ADB device: ${detected.widthPx}x${detected.heightPx} @ ${detected.dpi}dpi"
                        println(">>> $msg")
                        
                        if (!isManual) {
                            finalTargets.add(detected)
                        }
                        detectedMsgs.add(msg)
                    }
                }
            } catch (e: Exception) {
                println("ScreenAdaptationPlugin: Error during ADB detection: ${e.message}")
            }
        }

        // 清空并重建 build 生成目录，确保没有旧文件的干扰
        val buildResDir = outputDir.get().asFile
        if (buildResDir.exists()) {
            buildResDir.deleteRecursively()
        }
        buildResDir.mkdirs()

        // 2. 生成基准资源文件 (src/main/res/values/dimens.xml)
        // 这些资源比例为 1:1，是 IDE 预览和代码补全的基础
        val srcRoot = srcResDir.get().asFile
        generateDimensFile(srcRoot, "values", 1f, maxD, dpFormat, spFormat)
        
        // 3. 循环生成各个目标屏幕的适配文件夹 (如 values-sw600dp)
        if (finalTargets.isEmpty()) {
            println("No target screens provided for screen adaptation.")
        }
        
        finalTargets.forEach { screen ->
            val dIsLandscape = dWidthPx > dHeightPx
            val tIsLandscape = screen.widthPx > screen.heightPx
            
            // 自动对齐屏幕方向：如果设计图是横屏但设备报告的是竖屏，则在计算缩放比时逻辑上“旋转”它
            val (tW, tH) = if (dIsLandscape != tIsLandscape) {
                screen.heightPx to screen.widthPx
            } else {
                screen.widthPx to screen.heightPx
            }

            val tWidthDp = pxToDp(tW, screen.dpi)
            val tHeightDp = pxToDp(tH, screen.dpi)
            
            // 计算缩放因子：适配屏幕的宽度 / 设计图的宽度
            val scale = if (isBaseOnWidth) {
                tWidthDp / dWidthDp
            } else {
                tHeightDp / dHeightDp
            }
            
            // 确定最小宽度限定符 swXdp (始终取宽高中的最小值)
            val swDp = pxToDp(minOf(screen.widthPx, screen.heightPx), screen.dpi).toInt()
            generateDimensFile(buildResDir, "values-sw${swDp}dp", scale, maxD, dpFormat, spFormat)
        }
        
        // 打印最终汇总信息
        println("=".repeat(60))
        println("       SCREEN ADAPTATION PLUGIN - COMPLETED")
        detectedMsgs.forEach { println("       $it") }
        println("=".repeat(60) + "\n")
    }

    /**
     * 通过 ADB 命令探测所有在线设备的屏幕参数
     */
    private fun detectAllDeviceMetrics(): List<TargetScreen> {
        val devicesOutput = runAdb("devices") ?: return emptyList()
        // 解析 adb devices 列表，过滤出序列号
        val serials = devicesOutput.lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.endsWith("device") }
            .map { it.substringBefore("\t") }
            .toList()

        val results = mutableListOf<TargetScreen>()
        serials.forEach { serial ->
            // 获取屏幕像素尺寸 (wm size)
            val sizeOutput = runAdbForDevice(serial, "shell wm size") ?: return@forEach
            // 获取屏幕密度 (wm density)
            val densityOutput = runAdbForDevice(serial, "shell wm density") ?: return@forEach

            val sizeMatch = Regex("(\\d+)x(\\d+)").find(sizeOutput) ?: return@forEach
            val (w, h) = sizeMatch.destructured
            
            val densityMatch = Regex("(\\d+)").find(densityOutput) ?: return@forEach
            val (dpi) = densityMatch.destructured
            
            val screen = TargetScreenImpl(w.toInt(), h.toInt(), dpi.toInt())
            results.add(screen)
            
            // 顺便往主机的 Logcat 里也打一条日志，方便在 Studio 里确认探测成功
            val isManual = targetScreens.get().any { it.widthPx == w.toInt() && it.heightPx == h.toInt() && it.dpi == dpi.toInt() }
            val prefix = if (isManual) "Matched manual config" else "New device auto-added"
            runAdbForDevice(serial, "shell log -t ScreenAdaptationPlugin \"[$prefix] ADB device: ${w}x${h} @ ${dpi}dpi\"")
        }
        return results
    }

    /**
     * 针对指定设备序号运行 ADB 命令的封装
     */
    private fun runAdbForDevice(serial: String, cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("adb -s $serial $cmd")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 运行通用 ADB 命令的封装
     */
    private fun runAdb(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("adb $cmd")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 内部方法：按照 XML 格式将维度信息写入指定目录
     * @param resDir 根资源目录
     * @param dirName 文件夹名（如 values 或 values-sw600dp）
     * @param scale 比例缩放因子
     * @param maxD 生成维度的最大值
     * @param dpFormat 命名的格式字符串
     */
    private fun generateDimensFile(resDir: File, dirName: String, scale: Float, maxD: Int, dpFormat: String, spFormat: String) {
        val valuesDir = File(resDir, dirName)
        if (!valuesDir.exists()) {
            valuesDir.mkdirs()
        }
        
        val dimensFile = File(valuesDir, "dimens.xml")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<resources>\n")
        
        val dpPlaceholder = "\$i" // 处理转义逻辑
        
        // 生成每一行尺寸项
        for (i in 1..maxD) {
            val scaledDp = i * scale
            // 保留两位小数
            val formattedDp = String.format(Locale.US, "%.2f", scaledDp)
            val name = dpFormat.replace("{i}", i.toString()).replace(dpPlaceholder, i.toString())
            sb.append("    <dimen name=\"$name\">${formattedDp}dp</dimen>\n")
        }
        // 生成每一行字体项
        for (i in 1..maxD) {
            val scaledSp = i * scale
            val formattedSp = String.format(Locale.US, "%.2f", scaledSp)
            val name = spFormat.replace("{i}", i.toString()).replace(dpPlaceholder, i.toString())
            sb.append("    <dimen name=\"$name\">${formattedSp}sp</dimen>\n")
        }
        
        sb.append("</resources>\n")
        // 覆盖写入物理文件
        dimensFile.writeText(sb.toString())
        println("Generated dimens for $dirName in ${dimensFile.absolutePath}")
    }
}
