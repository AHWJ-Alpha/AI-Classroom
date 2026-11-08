package com.aiclassroom.feature.plugin

/** manifest 的校验结果；errors 为空且 manifest 非空才算通过。 */
data class ManifestValidation(val manifest: PluginManifest?, val errors: List<String>) {
    val ok: Boolean get() = manifest != null && errors.isEmpty()
}

/**
 * manifest.json 的严格校验。一次性收集所有错误（而不是遇到第一个就返回），
 * 这样插件作者一轮就能改完。
 *
 * 校验点：
 * 1. 包内必须有 manifest.json，且是合法 JSON 对象；
 * 2. id：必需，^[a-z0-9_]{2,32}$；
 * 3. name：必需，非空，<= 64；
 * 4. version：必需，语义化版本 MAJOR.MINOR.PATCH[-prerelease][+build]；
 * 5. description / author：可选字符串，长度上限 512 / 64；
 * 6. minApiVersion：必需整数，1..PluginApiVersion.CURRENT；
 * 7. permissions：可选数组，元素必须在白名单内，且不重复；
 * 8. extensions：可选数组（<= 32），type 必须是七类之一，按类型校验 slot / label / stage / entry / config；
 * 9. extensions[].config 必须指向包内真实文件（省略时默认 logic.js），路径不得穿越；
 * 10. extensions[].entry 必须是合法函数名，且能在 logic.js 里静态找到声明；
 * 11. timer：intervalSeconds 必须是 60..86400 的整数，同一插件最多 1 个；
 * 12. event：event 必须在事件白名单内，同一插件最多 8 个。
 */
object ManifestValidator {

    private val ID_PATTERN = Regex("^[a-z0-9_]{2,32}$")
    private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?(?:\\+[0-9A-Za-z.]+)?$")
    private val ENTRY_PATTERN = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

    private const val MAX_NAME = 64
    private const val MAX_DESCRIPTION = 512
    private const val MAX_AUTHOR = 64
    private const val MAX_LABEL = 24
    private const val MAX_EXTENSIONS = 32
    private const val MAX_PERMISSIONS = 16

    fun validate(pkg: PluginPackage): ManifestValidation =
        validate(pkg.manifestText, pkg.logicText, pkg.fileNames)

    fun validate(manifestText: String?, logicSource: String?, fileNames: Set<String>): ManifestValidation {
        if (manifestText == null) {
            return ManifestValidation(null, listOf("包内缺少必需的 ${PluginPackage.MANIFEST_FILE}"))
        }
        val root = try {
            Json.parse(manifestText)
        } catch (e: JsonParseException) {
            return ManifestValidation(null, listOf("${PluginPackage.MANIFEST_FILE} 不是合法 JSON：${e.message}"))
        }
        val obj = root as? JsonNode.Obj
            ?: return ManifestValidation(null, listOf("${PluginPackage.MANIFEST_FILE} 顶层必须是 JSON 对象"))

        val errors = mutableListOf<String>()
        val id = requiredString(obj, "id", errors)?.also {
            if (!ID_PATTERN.matches(it)) errors += "id 非法：'$it'（必须是小写字母/数字/下划线，2-32 位）"
        }
        val name = requiredString(obj, "name", errors)?.also {
            if (it.length > MAX_NAME) errors += "name 过长（最多 $MAX_NAME 字符）"
        }
        val version = requiredString(obj, "version", errors)?.also {
            if (!VERSION_PATTERN.matches(it)) errors += "version 非法：'$it'（必须形如 1.0.0）"
        }
        val description = optionalString(obj, "description", errors)?.also {
            if (it.length > MAX_DESCRIPTION) errors += "description 过长（最多 $MAX_DESCRIPTION 字符）"
        } ?: ""
        val author = optionalString(obj, "author", errors)?.also {
            if (it.length > MAX_AUTHOR) errors += "author 过长（最多 $MAX_AUTHOR 字符）"
        } ?: ""
        val minApiVersion = requiredInt(obj, "minApiVersion", errors)?.also {
            if (it < 1) {
                errors += "minApiVersion 必须 >= 1，实际 $it"
            } else if (it > PluginApiVersion.CURRENT) {
                errors += "插件要求 API 版本 $it，宿主只支持 ${PluginApiVersion.CURRENT}"
            }
        }
        val permissions = parsePermissions(obj, errors)
        val extensions = parseExtensions(obj, permissions, logicSource, fileNames, errors)

        if (errors.isNotEmpty() || id == null || name == null || version == null || minApiVersion == null) {
            return ManifestValidation(null, errors.ifEmpty { listOf("manifest 校验失败") })
        }
        return ManifestValidation(
            manifest = PluginManifest(id, name, version, description, author, minApiVersion, permissions, extensions),
            errors = emptyList(),
        )
    }

    private fun parsePermissions(obj: JsonNode.Obj, errors: MutableList<String>): List<String> {
        val node = obj.fields["permissions"] ?: return emptyList()
        if (node is JsonNode.Null) return emptyList()
        val array = node.asArrayOrNull
        if (array == null) {
            errors += "permissions 必须是字符串数组"
            return emptyList()
        }
        if (array.items.size > MAX_PERMISSIONS) errors += "permissions 最多 $MAX_PERMISSIONS 项"
        val result = LinkedHashSet<String>()
        for ((index, item) in array.items.withIndex()) {
            val permission = item.asStringOrNull
            if (permission == null) {
                errors += "permissions[$index] 必须是字符串"
                continue
            }
            if (permission !in PluginPermissions.ALL) {
                errors += "未知权限 permissions[$index]：'$permission'（白名单：${PluginPermissions.ALL.joinToString(", ")}）"
                continue
            }
            if (!result.add(permission)) errors += "permissions 重复声明：'$permission'"
        }
        return result.toList()
    }

    private fun parseExtensions(
        obj: JsonNode.Obj,
        permissions: List<String>,
        logicSource: String?,
        fileNames: Set<String>,
        errors: MutableList<String>,
    ): List<PluginExtension> {
        val node = obj.fields["extensions"] ?: return emptyList()
        if (node is JsonNode.Null) return emptyList()
        val array = node.asArrayOrNull
        if (array == null) {
            errors += "extensions 必须是数组"
            return emptyList()
        }
        if (array.items.size > MAX_EXTENSIONS) errors += "extensions 最多 $MAX_EXTENSIONS 项"
        val result = mutableListOf<PluginExtension>()
        var timerCount = 0
        var eventCount = 0
        for ((index, item) in array.items.withIndex()) {
            val extension = item.asObjectOrNull
            if (extension == null) {
                errors += "extensions[$index] 必须是对象"
                continue
            }
            val errorsBefore = errors.size
            val parsed = parseExtension(index, extension, permissions, logicSource, fileNames, errors)
            if (parsed != null && errors.size == errorsBefore) result += parsed
            // 数量上限按「声明了几项」计数：即使该项自身还有别的错误也要算，
            // 否则第二个 timer 会因为第一个的报错被提前 return 而漏报。
            when (PluginExtensionType.fromWire(extension.fields["type"]?.asStringOrNull.orEmpty())) {
                PluginExtensionType.TIMER -> {
                    timerCount++
                    if (timerCount > PluginLimits.MAX_TIMER_EXTENSIONS) {
                        errors += "extensions[" + index + "] 同一插件最多 " +
                            PluginLimits.MAX_TIMER_EXTENSIONS + " 个 timer，这是第 " + timerCount + " 个"
                    }
                }

                PluginExtensionType.EVENT -> {
                    eventCount++
                    if (eventCount > PluginLimits.MAX_EVENT_EXTENSIONS) {
                        errors += "extensions[" + index + "] 同一插件最多 " +
                            PluginLimits.MAX_EVENT_EXTENSIONS + " 个 event，这是第 " + eventCount + " 个"
                    }
                }

                else -> Unit
            }
        }
        return result
    }

    private fun parseExtension(
        index: Int,
        extension: JsonNode.Obj,
        permissions: List<String>,
        logicSource: String?,
        fileNames: Set<String>,
        errors: MutableList<String>,
    ): PluginExtension? {
        val prefix = "extensions[$index]"
        val typeName = extension.fields["type"]?.asStringOrNull
        if (typeName == null) {
            errors += "$prefix 缺少字符串字段 type"
            return null
        }
        val type = PluginExtensionType.fromWire(typeName)
        if (type == null) {
            errors += "未知扩展类型 $prefix.type='$typeName'（允许：${PluginExtensionType.wireNames.joinToString(", ")}）"
            return null
        }

        val slot = extension.fields["slot"]?.asStringOrNull
        val label = extension.fields["label"]?.asStringOrNull
        val stage = extension.fields["stage"]?.asStringOrNull
        val config = extension.fields["config"]?.asStringOrNull
        val entry = extension.fields["entry"]?.asStringOrNull
        val event = extension.fields["event"]?.asStringOrNull
        val intervalSeconds = parseIntervalSeconds(extension, prefix, errors)

        when (type) {
            PluginExtensionType.UI_OVERLAY -> {
                if (slot == null) {
                    errors += "$prefix (ui_overlay) 缺少 slot"
                } else if (slot !in PluginSlots.UI_OVERLAY) {
                    errors += "$prefix (ui_overlay) 未知 slot '$slot'（允许：${PluginSlots.UI_OVERLAY.joinToString(", ")}）"
                }
                if (config == null) errors += "$prefix (ui_overlay) 缺少 config（指向界面描述 json）"
            }

            PluginExtensionType.BAR_BUTTON -> {
                if (slot == null) {
                    errors += "$prefix (bar_button) 缺少 slot"
                } else if (slot !in PluginSlots.BAR_BUTTON) {
                    errors += "$prefix (bar_button) 未知 slot '$slot'（允许：${PluginSlots.BAR_BUTTON.joinToString(", ")}）"
                }
                if (label == null) errors += "$prefix (bar_button) 缺少 label"
                else if (label.length > MAX_LABEL) errors += "$prefix (bar_button) label 过长（最多 $MAX_LABEL 字符）"
                // 按钮的行为可以来自两处：logic.js 的 entry，或者 ui.json 里节点自己的 onTap。
                if (entry == null && !isUiConfig(config)) {
                    errors += "$prefix (bar_button) 缺少 entry（或声明 config 指向 ui.json，用节点 onTap 响应点击）"
                }
            }

            PluginExtensionType.PAGE -> {
                if (label == null) errors += "$prefix (page) 缺少 label"
                else if (label.length > MAX_LABEL) errors += "$prefix (page) label 过长（最多 $MAX_LABEL 字符）"
                if (entry == null) errors += "$prefix (page) 缺少 entry"
            }

            PluginExtensionType.MESSAGE_DECORATOR -> {
                // 旧写法：entry 返回装饰内容；新写法：config 指向 ui.json，由渲染器画在消息正文下方。
                if (slot != null && slot !in PluginSlots.MESSAGE_DECORATOR) {
                    errors += "$prefix (message_decorator) 未知 slot '$slot'（允许：${PluginSlots.MESSAGE_DECORATOR.joinToString(", ")}）"
                }
                if (entry == null && !isUiConfig(config)) {
                    errors += "$prefix (message_decorator) 缺少 entry（或声明 config 指向 ui.json）"
                }
            }

            PluginExtensionType.PROMPT_HOOK -> {
                if (stage == null) {
                    errors += "$prefix (prompt_hook) 缺少 stage"
                } else if (stage !in PluginSlots.PROMPT_HOOK_STAGE) {
                    errors += "$prefix (prompt_hook) 未知 stage '$stage'（允许：${PluginSlots.PROMPT_HOOK_STAGE.joinToString(", ")}）"
                }
                if (entry == null) errors += "$prefix (prompt_hook) 缺少 entry"
            }

            PluginExtensionType.TIMER -> {
                if (entry == null) errors += "$prefix (timer) 缺少 entry"
                if (intervalSeconds == null) errors += "$prefix (timer) 缺少合法整数 intervalSeconds"
                if (PluginPermissions.TIMER !in permissions) {
                    errors += "$prefix (timer) 声明定时器必须同时申请 ${PluginPermissions.TIMER} 权限"
                }
            }

            PluginExtensionType.EVENT -> {
                if (event == null) {
                    errors += "$prefix (event) 缺少 event"
                } else if (event !in PluginEventNames.ALL) {
                    errors += "$prefix (event) 未知事件 '$event'（允许：${PluginEventNames.ALL.joinToString(", ")}）"
                }
                if (entry == null) errors += "$prefix (event) 缺少 entry"
            }
        }

        val configFile = config ?: PluginPackage.LOGIC_FILE
        if (!PluginPackage.isSafeRelativePath(configFile)) {
            errors += "$prefix.config 非法路径：'$configFile'"
        } else if (configFile !in fileNames) {
            errors += "$prefix.config 引用的文件不存在：'$configFile'"
        } else if (type == PluginExtensionType.UI_OVERLAY && !configFile.endsWith(".json")) {
            errors += "$prefix (ui_overlay) config 必须是 .json 文件，实际 '$configFile'"
        } else if (type != PluginExtensionType.UI_OVERLAY &&
            configFile != PluginPackage.LOGIC_FILE &&
            !configFile.endsWith(".json")
        ) {
            // 非 ui_overlay 的扩展点只在「换成界面描述」时才有第二个 config，其它后缀没有意义。
            errors += "$prefix.config 指向 '$configFile'，但除了 ui.json 之外只允许默认的 ${PluginPackage.LOGIC_FILE}"
        }

        if (entry != null) {
            when {
                !ENTRY_PATTERN.matches(entry) ->
                    errors += "$prefix.entry 不是合法的函数名：'$entry'"
                configFile != PluginPackage.LOGIC_FILE ->
                    errors += "$prefix.entry 只能来自 ${PluginPackage.LOGIC_FILE}，但 config 指向 '$configFile'"
                !logicHasFunction(logicSource, entry) ->
                    errors += "$prefix.entry 函数 '$entry' 在 ${PluginPackage.LOGIC_FILE} 中不存在"
            }
        }

        return PluginExtension(
            type = type,
            slot = slot,
            label = label,
            config = config,
            entry = entry,
            stage = stage,
            intervalSeconds = intervalSeconds.takeIf { type == PluginExtensionType.TIMER },
            event = event.takeIf { type == PluginExtensionType.EVENT },
        )
    }

    /** config 是否指向一份界面描述（ui.json）；缺省与 logic.js 都不算。 */
    private fun isUiConfig(config: String?): Boolean =
        config != null && config.endsWith(".json") && config != PluginPackage.LOGIC_FILE

    /** intervalSeconds：字段缺省时返回 null（是否算错由调用方按类型决定），非整数 / 越界记错误并返回 null。 */
    private fun parseIntervalSeconds(
        extension: JsonNode.Obj,
        prefix: String,
        errors: MutableList<String>,
    ): Int? {
        val node = extension.fields["intervalSeconds"] ?: return null
        val value = node.asIntOrNull
        if (value == null) {
            errors += "$prefix.intervalSeconds 必须是整数"
            return null
        }
        val min = PluginLimits.MIN_TIMER_INTERVAL_SECONDS
        val max = PluginLimits.MAX_TIMER_INTERVAL_SECONDS
        if (value < min || value > max) {
            errors += "$prefix.intervalSeconds 越界：" + value + "（必须是 " + min + ".." + max + " 秒之间的整数）"
            return null
        }
        return value
    }

    /** 静态扫描 logic.js 里是否声明了该函数（运行时还会再兜底检查一次）。 */
    internal fun logicHasFunction(source: String?, name: String): Boolean {
        if (source == null || source.isBlank()) return false
        val quoted = Regex.escape(name)
        val patterns = listOf(
            Regex("\\bfunction\\s+$quoted\\s*\\("),
            Regex("\\b(?:var|let|const)\\s+$quoted\\s*=\\s*(?:async\\s*)?function\\b"),
            Regex("\\b(?:var|let|const)\\s+$quoted\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][A-Za-z0-9_$]*)\\s*=>"),
            Regex("(?:^|[\\s,{;])$quoted\\s*[:=]\\s*(?:async\\s*)?(?:function\\b|\\()"),
        )
        return patterns.any { it.containsMatchIn(source) }
    }

    private fun requiredString(obj: JsonNode.Obj, field: String, errors: MutableList<String>): String? {
        val node = obj.fields[field]
        if (node == null) {
            errors += "缺少必需字段 $field"
            return null
        }
        val text = node.asStringOrNull
        if (text == null) {
            errors += "字段 $field 必须是字符串"
            return null
        }
        if (text.isBlank()) {
            errors += "字段 $field 不能为空"
            return null
        }
        return text
    }

    private fun optionalString(obj: JsonNode.Obj, field: String, errors: MutableList<String>): String? {
        val node = obj.fields[field] ?: return null
        if (node is JsonNode.Null) return null
        val text = node.asStringOrNull
        if (text == null) {
            errors += "字段 $field 必须是字符串"
            return null
        }
        return text
    }

    private fun requiredInt(obj: JsonNode.Obj, field: String, errors: MutableList<String>): Int? {
        val node = obj.fields[field]
        if (node == null) {
            errors += "缺少必需字段 $field"
            return null
        }
        val value = node.asIntOrNull
        if (value == null) {
            errors += "字段 $field 必须是整数"
            return null
        }
        return value
    }
}
