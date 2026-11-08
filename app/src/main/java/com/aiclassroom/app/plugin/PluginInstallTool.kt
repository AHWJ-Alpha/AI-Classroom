package com.aiclassroom.app.plugin

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.plugin.PluginPackage
import org.json.JSONObject
import java.util.concurrent.CancellationException

/**
 * plugin_install：让 AI 在对话里自己造一个插件（内存里拼包 → 校验 → 落盘 → 注册）。
 *
 * 参数只有 manifest.json / logic.js / ui.json 三段文本，不含 assets：
 * 需要图片、音频的插件在运行时用 host.generateImage / host.speak 自己生成。
 *
 * 与「设置 → 导入插件文件」共用同一个 [PluginManager.install]，因此校验、落盘、注册的语义完全一致。
 */
class PluginInstallTool(
    private val manager: PluginManager,
    private val onInstalled: (LoadedPluginInfo) -> Unit = {},
) : ToolHandler {

    override val name: String = "plugin_install"

    override val description: String =
        "把一段 manifest.json + logic.js（可选 ui.json）装成一个本机插件，装好后立刻生效。" +
            "manifest 必须声明 id（小写字母/数字/下划线，2-32 位）、name、version（如 1.0.0）、minApiVersion（填 1）、" +
            "permissions（白名单：image.generate / tts.speak / http.request / dialog.read / dialog.insert / storage）" +
            "和 extensions（type 为 prompt_hook 时要给出 entry 函数名与 stage=before_send）。" +
            "logic.js 里的 prompt_hook 函数签名是 function onBeforePrompt(ctx)，返回新 prompt 字符串即替换、返回 null 表示不改写；" +
            "可通过 host.generateImage / host.speak / host.httpRequest / host.readDialog / host.insertDialog 调用宿主能力，" +
            "ctx.storage.get/set 存插件私有状态。插件包不含图片音频等资源文件。" +
            "已存在同 id 插件时会被覆盖更新。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "插件展示名（manifest.name 缺省时用它补上），例如「时间流动」"
            },
            "manifestJson": {
              "type": "string",
              "description": "完整的 manifest.json 文本"
            },
            "script": {
              "type": "string",
              "description": "完整的 logic.js 文本（插件脚本，入口函数是普通 function 声明）"
            },
            "uiJson": {
              "type": "string",
              "description": "可选的 ui.json 文本；本版本不渲染界面，留空即可"
            }
          },
          "required": ["name", "manifestJson", "script"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val arguments = try {
            JSONObject(argumentsJson)
        } catch (t: Throwable) {
            return ToolResult.Error("plugin_install 参数不是合法 JSON 对象：" + (t.message ?: t.javaClass.simpleName))
        }
        val displayName = arguments.optString("name").trim()
        val manifestText = arguments.optString("manifestJson").trim()
        val script = arguments.optString("script")
        val uiJson = arguments.optString("uiJson").trim()

        if (manifestText.isBlank()) return ToolResult.Error("plugin_install 缺少 manifestJson")
        if (script.isBlank()) return ToolResult.Error("plugin_install 缺少 script（logic.js 内容）")

        val manifest = try {
            normalizeManifest(manifestText, displayName)
        } catch (t: Throwable) {
            return ToolResult.Error("manifestJson 不合法：" + (t.message ?: t.javaClass.simpleName))
        }

        val entries = linkedMapOf(
            PluginPackage.MANIFEST_FILE to manifest,
            PluginPackage.LOGIC_FILE to script,
        )
        if (uiJson.isNotBlank()) entries[PluginPackage.UI_FILE] = uiJson

        val pkg = try {
            PluginPackage.ofText(entries)
        } catch (t: Throwable) {
            return ToolResult.Error("插件包构建失败：" + (t.message ?: t.javaClass.simpleName))
        }

        return try {
            when (val result = manager.install(pkg, replace = true)) {
                is PluginImportResult.Installed -> {
                    onInstalled(result.info)
                    ToolResult.Ok(
                        JSONObject()
                            .put("ok", true)
                            .put("id", result.info.id)
                            .put("name", result.info.name)
                            .put("version", result.info.version)
                            .put("replaced", result.replaced)
                            .put("promptHook", result.info.promptHook)
                            .toString()
                    )
                }

                is PluginImportResult.Failed -> ToolResult.Error("plugin_install 失败：" + result.message)
                is PluginImportResult.Conflict -> ToolResult.Error("plugin_install 冲突：" + result.pluginId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ToolResult.Error("plugin_install 失败：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /** 补上 AI 最容易漏的字段：name（用参数补）、version、minApiVersion；其余交给 ManifestValidator 严格校验。 */
    private fun normalizeManifest(manifestText: String, displayName: String): String {
        val json = JSONObject(manifestText)
        if (json.optString("name").isBlank() && displayName.isNotBlank()) json.put("name", displayName)
        if (json.optString("version").isBlank()) json.put("version", "1.0.0")
        if (!json.has("minApiVersion")) json.put("minApiVersion", 1)
        return json.toString()
    }
}
