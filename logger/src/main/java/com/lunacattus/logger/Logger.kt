package com.lunacattus.logger

import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Keep
object Logger {
    private var baseTag: String = "LunaLogger"
    private var showThreadName = false
    private var isDebug = true

    private var logFileDir: File? = null
    private var currentLogFile: File? = null
    private val logExecutor = Executors.newSingleThreadExecutor()
    private val logTimeFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        )
    }

    private const val MAX_LOG_CHUNK_SIZE = 2000

    private var maxFileSizeByte = 10L * 1024 * 1024
    private var maxDirSizeByte = 50L * 1024 * 1024

    private val fileDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    /**
     * 初始化全局 Base TAG、线程名显示开关以及 Debug 环境开关
     *
     * @param tag 全局日志的主 TAG 标识
     * @param showThreadName 是否在日志内容中附带线程名，默认为 false
     * @param isDebug 是否为测试环境，若为 false 则关闭所有 Logcat 输出，默认为 true
     */
    @JvmStatic
    @JvmOverloads
    fun initBaseTag(tag: String, showThreadName: Boolean = false, isDebug: Boolean = true) {
        baseTag = tag
        this.showThreadName = showThreadName
        this.isDebug = isDebug
    }

    /**
     * 初始化本地日志文件保存路径并配置容量限制，调用后将自动开启本地保存功能
     *
     * @param dir 本地日志存储的目录文件对象
     * @param maxFileSizeMb 单个日志文件的最大体积限制，单位为 MB，默认为 10
     * @param maxDirSizeMb 日志总目录的最大配额限制，单位为 MB，默认为 50
     */
    @JvmStatic
    @JvmOverloads
    fun initFileLogger(dir: File, maxFileSizeMb: Int = 10, maxDirSizeMb: Int = 50) {
        if (!dir.exists()) dir.mkdirs()
        logFileDir = dir

        maxFileSizeByte = maxFileSizeMb.toLong() * 1024 * 1024
        maxDirSizeByte = maxDirSizeMb.toLong() * 1024 * 1024

        logExecutor.execute {
            checkAndCleanExpiredFiles()
            prepareLogFile()
        }
    }

    /**
     * 打印 DEBUG 级别日志，未指定 tag 时自动获取调用处类名
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun d(tag: String = "", message: String) {
        val finalTag = tag.ifEmpty { getThrowableClassName() }
        log(message, LogLevel.DEBUG, finalTag)
    }

    /**
     * 打印 INFO 级别日志，未指定 tag 时自动获取调用处类名
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun i(tag: String = "", message: String) {
        val finalTag = tag.ifEmpty { getThrowableClassName() }
        log(message, LogLevel.INFO, finalTag)
    }

    /**
     * 打印 WARN 级别日志，未指定 tag 时自动获取调用处类名
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun w(tag: String = "", message: String) {
        val finalTag = tag.ifEmpty { getThrowableClassName() }
        log(message, LogLevel.WARN, finalTag)
    }

    /**
     * 打印 ERROR 级别日志，支持传入异常堆栈，未指定 tag 时自动获取调用处类名
     *
     * @param tag 子模块或类别的局部 TAG（可选']）
     * @param message 日志文本内容
     * @param throwable 异常对象（可选）
     */
    @JvmStatic
    @JvmOverloads
    fun e(tag: String = "", message: String, throwable: Throwable? = null) {
        val finalTag = tag.ifEmpty { getThrowableClassName() }
        val exceptionMessage = throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
        log(message + exceptionMessage, LogLevel.ERROR, finalTag)
    }

    /**
     * 以封闭边框（Box）样式打印格式化日志，内部支持长日志按行分片切分
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     * @param borderChar 构成边框的字符，默认为 '='
     */
    @JvmStatic
    @JvmOverloads
    fun box(tag: String = "", message: String, borderChar: Char = '=') {
        val lines = message.split("\n")
        val padding = 2
        val maxLen = lines.maxOfOrNull { it.length } ?: 0
        val contentWidth = maxLen + padding * 2
        val border = borderChar.toString().repeat(contentWidth + 4)

        val boxed = buildString {
            appendLine()
            appendLine(border)
            appendLine(" ".repeat(contentWidth + 2))
            for (line in lines) {
                val padded = " ".repeat(padding) + line.padEnd(maxLen) + " ".repeat(padding)
                appendLine("  $padded  ")
            }
            appendLine(" ".repeat(contentWidth + 2))
            append(border)
        }
        val finalTag = tag.ifEmpty { getThrowableClassName() }
        log(boxed, LogLevel.INFO, finalTag)
    }

    /**
     * 将 Byte 数组的指定区域数据转换为十六进制（Hex）字符串
     *
     * @param bytes 原始字节数组
     * @param offset 起始偏移量（包含）
     * @param limit 结束索引边界（不包含）
     * @return 格式化后的十六进制字符串（例如: "0A 1F FF"）
     */
    @JvmStatic
    fun getArray(bytes: ByteArray, offset: Int, limit: Int): String {
        if (offset < 0 || limit > bytes.size || offset >= limit) return ""
        return bytes.slice(offset until limit).joinToString(" ") { String.format("%02X", it) }
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO, subTag: String) {
        val logcatTag = if (subTag.isNotEmpty()) "$baseTag.$subTag" else baseTag
        val threadName = Thread.currentThread().name
        val prefix = if (showThreadName) "[$threadName] " else ""

        val lines = message.split("\n")
        for (line in lines) {
            val fullLine = prefix + line
            if (fullLine.length > MAX_LOG_CHUNK_SIZE) {
                var i = 0
                while (i < fullLine.length) {
                    val end = minOf(fullLine.length, i + MAX_LOG_CHUNK_SIZE)
                    val chunk = if (i == 0) {
                        fullLine.substring(i, end)
                    } else {
                        prefix + "-> " + fullLine.substring(i, end)
                    }
                    printToLogcat(level, logcatTag, chunk)
                    writeLogToFile(level, logcatTag, chunk)
                    i += MAX_LOG_CHUNK_SIZE
                }
            } else {
                printToLogcat(level, logcatTag, fullLine)
                writeLogToFile(level, logcatTag, fullLine)
            }
        }
    }

    private fun printToLogcat(level: LogLevel, tag: String, msg: String) {
        if (!isDebug) return
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, msg)
            LogLevel.DEBUG -> Log.d(tag, msg)
            LogLevel.INFO -> Log.i(tag, msg)
            LogLevel.WARN -> Log.w(tag, msg)
            LogLevel.ERROR -> Log.e(tag, msg)
        }
    }

    private fun writeLogToFile(level: LogLevel, tag: String, msg: String) {
        if (logFileDir == null) return
        val logTime = logTimeFormat.format(Date())
        val fileLine = "$logTime ${level.name[0]}/$tag: $msg\n"

        logExecutor.execute {
            try {
                prepareLogFile()
                currentLogFile?.let { file ->
                    FileWriter(file, true).use { writer ->
                        writer.write(fileLine)
                    }
                }
            } catch (e: IOException) {
                Log.e("Logger", "Failed to write log to file", e)
            }
        }
    }

    private fun prepareLogFile() {
        val dir = logFileDir ?: return
        val dateStr = fileDateFormat.format(Date())
        var file = File(dir, "log_$dateStr.txt")

        if (file.exists() && file.length() > maxFileSizeByte) {
            var index = 1
            while (file.exists() && file.length() > maxFileSizeByte) {
                file = File(dir, "log_${dateStr}_$index.txt")
                index++
            }
        }

        if (currentLogFile?.absolutePath != file.absolutePath) {
            currentLogFile = file
            checkAndCleanExpiredFiles()
        }
    }

    private fun checkAndCleanExpiredFiles() {
        val dir = logFileDir ?: return
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("log_") } ?: return

        var totalSize = files.sumOf { it.length() }
        if (totalSize > maxDirSizeByte) {
            files.sortBy { it.lastModified() }
            for (file in files) {
                val fileSize = file.length()
                if (file.delete()) {
                    totalSize -= fileSize
                    if (totalSize <= maxDirSizeByte * 0.7) break
                }
            }
        }
    }

    private fun getThrowableClassName(): String {
        val stackTrace = Throwable().stackTrace
        if (stackTrace.size < 4) return ""
        val className = stackTrace[3].className
        return className.substringAfterLast(".")
    }

    private enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}