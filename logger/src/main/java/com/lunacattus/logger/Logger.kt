package com.lunacattus.logger

import android.util.Log
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LunaApp 统一日志管理工具
 * 支持高并发、超长日志自动分片，具备完善的 Java/Kotlin 混编互操作性。
 */
@Keep
object Logger {
    private var baseTag: String = "LunaLogger"
    private var showThreadAndTime = false

    private val dateFormatLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
    }

    private const val MAX_LOG_CHUNK_SIZE = 3500

    /**
     * 初始化全局 Base TAG 和线程元数据显示开关
     *
     * @param tag 全局日志的主 TAG 标识
     * @param showThreadAndTime 是否在日志内容中附带时间戳和线程名，默认为 false
     */
    @JvmStatic
    @JvmOverloads
    fun initBaseTag(tag: String, showThreadAndTime: Boolean = false) {
        baseTag = tag
        this.showThreadAndTime = showThreadAndTime
    }

    /**
     * 打印 DEBUG 级别日志
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun d(tag: String = "", message: String) = log(message, LogLevel.DEBUG, tag)

    /**
     * 打印 INFO 级别日志
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun i(tag: String = "", message: String) = log(message, LogLevel.INFO, tag)

    /**
     * 打印 WARN 级别日志
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun w(tag: String = "", message: String) = log(message, LogLevel.WARN, tag)

    /**
     * 打印 ERROR 级别日志
     *
     * @param tag 子模块或类别的局部 TAG（可选）
     * @param message 日志文本内容
     */
    @JvmStatic
    @JvmOverloads
    fun e(tag: String = "", message: String) = log(message, LogLevel.ERROR, tag)

    /**
     * 以封闭边框（Box）样式打印格式化日志，常用于协议报文、复杂数据结构的排版调试
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
        log(boxed, LogLevel.INFO, tag)
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
        val timestamp = dateFormatLocal.get()?.format(Date()) ?: ""
        val threadName = Thread.currentThread().name
        val logcatTag = if (subTag.isNotEmpty()) "$baseTag.$subTag" else baseTag
        val prefix = if (showThreadAndTime) "[$timestamp] [$threadName] " else "[$timestamp] "
        val fullContent = prefix + message

        if (fullContent.length > MAX_LOG_CHUNK_SIZE) {
            var i = 0
            while (i < fullContent.length) {
                val end = minOf(fullContent.length, i + MAX_LOG_CHUNK_SIZE)
                val chunk = fullContent.substring(i, end)
                printToLogcat(level, logcatTag, chunk)
                i += MAX_LOG_CHUNK_SIZE
            }
        } else {
            printToLogcat(level, logcatTag, fullContent)
        }
    }

    private fun printToLogcat(level: LogLevel, tag: String, msg: String) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, msg)
            LogLevel.DEBUG -> Log.d(tag, msg)
            LogLevel.INFO -> Log.i(tag, msg)
            LogLevel.WARN -> Log.w(tag, msg)
            LogLevel.ERROR -> Log.e(tag, msg)
        }
    }

    private enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}