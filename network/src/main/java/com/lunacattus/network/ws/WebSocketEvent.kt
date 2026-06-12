package com.lunacattus.network.ws

/**
 * WebSocket 生命周期事件。
 *
 * - [Connected]：连接成功
 * - [Disconnected]：连接断开
 * - [MessageReceived]：收到消息（文本或二进制）
 * - [Error]：发生错误
 */
sealed interface WebSocketEvent {
    /** 连接成功，携带响应头。 */
    data class Connected(val headers: Map<String, List<String>>) : WebSocketEvent
    /** 连接断开，携带状态码和原因。 */
    data class Disconnected(val code: Int, val reason: String) : WebSocketEvent
    /** 收到消息。 */
    data class MessageReceived(val message: Message) : WebSocketEvent
    /** 发生错误。 */
    data class Error(val throwable: Throwable) : WebSocketEvent

    /**
     * WebSocket 消息。
     *
     * - [Text]：文本消息
     * - [Binary]：二进制消息
     */
    sealed interface Message {
        /** 文本消息。 */
        data class Text(val data: String) : Message
        /** 二进制消息，基于 [ByteArray]，已正确实现 [equals] 和 [hashCode]。 */
        class Binary(val data: ByteArray) : Message {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as Binary
                return data.contentEquals(other.data)
            }

            override fun hashCode(): Int = data.contentHashCode()
        }
    }
}
