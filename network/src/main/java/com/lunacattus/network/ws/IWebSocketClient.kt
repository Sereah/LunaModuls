package com.lunacattus.network.ws

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket 客户端接口，定义连接、断开、发送等基本操作。
 */
interface IWebSocketClient {

    /** 当前连接状态，通过 [StateFlow] 观察变化。 */
    val state: StateFlow<WebSocketState>

    /** 连接事件流，通过 [SharedFlow] 订阅连接/断开/消息/错误事件。 */
    val events: SharedFlow<WebSocketEvent>

    /**
     * 连接到 WebSocket 服务器。
     *
     * @param url WebSocket 地址（ws:// 或 wss://）
     * @param headers 自定义请求头
     * @param config 连接配置，如心跳间隔、重连策略等
     * @return 连接发起成功返回 true；若已有连接则返回 false
     */
    suspend fun connect(
        url: String,
        headers: Map<String, String> = emptyMap(),
        config: WebSocketConfig = WebSocketConfig(),
    ): Boolean

    /**
     * 断开 WebSocket 连接。
     * 断开后自动取消重连任务并清理资源。
     */
    suspend fun disconnect()

    /**
     * 发送文本消息。
     *
     * @param message 文本内容
     * @return 发送成功返回 true；未连接时返回 false
     */
    suspend fun send(message: String): Boolean

    /**
     * 发送二进制消息。
     *
     * @param data 二进制数据
     * @return 发送成功返回 true；未连接时返回 false
     */
    suspend fun send(data: ByteArray): Boolean
}
