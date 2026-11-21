package com.aiclassroom.classroom.gateway

import com.aiclassroom.classroom.json.Json
import com.aiclassroom.classroom.json.JsonWriter

/**
 * 把 [ModelRequest] 编成 chat/completions 的请求体。
 *
 * 参数按能力位裁剪：不支持 temperature 的供应商就不发这个字段，
 * max_tokens 与 max_completion_tokens 也按供应商选 —— 一律强加参数会让部分服务直接 400。
 */
object ChatRequestJson {

    fun build(request: ModelRequest): String {
        val fields = linkedMapOf<String, Json>()
        fields["model"] = Json.of(request.model)
        fields["messages"] = Json.arr(
            (if (request.expectJson && request.capabilities.supportsJsonMode &&
                request.messages.none { "json" in it.content }) {
                listOf(ModelMessage(ModelMessage.Role.SYSTEM, "Return a valid json object.")) + request.messages
            } else request.messages).map { m ->
                Json.obj(
                    "role" to Json.of(m.role.name.lowercase()),
                    "content" to Json.of(m.content),
                )
            },
        )
        fields["stream"] = Json.of(request.stream)

        if (request.temperature != null && request.capabilities.supportsTemperature) {
            fields["temperature"] = Json.of(request.temperature)
        }
        if (request.reasoningEffort != null && request.capabilities.supportsReasoningEffort) {
            fields["reasoning_effort"] = Json.of(request.reasoningEffort)
        }
        if (request.maxOutputTokens != null) {
            when (request.capabilities.maxTokensField) {
                ModelCapabilities.MaxTokensField.MAX_TOKENS ->
                    fields["max_tokens"] = Json.of(request.maxOutputTokens)
                ModelCapabilities.MaxTokensField.MAX_COMPLETION_TOKENS ->
                    fields["max_completion_tokens"] = Json.of(request.maxOutputTokens)
                ModelCapabilities.MaxTokensField.NONE -> Unit
            }
        }
        if (request.expectJson && request.capabilities.supportsJsonMode) {
            fields["response_format"] = Json.obj("type" to Json.of("json_object"))
        }
        return JsonWriter.write(Json.Obj(fields))
    }

    /** 拼 endpoint：base 末尾的斜杠先去掉，避免出现双斜杠。 */
    fun endpoint(baseUrl: String, path: String = "/chat/completions"): String =
        baseUrl.trimEnd('/') + path
}
