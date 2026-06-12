package com.lunacattus.network.id

import androidx.annotation.Keep
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于 ULID（Universally Unique Lexicographically Sortable Identifier）算法的请求 ID 生成器。
 *
 * 生成的 ID 格式为 "{deviceCode}_26字符ULID"，如 "DEVICE_A_01E7RZP1KZV3Y5Z6A1F2G3H4J5"。
 * ULID 结构：26 字符 Crockford Base32 编码，前 10 字符为毫秒时间戳，后 16 字符为 80 bits 真随机数。
 * 按字典序排序即按时间排序，对数据库索引友好。
 *
 * 线程安全：通过 CAS 循环保证 lastTimestamp 的原子读写，SecureRandom 本身线程安全。
 */
@Keep
class RequestIdGenerator(
    private val deviceCode: String,
) {

    companion object {
        private const val CLOCK_DRIFT_TOLERANCE_MS = 10_000L
        private val RANDOM = SecureRandom()
        private val lastTimestamp = AtomicLong(-1L)
        private val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    }

    /**
     * 生成带 deviceCode 前缀的 ULID，格式为 "{deviceCode}_26字符ULID"。
     */
    fun generate(): String = "${deviceCode}_${nextId()}"

    /**
     * 生成不带前缀的纯 ULID 字符串。
     */
    fun generateRaw(): String = nextId()

    /**
     * 从指定毫秒时间戳生成带前缀的 ULID（随机部分仍为随机）。
     *
     * @param epochMillis 毫秒级 Unix 时间戳，必须在 [0, 2^48) 范围内
     * @throws IllegalArgumentException 若时间戳超出 48 bits 范围
     */
    fun fromTimestamp(epochMillis: Long): String {
        require(epochMillis in 0L until (1L shl 48)) {
            "Timestamp $epochMillis out of 48-bit range [0, ${1L shl 48})"
        }
        return "${deviceCode}_${encodeUlid(epochMillis)}"
    }

    /**
     * 核心生成逻辑：通过 CAS 循环保证线程安全，避免递归带来的栈溢出风险。
     *
     * 若时钟回拨超过容忍阈值，抛出异常；若在容忍范围内，自旋等待后重试。
     * 取 max(now, prev) 确保时间戳单调递增。
     */
    private fun nextId(): String {
        while (true) {
            val now = System.currentTimeMillis()
            val prev = lastTimestamp.get()

            if (now < prev - CLOCK_DRIFT_TOLERANCE_MS) {
                throw IllegalStateException(
                    "Clock moved backwards by ${prev - now} ms, exceeds tolerance $CLOCK_DRIFT_TOLERANCE_MS ms"
                )
            }

            if (now < prev) {
                while (System.currentTimeMillis() < prev) Thread.yield()
                continue
            }

            val ts = maxOf(now, prev)

            if (lastTimestamp.compareAndSet(prev, ts)) {
                return encodeUlid(ts)
            }
        }
    }

    /**
     * 将时间戳和随机字节编码为 26 字符的 ULID。
     */
    private fun encodeUlid(timestamp: Long): String {
        val chars = CharArray(26)
        var remaining = timestamp
        for (i in 9 downTo 0) {
            chars[i] = ENCODING[(remaining and 0x1F).toInt()]
            remaining = remaining ushr 5
        }
        val random = ByteArray(10)
        RANDOM.nextBytes(random)
        var buffer = 0L
        var bitsInBuffer = 0
        var idx = 10
        for (byte in random) {
            buffer = (buffer shl 8) or (byte.toLong() and 0xFF)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                chars[idx++] = ENCODING[(buffer ushr bitsInBuffer).toInt() and 0x1F]
            }
        }
        return String(chars)
    }
}
