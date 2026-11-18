package com.aiclassroom.classroom.gateway

/** 一次 HTTP 请求的原料。网关只管拼内容，地址与密钥由宿主提供。 */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

/**
 * 传输结果。
 *
 * [completed] 表示连接是否**正常读完**：流式请求里它比 HTTP 状态更重要 ——
 * 200 但连接在半截断掉，正文就是不完整的。
 */
data class HttpExchange(
    val status: Int,
    val completed: Boolean,
    val retryAfterMs: Long? = null,
    val errorMessage: String? = null,
    /** 服务端返回的 model 字段（可能和请求的不一样，降级时尤其要记真实值）。 */
    val actualModel: String? = null,
)

/**
 * 传输层接口。
 *
 * 故意用「逐行回调」而不是返回整个字符串：流式响应必须边收边解析，
 * 否则长内容要等全部结束才能显示，也判断不了中途断掉。
 */
interface HttpTransport {
    suspend fun post(
        request: HttpRequest,
        onLine: (String) -> Unit,
    ): HttpExchange
}

/** 睡眠抽象：测试里注入假时钟，不真的等 30 秒。 */
fun interface Sleeper {
    suspend fun sleep(ms: Long)
}