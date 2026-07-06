package com.lunacattus.llm.api.generate

import kotlinx.coroutines.flow.Flow

interface IGenerateEngine {

    fun load(): Flow<Result<Unit>>

    suspend fun setSystemPrompt(prompt: String, enableThinking: Boolean = false)

    fun generate(
        prompt: String,
        maxTokens: Int = 256,
        enableThinking: Boolean = false,
    ): Flow<String>

    val isReady: Boolean

    fun close()
}