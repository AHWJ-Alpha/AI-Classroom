package com.aiclassroom.feature.exam.tool.config

/**
 * 配置工具的 parameters JSON Schema（OpenAI function calling 格式）。
 * 五个工具都没有「写 API Key」的入口，这一点在 Schema 层就固定住了。
 */
internal object ConfigToolSchemas {
    const val GET: String = """
{
  "type": "object",
  "properties": {},
  "additionalProperties": false
}
"""

    const val SET_MODEL: String = """
{
  "type": "object",
  "properties": {
    "model": {
      "type": "string",
      "description": "要切换到的模型名，例如 gpt-4o-mini / deepseek-chat。不能为空，不能包含换行。"
    },
    "modelChain": {
      "type": "array",
      "items": { "type": "string" },
      "description": "可选：新的模型优先级链（一行一个，前面的失败后自动尝试后面的）。省略时把 model 插到现有链的最前面，保留原有兜底顺序。"
    }
  },
  "required": ["model"],
  "additionalProperties": false
}
"""

    const val SET_BASE_URL: String = """
{
  "type": "object",
  "properties": {
    "baseUrl": {
      "type": "string",
      "description": "新的 Base URL，必须是带主机名的 http:// 或 https:// 地址，例如 https://api.openai.com/v1。非法地址会被拒绝。"
    }
  },
  "required": ["baseUrl"],
  "additionalProperties": false
}
"""

    const val SET_TEMPERATURE: String = """
{
  "type": "object",
  "properties": {
    "temperature": {
      "type": "number",
      "minimum": 0.0,
      "maximum": 2.0,
      "description": "采样温度，取值范围 0.0–2.0：越小越稳定，越大越发散。超范围会被拒绝。"
    }
  },
  "required": ["temperature"],
  "additionalProperties": false
}
"""

    const val SET_PROVIDER: String = """
{
  "type": "object",
  "properties": {
    "provider": {
      "type": "string",
      "description": "provider 名称，例如 OpenAI / DeepSeek / 通义千问 / 自定义。只改名称标签，不改 BaseURL（改 BaseURL 请用 config_set_base_url）。"
    }
  },
  "required": ["provider"],
  "additionalProperties": false
}
"""
}
