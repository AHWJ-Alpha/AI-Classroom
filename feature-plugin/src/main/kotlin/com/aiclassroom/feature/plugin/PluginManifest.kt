package com.aiclassroom.feature.plugin

/** manifest.extensions 里的一项扩展点声明。 */
data class PluginExtension(
    val type: PluginExtensionType,
    val slot: String? = null,
    val label: String? = null,
    val config: String? = null,
    val entry: String? = null,
    val stage: String? = null,
    /** timer 扩展点：触发间隔（秒）。 */
    val intervalSeconds: Int? = null,
    /** event 扩展点：监听的事件名（白名单之一）。 */
    val event: String? = null,
    /**
     * ui_overlay 的取数函数名：宿主每次渲染前调它拿最新 state。
     * 不填时沿用「插件自己 setState / host.requestUiRefresh」的旧路径。
     */
    val provider: String? = null,
    /** ui_ticker 扩展点：每次触发间隔（毫秒）。 */
    val intervalMillis: Int? = null,
) {
    /** config 省略时默认走包内的 logic.js。 */
    val configFile: String get() = config ?: PluginPackage.LOGIC_FILE
}

/** settings[].default 的取值：只会是这三种之一，具体哪种由 type 决定。 */
sealed class PluginSettingDefault {
    data class Text(val value: String) : PluginSettingDefault()
    data class Number(val value: Double) : PluginSettingDefault()
    data class Flag(val value: Boolean) : PluginSettingDefault()

    /** 存进宿主 key-value 表时的字符串形式（number 去尾，boolean 用 true/false）。 */
    fun asStorageValue(): String = when (this) {
        is Text -> value
        is Number -> if (value == Math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            value.toString()
        }

        is Flag -> if (value) "true" else "false"
    }
}

/** settings[] 的一项：插件在 manifest 里声明的配置字段，宿主据此自动生成表单。 */
data class PluginSetting(
    val key: String,
    val label: String,
    val type: PluginSettingType,
    val required: Boolean = false,
    val description: String? = null,
    val placeholder: String? = null,
    val defaultValue: PluginSettingDefault? = null,
    /** 仅 number 有效。 */
    val min: Double? = null,
    /** 仅 number 有效。 */
    val max: Double? = null,
    /** 仅 select 有效，非空。 */
    val options: List<String> = emptyList(),
) {
    /**
     * secret 的默认值不进任何字符串输出：manifest 会被日志、设置页与错误信息打印，
     * 一旦 toString 带上明文，脱敏就形同虚设。宿主存储里 secret 仍是明文（本地 prefs）。
     */
    override fun toString(): String {
        val defaultText = defaultValue
            ?.let { if (type == PluginSettingType.SECRET) "<hidden>" else it.asStorageValue() }
            ?: "null"
        return "PluginSetting(key=" + key + ", label=" + label + ", type=" + type.wire +
            ", required=" + required + ", description=" + description +
            ", placeholder=" + placeholder + ", default=" + defaultText +
            ", min=" + min + ", max=" + max + ", options=" + options + ")"
    }
}

/** settings[].type 的取值。 */
enum class PluginSettingType(val wire: String) {
    TEXT("text"),
    SECRET("secret"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    SELECT("select"),
    /** 用户在设置页选的本地图片：宿主弹系统选择器并复制进插件私有目录，prefs 里存路径。 */
    IMAGE("image"),
    ;

    companion object {
        private val byWire: Map<String, PluginSettingType> = entries.associateBy { it.wire }

        fun fromWire(value: String?): PluginSettingType? = value?.let { byWire[it] }

        val wireNames: List<String> get() = entries.map { it.wire }
    }
}

/** 通过校验的 manifest。 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val minApiVersion: Int,
    val permissions: List<String>,
    val extensions: List<PluginExtension>,
    /** settings 段（配置表单声明）；没有声明时为空列表。 */
    val settings: List<PluginSetting> = emptyList(),
) {
    fun extensionsOf(type: PluginExtensionType): List<PluginExtension> = extensions.filter { it.type == type }

    /** 该插件在指定阶段注册的钩子入口函数名，没有则返回 null。 */
    fun promptHookEntry(stage: String = "before_send"): String? =
        extensions.firstOrNull { it.type == PluginExtensionType.PROMPT_HOOK && it.stage == stage }?.entry

    /** 定时器声明（同一插件最多一个）。 */
    val timerExtension: PluginExtension? get() = extensions.firstOrNull { it.type == PluginExtensionType.TIMER }

    /** 界面时钟声明（同一插件最多一个）。 */
    val uiTickerExtension: PluginExtension? get() =
        extensions.firstOrNull { it.type == PluginExtensionType.UI_TICKER }

    /** 声明在 FULLSCREEN 插槽上的界面扩展点；没有则 null。 */
    val fullscreenExtension: PluginExtension? get() = extensions.firstOrNull {
        it.type == PluginExtensionType.UI_OVERLAY && it.slot == PluginSlots.FULLSCREEN
    }

    /** 声明在指定 slot 上的扩展点（按声明顺序）。 */
    fun extensionsInSlot(slot: String): List<PluginExtension> = extensions.filter { it.slot == slot }

    /** 该插件在指定事件上注册的入口函数名，没有则返回 null。 */
    fun eventEntry(event: String): String? =
        extensions.firstOrNull { it.type == PluginExtensionType.EVENT && it.event == event }?.entry
}
