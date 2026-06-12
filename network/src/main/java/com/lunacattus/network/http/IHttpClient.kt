package com.lunacattus.network.http

/**
 * HTTP 客户端接口，定义 HTTP 请求的基本操作。
 */
interface IHttpClient {

    /**
     * 发送 POST 请求。
     *
     * @param url 请求地址
     * @param body 请求体 JSON 字符串
     * @param headers 请求头键值对
     * @return 成功时返回 [Result.success] 包含响应体字符串；失败时返回 [Result.failure] 包含异常
     */
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String>

    /**
     * 发送 GET 请求。
     *
     * @param url 请求地址
     * @param headers 请求头键值对
     * @return 成功时返回 [Result.success] 包含响应体字符串；失败时返回 [Result.failure] 包含异常
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String>
}
