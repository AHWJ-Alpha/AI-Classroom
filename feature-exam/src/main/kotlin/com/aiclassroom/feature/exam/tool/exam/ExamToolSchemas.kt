package com.aiclassroom.feature.exam.tool.exam

/**
 * 各工具的 parameters JSON Schema（OpenAI function calling 格式）。
 * 题号一律 integer 且从 1 开始，与考生看到的「第 N 题」一致。
 */
internal object ExamToolSchemas {
    const val CREATE: String = """
{
  "type": "object",
  "properties": {
    "title": {
      "type": "string",
      "description": "考试标题，例如「AI 随堂考试」。"
    },
    "questions": {
      "type": "array",
      "minItems": 1,
      "description": "题目列表，按数组顺序依次作为第 1 题、第 2 题……至少 1 题。",
      "items": {
        "type": "object",
        "properties": {
          "premise": {
            "type": "string",
            "description": "题干前提：题目背景、阅读材料、数据或作答说明。"
          },
          "question": {
            "type": "string",
            "description": "具体问题。"
          },
          "referenceAnswer": {
            "type": "string",
            "description": "参考解析 / 标准答案（可选，省略即空字符串）。只会显示在题卡的「查看解析」区域，不会填入考生作答框；考生作答由考生自己填写。"
          }
        },
        "required": ["premise", "question"],
        "additionalProperties": false
      }
    }
  },
  "required": ["title", "questions"],
  "additionalProperties": false
}
"""

    const val GET: String = """
{
  "type": "object",
  "properties": {},
  "additionalProperties": false
}
"""

    const val ADD_QUESTION: String = """
{
  "type": "object",
  "properties": {
    "index": {
      "type": "integer",
      "description": "插入位置，从 1 开始（即插入后成为第 index 题）：1 = 插到最前面，当前题数 + 1 = 追加到末尾。省略则追加到末尾。"
    },
    "premise": {
      "type": "string",
      "description": "题干前提：题目背景、阅读材料、数据或作答说明。"
    },
    "question": {
      "type": "string",
      "description": "具体问题。"
    },
    "referenceAnswer": {
      "type": "string",
      "description": "参考解析 / 标准答案（可选）。只会显示在题卡的「查看解析」区域，不会填入考生作答框；考生作答由考生自己填写。"
    }
  },
  "required": ["premise", "question"],
  "additionalProperties": false
}
"""

    const val EDIT_QUESTION: String = """
{
  "type": "object",
  "properties": {
    "index": {
      "type": "integer",
      "description": "要修改的题号，从 1 开始（第 1 题 = 1）。超出 1..当前题数 会返回错误。"
    },
    "premise": {
      "type": "string",
      "description": "新的题干前提；不传则保持原值。"
    },
    "question": {
      "type": "string",
      "description": "新的问题；不传则保持原值。"
    },
    "referenceAnswer": {
      "type": "string",
      "description": "新的参考解析 / 标准答案；不传则保持原值，传空字符串表示清空解析。只影响解析区，不会改动考生作答框里的内容。"
    }
  },
  "required": ["index"],
  "additionalProperties": false
}
"""

    const val DELETE_QUESTION: String = """
{
  "type": "object",
  "properties": {
    "index": {
      "type": "integer",
      "description": "要删除的题号，从 1 开始（第 1 题 = 1）。超出 1..当前题数 会返回错误。"
    }
  },
  "required": ["index"],
  "additionalProperties": false
}
"""

    const val SET_TITLE: String = """
{
  "type": "object",
  "properties": {
    "title": {
      "type": "string",
      "description": "新的考试标题，不能为空。"
    }
  },
  "required": ["title"],
  "additionalProperties": false
}
"""

    const val MARK_UNKNOWN: String = """
{
  "type": "object",
  "properties": {
    "index": {
      "type": "integer",
      "description": "要标记的题号，从 1 开始（第 1 题 = 1）。超出 1..当前题数 会返回错误。"
    },
    "unknown": {
      "type": "boolean",
      "description": "true = 标记该题「不会」（跳过作答），false = 取消该标记。"
    }
  },
  "required": ["index", "unknown"],
  "additionalProperties": false
}
"""
}
