package com.aiclassroom.feature.exam.tool.universal

/** universal_call / universal_get_json / list_profiles 的 function calling 参数 Schema。 */
internal object UniversalToolSchemas {

    /** auth 参数在两个工具里完全一致，只写一份，免得改漏一处。 */
    private val AUTH_ARGS: String = """
        {
          "type": "object",
          "description": "认证方式；不传则不认证（给了 profile 时用 profile 里的）。",
          "properties": {
            "type": { "type": "string", "enum": ["none", "bearer", "header", "basic", "query"] },
            "header": { "type": "string", "description": "type=header 时的请求头名（如 X-Api-Key）；type=query 时的 query 参数名。" },
            "token": { "type": "string", "description": "凭据原文。bearer 不用自己加 Bearer 前缀。" },
            "prefix": { "type": "string", "description": "值前缀，例如 \"\" 或 ghost-。" }
          },
          "required": ["type"],
          "additionalProperties": false
        }
    """.trimIndent()

    val UNIVERSAL_CALL: String = """
        {
          "type": "object",
          "properties": {
            "baseUrl": {
              "type": "string",
              "description": "服务根地址，例如 http://127.0.0.1:8820。给了 profile 时可省略（用 profile 里的）。"
            },
            "profile": {
              "type": "string",
              "description": "内置或自定义 profile 名：nightmare（手机 8820 端口）/ openai-images / sd-webui。用 list_profiles 查看全部。"
            },
            "profileJson": {
              "type": "string",
              "description": "内联 profile JSON（别人分享的配置），优先级高于 profile。"
            },
            "auth": $AUTH_ARGS,
            "request": {
              "type": "object",
              "description": "请求描述。path 可以是相对路径（拼在 baseUrl 后）或完整 URL。",
              "properties": {
                "method": { "type": "string", "enum": ["POST", "GET", "PUT", "PATCH", "DELETE", "HEAD"] },
                "path": { "type": "string", "description": "例如 /run 或 /sdapi/v1/txt2img。" },
                "headers": { "type": "object", "description": "额外请求头。", "additionalProperties": { "type": "string" } },
                "bodyTemplate": {
                  "type": "string",
                  "description": "请求体模板，占位符 {{prompt}} {{negative}} {{width}} {{height}} {{steps}} {{cfg}} {{model}}，替换时做 JSON 转义。数字占位符不要加引号。"
                }
              },
              "required": ["path"],
              "additionalProperties": false
            },
            "response": {
              "type": "object",
              "description": "怎么从响应里取结果。json_base64 = 取出的值是 base64；json_url = 取出的值是图片地址（相对路径也行，会自动带同样的认证再 GET）；binary = 响应体本身就是图片；json = 直接返回 JSON 数据、不落盘（适合查询 / 控制 / 状态类接口）。",
              "properties": {
                "extract": { "type": "string", "description": "极简路径，例如 results[0].url、data[0].b64_json、b.c[1]。binary 可留空；json 留空表示返回整个响应体。" },
                "type": { "type": "string", "enum": ["json_base64", "json_url", "binary", "json"] },
                "mime": { "type": "string", "description": "可选，如 image/png；留空按字节探测。只对图片类型有意义。" }
              },
              "required": ["type"],
              "additionalProperties": false
            },
            "async": {
              "type": "object",
              "description": "异步模式：提交后从响应取结果地址，取不到就轮询 pollPath 直到 doneField 为真。提交响应已带结果地址时（nightmare）会直接下载，不轮询。",
              "properties": {
                "enabled": { "type": "boolean" },
                "resultUrlFrom": { "type": "string", "description": "从响应里取结果地址的路径，例如 results[0].url。" },
                "pollPath": { "type": "string", "description": "轮询路径，例如 /jobs/123；不需要轮询时留空。" },
                "doneField": { "type": "string", "description": "完成标志字段，例如 status 或 done；留空则结果地址出现即算完成。" },
                "timeoutSeconds": { "type": "integer", "description": "轮询超时秒数，1-300。" }
              },
              "additionalProperties": false
            },
            "prompt": { "type": "string", "description": "正向提示词，填进模板的 {{prompt}}。" },
            "negative": { "type": "string", "description": "反向提示词，填进 {{negative}}。" },
            "width": { "type": "integer", "description": "宽，填进 {{width}}。" },
            "height": { "type": "integer", "description": "高，填进 {{height}}。" },
            "steps": { "type": "integer", "description": "采样步数，填进 {{steps}}。" },
            "cfg": { "type": "number", "description": "CFG 引导强度，填进 {{cfg}}。" },
            "model": { "type": "string", "description": "模型名，填进 {{model}}。" },
            "values": {
              "type": "object",
              "description": "其它占位符的取值（自定义模板用），键值都是字符串。",
              "additionalProperties": { "type": "string" }
            },
            "timeoutSeconds": { "type": "integer", "description": "单次 HTTP 请求超时秒数，1-300，默认 120。" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    val UNIVERSAL_GET_JSON: String = """
        {
          "type": "object",
          "properties": {
            "baseUrl": {
              "type": "string",
              "description": "服务根地址，例如 http://127.0.0.1:48597。"
            },
            "path": {
              "type": "string",
              "description": "GET 路径，例如 /version 或 /configs；也可以直接写完整 URL。"
            },
            "auth": $AUTH_ARGS,
            "extract": {
              "type": "string",
              "description": "可选，只取子对象：极简路径，例如 b.c[1]、proxies.DIRECT；留空返回整个响应体。"
            },
            "timeoutSeconds": { "type": "integer", "description": "单次 HTTP 请求超时秒数，1-300，默认 120。" }
          },
          "required": ["baseUrl", "path"],
          "additionalProperties": false
        }
    """.trimIndent()

    val LIST_PROFILES: String = """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "只看某一个 profile 的完整 JSON（便于复制 / 修改 / 分享）；留空列出全部摘要。"
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()
}
