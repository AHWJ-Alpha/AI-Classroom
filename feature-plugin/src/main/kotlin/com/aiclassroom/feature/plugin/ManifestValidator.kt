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
 * 12. event：event 必须在事件白名单内，同一插件最多 8 个；
 * 13. settings：配置表单声明，逐项校验 key / label / type / required / default / min / max / options；
 * 14. ui_overlay 的 provider：合法函数名 + 在 logic.js 里存在；FULLSCREEN 插槽必须申请 ui.fullscreen 权限；
 * 15. ui_ticker：entry 必需，intervalMs 在 250..60000 之间，同一插件最多 1 个。
 */
object ManifestValidator {

    private val ID_PATTERN = Regex("^[a-z0-9_]{2,32}$")
    private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?(?:\\+[0-9A-Za-z.]+)?$")
    private val ENTRY_PATTERN = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")
    // settings 的 key 允许 camelCase（JS 里 ctx.settings.stepMinutes 这样读最自然），
    // 但首字符必须是小写字母，避免数字开头或与 JS 保留字冲突。
    private val SETTING_KEY_PATTERN = Regex("^[a-z][A-Za-z0-9_]{0,31}$")

    private const val MAX_NAME = 64
    private const val MAX_DESCRIPTION = 512
    private const val MAX_AUTHOR = 64
    private const val MAX_LABEL = 24
    private const val MAX_EXTENSIONS = 32
    private const val MAX_PERMISSIONS = 16
    private const val MAX_SETTINGS = 16
    private const val MAX_SETTING_LABEL = 32
    private const val MAX_SETTING_HINT = 120

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
        val settings = parseSettings(obj, errors)

        if (errors.isNotEmpty() || id == null || name == null || version == null || minApiVersion == null) {
            return ManifestValidation(null, errors.ifEmpty { listOf("manifest 校验失败") })
        }
        return ManifestValidation(
            manifest = PluginManifest(
                id = id,
                name = name,
                version = version,
                description = description,
                author = author,
                minApiVersion = minApiVersion,
                permissions = permissions,
                extensions = extensions,
                settings = settings,
            ),
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
        var tickerCount = 0
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

                PluginExtensionType.UI_TICKER -> {
                    tickerCount++
                    if (tickerCount > PluginLimits.MAX_UI_TICKER_EXTENSIONS) {
                        errors += "extensions[" + index + "] 同一插件最多 " +
                            PluginLimits.MAX_UI_TICKER_EXTENSIONS + " 个 ui_ticker，这是第 " + tickerCount + " 个"
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
        val provider = extension.fields["provider"]?.asStringOrNull
        val intervalSeconds = parseIntervalSeconds(extension, prefix, errors)
        val intervalMillis = parseIntervalMillis(extension, prefix, errors)

        when (type) {
            PluginExtensionType.UI_OVERLAY -> {
                if (slot == null) {
                    errors += "$prefix (ui_overlay) 缺少 slot"
                } else if (slot !in PluginSlots.UI_OVERLAY) {
                    errors += "$prefix (ui_overlay) 未知 slot '$slot'（允许：${PluginSlots.UI_OVERLAY.joinToString(", ")}）"
                } else if (slot == PluginSlots.FULLSCREEN &&
                    PluginPermissions.UI_FULLSCREEN !in permissions
                ) {
                    errors += "$prefix (ui_overlay) 声明 FULLSCREEN 插槽必须同时申请 " +
                        PluginPermissions.UI_FULLSCREEN + " 权限（全屏层会隐藏系统栏）"
                }
                if (config == null) errors += "$prefix (ui_overlay) 缺少 config（指向界面描述 json）"
            }

            PluginExtensionType.UI_TICKER -> {
                if (entry == null) errors += "$prefix (ui_ticker) 缺少 entry"
                if (intervalMillis == null) {
                    errors += "$prefix (ui_ticker) intervalMs 缺失或越界（必须是 " +
                        PluginLimits.MIN_UI_TICKER_INTERVAL_MILLIS + ".." +
                        PluginLimits.MAX_UI_TICKER_INTERVAL_MILLIS + " 之间的整数）"
                }
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

        // provider：只有 ui_overlay 有意义，其它类型写了就是作者写错了，直接报错而不是静默忽略。
        val resolvedProvider = if (provider == null) {
            null
        } else if (type != PluginExtensionType.UI_OVERLAY) {
            errors += "$prefix.provider 只对 ui_overlay 有效，当前 type=" + type.wire
            null
        } else if (!ENTRY_PATTERN.matches(provider)) {
            errors += "$prefix.provider 不是合法的函数名：'$provider'"
            null
        } else if (!logicHasFunction(logicSource, provider)) {
            errors += "$prefix.provider 函数 '$provider' 在 " + PluginPackage.LOGIC_FILE + " 中不存在"
            null
        } else {
            provider
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
            provider = resolvedProvider,
            intervalMillis = intervalMillis.takeIf { type == PluginExtensionType.UI_TICKER },
        )
    }

    /**
     * settings：插件声明需要哪些配置，宿主据此自动生成表单。
     * 数组内 key 不可重复；错误信息一律带 settings[i] 与字段名，插件作者一轮就能改完。
     */
    private fun parseSettings(obj: JsonNode.Obj, errors: MutableList<String>): List<PluginSetting> {
        val node = obj.fields["settings"] ?: return emptyList()
        if (node is JsonNode.Null) return emptyList()
        val array = node.asArrayOrNull
        if (array == null) {
            errors += "settings 必须是数组"
            return emptyList()
        }
        if (array.items.size > MAX_SETTINGS) errors += "settings 最多 " + MAX_SETTINGS + " 项"
        val result = mutableListOf<PluginSetting>()
        val seenKeys = HashSet<String>()
        for ((index, item) in array.items.withIndex()) {
            val errorsBefore = errors.size
            val parsed = parseSetting(index, item, seenKeys, errors)
            if (parsed != null && errors.size == errorsBefore) result += parsed
        }
        return result
    }

    private fun parseSetting(
        index: Int,
        item: JsonNode,
        seenKeys: MutableSet<String>,
        errors: MutableList<String>,
    ): PluginSetting? {
        val prefix = "settings[" + index + "]"
        val setting = item.asObjectOrNull
        if (setting == null) {
            errors += prefix + " 必须是对象"
            return null
        }

        val key = setting.fields["key"]?.asStringOrNull
        if (key == null) {
            errors += prefix + " 缺少字符串字段 key"
        } else if (!SETTING_KEY_PATTERN.matches(key)) {
            errors += prefix + ".key 非法：'" + key + "'（首字符小写字母，之后字母/数字/下划线，1-32 位，例：stepMinutes）"
        } else if (!seenKeys.add(key)) {
            errors += prefix + ".key 重复声明：'" + key + "'"
        }

        val label = setting.fields["label"]?.asStringOrNull
        if (label == null) {
            errors += prefix + " 缺少字符串字段 label"
        } else if (label.isBlank()) {
            errors += prefix + ".label 不能为空"
        } else if (label.length > MAX_SETTING_LABEL) {
            errors += prefix + ".label 过长（最多 " + MAX_SETTING_LABEL + " 字符）"
        }

        val typeName = setting.fields["type"]?.asStringOrNull
        val type = if (typeName == null) {
            errors += prefix + " 缺少字符串字段 type"
            null
        } else {
            PluginSettingType.fromWire(typeName).also {
                if (it == null) {
                    errors += "未知配置类型 " + prefix + ".type='" + typeName +
                        "'（允许：" + PluginSettingType.wireNames.joinToString(", ") + "）"
                }
            }
        }

        val required = when (val requiredNode = setting.fields["required"]) {
            null -> false
            is JsonNode.Null -> false
            is JsonNode.Bool -> requiredNode.value
            else -> {
                errors += prefix + ".required 必须是布尔值"
                false
            }
        }

        val description = optionalSettingHint(setting, "description", prefix, errors)
        val placeholder = optionalSettingHint(setting, "placeholder", prefix, errors)

        val defaultNode = setting.fields["default"]
        val defaultValue = if (defaultNode == null || defaultNode is JsonNode.Null) {
            null
        } else if (type == null) {
            // type 已经报错，不再叠加「default 类型不匹配」的噪声。
            null
        } else {
            when (type) {
                PluginSettingType.TEXT, PluginSettingType.SECRET -> {
                    val text = defaultNode.asStringOrNull
                    if (text == null) {
                        errors += prefix + ".default 必须是字符串（type=" + type.wire + "）"
                        null
                    } else {
                        PluginSettingDefault.Text(text)
                    }
                }

                PluginSettingType.NUMBER -> {
                    val number = defaultNode.asNumberOrNull
                    if (number == null) {
                        errors += prefix + ".default 必须是数字（type=number）"
                        null
                    } else {
                        PluginSettingDefault.Number(number)
                    }
                }

                PluginSettingType.BOOLEAN -> when (defaultNode) {
                    is JsonNode.Bool -> PluginSettingDefault.Flag(defaultNode.value)
                    else -> {
                        errors += prefix + ".default 必须是布尔值（type=boolean）"
                        null
                    }
                }

                PluginSettingType.SELECT -> {
                    val text = defaultNode.asStringOrNull
                    if (text == null) {
                        errors += prefix + ".default 必须是字符串（type=select）"
                        null
                    } else {
                        PluginSettingDefault.Text(text)
                    }
                }

                // image 的值是宿主给出的私有目录路径，插件写 default 没有意义；
                // 真写了就按普通字符串收下（等于预置一张图），不做额外解释。
                PluginSettingType.IMAGE -> {
                    val text = defaultNode.asStringOrNull
                    if (text == null) {
                        errors += prefix + ".default 必须是字符串（type=image）"
                        null
                    } else {
                        PluginSettingDefault.Text(text)
                    }
                }
            }
        }

        val min = if (type == PluginSettingType.NUMBER) {
            optionalNumber(setting, "min", prefix, errors)
        } else {
            rejectFieldForType(setting, "min", prefix, type, errors)
            null
        }
        val max = if (type == PluginSettingType.NUMBER) {
            optionalNumber(setting, "max", prefix, errors)
        } else {
            rejectFieldForType(setting, "max", prefix, type, errors)
            null
        }
        if (min != null && max != null && min > max) {
            errors += prefix + " 的 min 不能大于 max（" + min + " > " + max + "）"
        }

        val options = if (type == PluginSettingType.SELECT) {
            parseOptions(setting, prefix, errors)
        } else {
            rejectFieldForType(setting, "options", prefix, type, errors)
            emptyList()
        }

        // default 与 min/max、options 的一致性：只在都合法时校验，避免一个错带出一串错。
        if (defaultValue is PluginSettingDefault.Number) {
            val value = defaultValue.value
            if (min != null && value < min) {
                errors += prefix + ".default 越界：" + formatNumber(value) + "（不得小于 min=" + formatNumber(min) + "）"
            }
            if (max != null && value > max) {
                errors += prefix + ".default 越界：" + formatNumber(value) + "（不得大于 max=" + formatNumber(max) + "）"
            }
        }
        if (defaultValue is PluginSettingDefault.Text && options.isNotEmpty() && defaultValue.value !in options) {
            errors += prefix + ".default='" + defaultValue.value + "' 不在 options 里（允许：" +
                options.joinToString(", ") + "）"
        }

        return PluginSetting(
            key = key.orEmpty(),
            label = label.orEmpty(),
            type = type ?: PluginSettingType.TEXT,
            required = required,
            description = description,
            placeholder = placeholder,
            defaultValue = defaultValue,
            min = min,
            max = max,
            options = options,
        )
    }

    private fun optionalSettingHint(
        setting: JsonNode.Obj,
        field: String,
        prefix: String,
        errors: MutableList<String>,
    ): String? {
        val node = setting.fields[field] ?: return null
        if (node is JsonNode.Null) return null
        val text = node.asStringOrNull
        if (text == null) {
            errors += prefix + "." + field + " 必须是字符串"
            return null
        }
        if (text.length > MAX_SETTING_HINT) {
            errors += prefix + "." + field + " 过长（最多 " + MAX_SETTING_HINT + " 字符）"
            return null
        }
        return text
    }

    private fun optionalNumber(
        setting: JsonNode.Obj,
        field: String,
        prefix: String,
        errors: MutableList<String>,
    ): Double? {
        val node = setting.fields[field] ?: return null
        if (node is JsonNode.Null) return null
        val value = node.asNumberOrNull
        if (value == null) {
            errors += prefix + "." + field + " 必须是数字"
            return null
        }
        return value
    }

    /** min / max / options 只对特定类型有意义，出现在别的类型上是作者写错了，直接报错而不是静默忽略。 */
    private fun rejectFieldForType(
        setting: JsonNode.Obj,
        field: String,
        prefix: String,
        type: PluginSettingType?,
        errors: MutableList<String>,
    ) {
        val node = setting.fields[field] ?: return
        if (node is JsonNode.Null) return
        val onlyFor = if (field == "options") "select" else "number"
        errors += prefix + "." + field + " 只对 type=" + onlyFor + " 有效，当前 type=" +
            (type?.wire ?: "未知")
    }

    private fun parseOptions(
        setting: JsonNode.Obj,
        prefix: String,
        errors: MutableList<String>,
    ): List<String> {
        val node = setting.fields["options"]
        if (node == null || node is JsonNode.Null) {
            errors += prefix + " (select) 缺少 options（必须是非空字符串数组）"
            return emptyList()
        }
        val array = node.asArrayOrNull
        if (array == null) {
            errors += prefix + ".options 必须是字符串数组"
            return emptyList()
        }
        if (array.items.isEmpty()) {
            errors += prefix + ".options 不能为空（type=select 至少给一个选项）"
            return emptyList()
        }
        if (array.items.size > MAX_SETTINGS * 2) {
            errors += prefix + ".options 最多 " + (MAX_SETTINGS * 2) + " 个选项"
        }
        val result = mutableListOf<String>()
        for ((optionIndex, item) in array.items.withIndex()) {
            val option = item.asStringOrNull
            when {
                option == null -> errors += prefix + ".options[" + optionIndex + "] 必须是字符串"
                option.isBlank() -> errors += prefix + ".options[" + optionIndex + "] 不能为空"
                option in result -> errors += prefix + ".options 重复：'" + option + "'"
                else -> result += option
            }
        }
        return result
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

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

    /** intervalMs：缺省时返回默认值 1000；非整数 / 越界记错误并返回 null。 */
    private fun parseIntervalMillis(
        extension: JsonNode.Obj,
        prefix: String,
        errors: MutableList<String>,
    ): Int? {
        val node = extension.fields["intervalMs"] ?: return PluginLimits.DEFAULT_UI_TICKER_INTERVAL_MILLIS
        val value = node.asIntOrNull
        if (value == null) {
            errors += "$prefix.intervalMs 必须是整数"
            return null
        }
        val min = PluginLimits.MIN_UI_TICKER_INTERVAL_MILLIS
        val max = PluginLimits.MAX_UI_TICKER_INTERVAL_MILLIS
        if (value < min || value > max) {
            errors += "$prefix.intervalMs 越界：" + value + "（必须是 " + min + ".." + max + " 毫秒之间的整数）"
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
