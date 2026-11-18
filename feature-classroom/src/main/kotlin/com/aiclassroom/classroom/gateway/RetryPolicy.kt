package com.aiclassroom.classroom.gateway

/**
 * 重试策略。
 *
 * 只回答两个问题：该不该重试、等多久。发请求的地方照它执行。
 * 关键约定：401 不循环重试；429 尊重服务端 Retry-After；取消永不重试。
 */
object RetryPolicy {

    const val DEFAULT_MAX_ATTEMPTS = 3
    private const val BASE_DELAY_MS = 500L
    private const val MAX_DELAY_MS = 30_000L

    data class Decision(val retry: Boolean, val delayMs: Long, val reason: String) {
        companion object {
            fun stop(reason: String) = Decision(false, 0, reason)
        }
    }

    /**
     * @param attempt 已经失败了几次（从 1 开始）。
     * @param serverRetryAfterMs 服务端 Retry-After 换算成的毫秒，没有则 null。
     */
    fun decide(
        error: ModelCallError?,
        attempt: Int,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        serverRetryAfterMs: Long? = null,
    ): Decision {
        if (error == null) return Decision.stop("没有错误")
        if (attempt >= maxAttempts) return Decision.stop("已达最大重试次数 " + maxAttempts)

        return when (error) {
            is ModelCallError.Unauthorized -> Decision.stop("认证失败，重试无效")
            is ModelCallError.Refused -> Decision.stop("模型明确拒绝")
            is ModelCallError.BadPayload -> Decision.stop("响应格式错误，重试大概率一样")
            ModelCallError.Cancelled -> Decision.stop("用户取消")

            is ModelCallError.RateLimited -> {
                val wait = serverRetryAfterMs?.coerceIn(0, MAX_DELAY_MS) ?: backoff(attempt)
                Decision(true, wait, "限流，等待 " + wait + "ms")
            }
            is ModelCallError.ServerError -> Decision(true, backoff(attempt), "服务端 " + error.status)
            is ModelCallError.Network -> Decision(true, backoff(attempt), "网络错误")
            ModelCallError.Timeout -> Decision(true, backoff(attempt), "超时")
            is ModelCallError.Truncated -> Decision(true, backoff(attempt), "响应被截断")
        }
    }

    /** 指数退避：500、1000、2000……上限 30 秒。 */
    fun backoff(attempt: Int): Long {
        val exp = BASE_DELAY_MS shl (attempt - 1).coerceIn(0, 10)
        return exp.coerceAtMost(MAX_DELAY_MS)
    }
}