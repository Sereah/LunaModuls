package com.lunacattus.network.http

/**
 * HTTP 客户端配置
 *
 * @property connectTimeoutMs 连接超时时间（毫秒），默认 10000ms
 * @property readTimeoutMs 读取超时时间（毫秒），默认 30000ms
 * @property writeTimeoutMs 写入超时时间（毫秒），默认 15000ms
 */
data class HttpConfig(
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 30_000L,
    val writeTimeoutMs: Long = 15_000L,
)
