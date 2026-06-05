package com.lunacattus.common.utils

/**
 * 将文件大小（字节）转换为易读的字符串格式
 */
fun Long.toFileSizeString(): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024

    return when {
        this < kb -> "$this B"
        this < mb -> "%.2f KB".format(this / kb)
        this < gb -> "%.2f MB".format(this / mb)
        this < tb -> "%.2f GB".format(this / gb)
        else -> "%.2f TB".format(this / tb)
    }
}

fun Long.toDuration(): String {
    val ms = this % 1000
    val totalSeconds = this / 1000
    val s = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val m = totalMinutes % 60
    val h = totalMinutes / 60

    return "%02d:%02d:%02d.%03d".format(h, m, s, ms)
}

fun Long.toDurationStringShort(): String {
    return when {
        this < 1_000 -> "${this}ms"
        this < 60_000 -> "%.2fs".format(this / 1000f)
        else -> {
            val minutes = this / 60_000
            val seconds = (this % 60_000) / 1000
            "${minutes}m ${seconds}s"
        }
    }
}