package com.lunacattus.network.ws

/**
 * WebSocket 连接状态。
 *
 * - [Idle]：初始空闲状态
 * - [Connecting]：正在建立连接
 * - [Connected]：已成功连接
 * - [Reconnecting]：正在重连（携带重连次数和延迟）
 * - [Disconnected]：已断开（携带状态码和原因）
 * - [Failed]：连接失败（携带异常）
 */
sealed interface WebSocketState {
    /** 初始空闲状态。 */
    data object Idle : WebSocketState
    /** 正在建立连接。 */
    data object Connecting : WebSocketState
    /** 已成功连接。 */
    data object Connected : WebSocketState
    /** 正在重连。 */
    data class Reconnecting(val attempt: Int, val delayMs: Long) : WebSocketState
    /** 已断开连接。 */
    data class Disconnected(val code: Int, val reason: String) : WebSocketState
    /** 连接失败。 */
    data class Failed(val throwable: Throwable) : WebSocketState
}
