package com.lunacattus.common

import android.util.Log

object CommonLog {

    private var debugLogger: ((tag: String, msg: String) -> Unit)? = null
    private var errorLogger: ((tag: String, msg: String, tr: Throwable?) -> Unit)? = null

    /**
     * 一键配置整个 AAR 基础库的自定义日志通道。
     *
     * @param debug Debug 级别日志回调。
     * @param error Error 级别日志回调，携带异常堆栈。
     */
    fun setLogger(
        debug: (tag: String, msg: String) -> Unit,
        error: (tag: String, msg: String, tr: Throwable?) -> Unit
    ) {
        this.debugLogger = debug
        this.errorLogger = error
    }

    fun d(tag: String, msg: String) {
        debugLogger?.invoke(tag, msg) ?: Log.d(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        errorLogger?.invoke(tag, msg, tr) ?: Log.e(tag, msg, tr)
    }
}