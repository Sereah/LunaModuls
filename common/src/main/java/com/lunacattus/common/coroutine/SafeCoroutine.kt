package com.lunacattus.common.coroutine

import com.lunacattus.common.CommonLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object SafeCoroutine {
    private const val TAG = "SafeCoroutine"

    /**
     * 安全启动协程 (launch)，内部自动捕获非受检异常，防止单点业务崩溃导致整个应用或父作用域退出。
     *
     * @param name 协程任务别名，用于日志追踪与性能定位。
     * @param context 附加的协程上下文（如 [Dispatchers.Main]），默认不附加任何上下文。
     * @param block 协程执行体。
     * @return 返回创建的 [Job] 实例，可用于手动取消任务。
     */
    fun CoroutineScope.launchSafe(
        name: String,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler
            CommonLog.e(
                TAG,
                "Uncaught exception in Coroutine[$name]: ${throwable.message}",
                throwable
            )
        }

        return this.launch(CoroutineName(name) + context + exceptionHandler) {
            try {
                block()
            } catch (e: CancellationException) {
                CommonLog.d(TAG, "Coroutine[$name] cancelled safely.")
                throw e
            } catch (t: Throwable) {
                CommonLog.e(TAG, "Exception caught in launchSafe[$name]: ${t.message}", t)
            }
        }
    }

    /**
     * 安全启动带有返回值的异步协程 (async)，并在发生异常时自动记录。
     *
     * 注意：由于 `async` 的结构化并发特性，内部异常虽会被捕获并记录日志，但仍会向上冒泡。
     * 外部在调用 [Deferred.await] 时，必须外嵌 `try-catch` 块来处理业务侧的精确异常。
     *
     * @param name 异步任务别名，用于日志追踪与性能定位。
     * @param context 附加的协程上下文，默认不附加任何上下文。
     * @param block 异步执行体，返回泛型结果 [T]。
     * @return 返回携带执行结果的 [Deferred] 实例。
     */
    fun <T> CoroutineScope.asyncSafe(
        name: String,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler
            CommonLog.e(TAG, "Uncaught exception in Async[$name]: ${throwable.message}", throwable)
        }

        return this.async(CoroutineName(name) + context + exceptionHandler) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                CommonLog.e(TAG, "Exception caught in asyncSafe[$name]: ${t.message}", t)
                throw t
            }
        }
    }

    /**
     * 安全取消当前协程作用域及其所有子协程。
     *
     * 内部自动校验当前作用域的激活状态（Active），避免对已关闭的作用域重复取消。
     */
    fun CoroutineScope.cancelSafe() {
        val name = this.coroutineContext[CoroutineName]?.name ?: "Unknown"
        if (this.isActive) {
            this.cancel()
            CommonLog.d(TAG, "Scope[$name] and its children cancelled.")
        }
    }
}