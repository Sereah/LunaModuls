package com.lunacattus.network.http

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端默认实现，基于 OkHttp。
 *
 * 使用示例：
 * ```
 * val http = HttpManager()
 * http.post("https://api.example.com/data", """{"key":"value"}""")
 * ```
 */
class HttpManager : IHttpClient {

    @Volatile
    private var config: HttpConfig = HttpConfig()

    private val client by lazy {
        buildClient()
    }

    /**
     * 更新 HTTP 客户端配置（连接/读取/写入超时）。
     * 配置将在下次创建新连接时生效。
     *
     * @param newConfig 新的配置参数
     */
    fun updateConfig(newConfig: HttpConfig) {
        config = newConfig
    }

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body.string()
            if (response.isSuccessful) {
                Result.success(responseBody)
            } else {
                Result.failure(
                    HttpException(response.code, responseBody)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP POST failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url).get()
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body.string()
            if (response.isSuccessful) {
                Result.success(responseBody)
            } else {
                Result.failure(
                    HttpException(response.code, responseBody)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP GET failed: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "HttpManager"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * HTTP 请求异常，包含响应状态码和错误信息。
 *
 * @param code HTTP 状态码
 * @param message 错误信息字符串
 */
class HttpException(code: Int, message: String) : Exception("HTTP $code: $message")
