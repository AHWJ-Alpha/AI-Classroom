package com.aiclassroom.feature.plugin

/**
 * manifest.settings 与用户实际填的值的合并、序列化与完整性判定。
 *
 * 三条规则：
 * 1. 合并只认 manifest 里声明过的 key —— 插件改版删掉的旧键不会再流进 ctx.settings；
 * 2. 空白值视为「没填」，回落到该字段的 default；没有 default 就不出现在结果里（JS 侧读到 undefined）；
 * 3. 序列化按 type 给真类型：number 给数字、boolean 给布尔，其余给字符串。
 */
/**
 * 生效中的配置值。secret 的明文只在按 key 取值时给出，toString / equals 的输出里一律抹掉 ——
 * 这张表会被日志、设置页与异常信息顺手打印，明文一旦漏进去脱敏就形同虚设。
 */
class PluginSettingsValues(
    private val stored: Map<String, String>,
    private val secretKeys: Set<String>,
) : Map<String, String> by stored {

    override fun toString(): String = stored.entries.joinToString(", ", "{", "}") { (key, value) ->
        key + "=" + if (key in secretKeys && value.isNotEmpty()) "<hidden>" else value
    }
}

object PluginSettingsCodec {

    /** 把用户存下来的值按 manifest 补齐 default，得到「当前生效值」。 */
    fun resolve(declared: List<PluginSetting>, stored: Map<String, String>): PluginSettingsValues {
        val result = LinkedHashMap<String, String>()
        for (setting in declared) {
            val raw = stored[setting.key]
            val value = if (raw == null || raw.isBlank()) setting.defaultValue?.asStorageValue() else raw
            if (value != null) result[setting.key] = value
        }
        val secretKeys = declared.filter { it.type == PluginSettingType.SECRET }.map { it.key }.toSet()
        return PluginSettingsValues(result, secretKeys)
    }

    /** 已生效值的 JSON（交给 JsBridge 转成 ctx.settings）；secret 也在这里，脚本本来就要用它。 */
    fun toJson(declared: List<PluginSetting>, resolved: Map<String, String>): String {
        val fields = LinkedHashMap<String, JsonNode>()
        for (setting in declared) {
            val value = resolved[setting.key] ?: continue
            fields[setting.key] = when (setting.type) {
                PluginSettingType.NUMBER -> value.toDoubleOrNull()?.let { JsonNode.Num(it) }
                    ?: JsonNode.Str(value)

                PluginSettingType.BOOLEAN -> JsonNode.Bool(value == "true")
                else -> JsonNode.Str(value)
            }
        }
        return Json.write(JsonNode.Obj(fields))
    }

    /** 某字段的值是否算「填好了」：必填的文本不能空白，number 要能解析且不越界，select 要在选项内。 */
    fun isComplete(setting: PluginSetting, resolved: Map<String, String>): Boolean {
        val value = resolved[setting.key]
        if (value == null || value.isBlank()) return setting.defaultValue != null
        return when (setting.type) {
            PluginSettingType.NUMBER -> numberProblem(setting, value) == null
            PluginSettingType.SELECT -> setting.options.isEmpty() || value in setting.options
            else -> true
        }
    }

    /** 未填 / 填错的必填项 key 列表（设置页据此标红并显示「未完成 M 项」）。 */
    fun missingRequired(declared: List<PluginSetting>, resolved: Map<String, String>): List<String> =
        declared.filter { it.required && !isComplete(it, resolved) }.map { it.key }

    /** number 字段的越界说明；没问题返回 null（设置页直接显示这句话）。 */
    fun numberProblem(setting: PluginSetting, raw: String): String? {
        val value = raw.trim().toDoubleOrNull() ?: return "请填数字"
        val min = setting.min
        val max = setting.max
        return when {
            min != null && value < min -> "不能小于 " + formatNumber(min)
            max != null && value > max -> "不能大于 " + formatNumber(max)
            else -> null
        }
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
}
