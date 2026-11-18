package com.aiclassroom.classroom.gateway

/**
 * 把「发请求」和「怎么应对失败」拼起来的网关实现。
 *
 * 它不关心 HTTP 细节（那是 [HttpTransport] 的事），只管：
 * - 每次尝试都用一个**全新的**解析器，绝不复用上一次的残留内容；
 * - 失败按 [RetryPolicy] 决定重试与等待；
 * - 重试用尽后按 [ModelRequest.fallbackModels] 降级，并且**重新开始计算尝试次数**；
 * - 取消直接抛出去，不吞成普通失败再换模型。
 */
class HttpModelGateway(
    private val transport: HttpTransport,
    private val sleeper: Sleeper,
    private val endpointOf: (model: String) -> String,
    private val headersOf: () -> Map<String, String>,
    private val bodyBuilder: (ModelRequest) -> String,
    private val maxAttemptsPerModel: Int = RetryPolicy.DEFAULT_MAX_ATTEMPTS,
) : ModelGateway {

    override suspend fun complete(request: ModelRequest): ModelCallResult {
        val models = listOf(request.model) + request.fallbackModels
        var last: ModelCallResult? = null

        for (model in models) {
            val single = if (model == request.model) request else request.copy(model = model)
            last = attemptWithRetry(single)
            // 只有「换一个模型可能好」的失败才降级；认证失败、取消都不该换模型。
            if (last.error == null || !shouldFallback(last.error!!)) return last
        }
        return last ?: ModelCallResult(
            content = "", actualModel = null, finishReason = null, transportCompleted = false,
            structuredPayload = null, usage = null,
            error = ModelCallError.BadPayload("没有可用的模型"), diagnosticId = null,
        )
    }

    private suspend fun attemptWithRetry(request: ModelRequest): ModelCallResult {
        var attempt = 0
        while (true) {
            attempt++
            val interpreter = SseInterpreter()
            var exchange: HttpExchange
            try {
                exchange = transport.post(
                    HttpRequest(
                        url = endpointOf(request.model),
                        headers = headersOf(),
                        body = bodyBuilder(request),
                    ),
                ) { line -> interpreter.feedLine(line) }
            } catch (c: kotlinx.coroutines.CancellationException) {
                // 取消不是失败：原样抛出，绝不换模型继续跑。
                throw c
            } catch (t: Throwable) {
                val err = ModelCallError.Network(t.message ?: t::class.simpleName ?: "网络错误")
                val decision = RetryPolicy.decide(err, attempt, maxAttemptsPerModel)
                if (!decision.retry) return failure(err, attempt)
                sleeper.sleep(decision.delayMs)
                continue
            }

            val upstream = classify(exchange)
            val result = if (upstream != null) {
                failure(upstream, attempt).copy(actualModel = exchange.actualModel)
            } else {
                interpreter.finish(
                    transportCompleted = exchange.completed,
                    structuredPayload = if (request.expectJson) null else null,
                ).copy(actualModel = exchange.actualModel)
            }

            val err = result.error
            if (err == null) return result

            val decision = RetryPolicy.decide(err, attempt, maxAttemptsPerModel, exchange.retryAfterMs)
            if (!decision.retry) return result
            sleeper.sleep(decision.delayMs)
        }
    }

    private fun classify(exchange: HttpExchange): ModelCallError? = when {
        exchange.status == 401 || exchange.status == 403 -> ModelCallError.Unauthorized(exchange.status)
        exchange.status == 429 -> ModelCallError.RateLimited(exchange.status, exchange.retryAfterMs)
        exchange.status in 500..599 -> ModelCallError.ServerError(exchange.status)
        exchange.status in 400..499 -> ModelCallError.BadPayload(
            "HTTP " + exchange.status + (exchange.errorMessage?.let { "：" + it } ?: ""),
        )
        exchange.errorMessage != null -> ModelCallError.Network(exchange.errorMessage)
        else -> null
    }

    private fun failure(error: ModelCallError, attempt: Int) = ModelCallResult(
        content = "",
        actualModel = null,
        finishReason = null,
        transportCompleted = false,
        structuredPayload = null,
        usage = null,
        error = error,
        diagnosticId = "attempt-" + attempt,
    )

    /** 认证失败、拒绝、取消、格式错误 — 换模型也救不回来。 */
    private fun shouldFallback(error: ModelCallError): Boolean = when (error) {
        is ModelCallError.Unauthorized -> false
        is ModelCallError.Refused -> false
        is ModelCallError.BadPayload -> false
        ModelCallError.Cancelled -> false
        else -> true
    }
}