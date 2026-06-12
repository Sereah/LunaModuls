package com.lunacattus.network.ws

/**
 * WebSocket 连接配置
 *
 * @property reconnectEnabled 是否启用自动重连，默认 true
 * @property maxReconnectAttempts 最大重连尝试次数，默认 Int.MAX_VALUE（不限制）
 * @property reconnectBaseDelayMs 重连初始延迟（毫秒），默认 1000ms
 * @property reconnectMaxDelayMs 重连最大延迟（毫秒），默认 30000ms
 * @property reconnectDelayMultiplier 重连延迟指数退避倍数，默认 2.0f（每次重连延迟翻倍）
 * @property pingIntervalMs 心跳 ping 间隔（毫秒），默认 15000ms
 * @property readTimeoutMs 读取超时（毫秒），0 表示无超时，默认 0
 * @property writeTimeoutMs 写入超时（毫秒），默认 10000ms
 * @property connectTimeoutMs 连接超时（毫秒），默认 10000ms
 */
data class WebSocketConfig(
    val reconnectEnabled: Boolean = true,
    val maxReconnectAttempts: Int = Int.MAX_VALUE,
    val reconnectBaseDelayMs: Long = 1_000L,
    val reconnectMaxDelayMs: Long = 30_000L,
    val reconnectDelayMultiplier: Float = 2.0f,
    val pingIntervalMs: Long = 15_000L,
    val readTimeoutMs: Long = 0L,
    val writeTimeoutMs: Long = 10_000L,
    val connectTimeoutMs: Long = 10_000L,
)
