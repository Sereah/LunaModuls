package com.lunacattus.common.utils

/**
 * 长字符串中间部分隐藏
 */
fun String.maskCenter(headLen: Int = 15, tailLen: Int = 15): String {
    if (this.length <= headLen + tailLen) return this
    return "${this.take(headLen)}...${this.takeLast(tailLen)}"
}