package com.aiclassroom.feature.exam.tool.image

/** generate_image 的 function calling 参数 Schema。 */
internal object ImageToolSchemas {

    val GENERATE_IMAGE: String = """
        {
          "type": "object",
          "properties": {
            "prompt": {
              "type": "string",
              "description": "画面描述（正向提示词）。写清主体、场景、风格、光线、构图；SD 后端建议按「主体, 细节, 风格, 画质词」的顺序用逗号分隔。"
            },
            "negativePrompt": {
              "type": "string",
              "description": "反向提示词，只在本地 SD / WebUI 后端生效，用来排除不想要的内容。"
            },
            "width": {
              "type": "integer",
              "description": "图片宽度像素，16-4096，仅 sdwebui / custom 后端使用；云端后端按配置里的 size 走，会忽略宽高。"
            },
            "height": {
              "type": "integer",
              "description": "图片高度像素，16-4096，同上。"
            },
            "steps": {
              "type": "integer",
              "description": "采样步数，1-150，仅 sdwebui / custom 后端使用。"
            },
            "cfgScale": {
              "type": "number",
              "description": "提示词引导强度 CFG，0-30，仅 sdwebui / custom 后端使用。"
            },
            "timeoutSeconds": {
              "type": "integer",
              "description": "本次生图的超时秒数，1-300，默认 120。生图普遍较慢，除非网络很差不要改小。"
            }
          },
          "required": ["prompt"],
          "additionalProperties": false
        }
    """.trimIndent()
}
