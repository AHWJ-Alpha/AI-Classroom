package com.aiclassroom.feature.plugin

/** 宿主已实现的插件 API 版本；manifest.minApiVersion 不得超过它。 */
object PluginApiVersion {
    const val CURRENT = 1
}

/** manifest.permissions 白名单，与 PluginHostApi 的能力一一对应。 */
object PluginPermissions {
    const val IMAGE_GENERATE = "image.generate"
    const val TTS_SPEAK = "tts.speak"
    const val HTTP_REQUEST = "http.request"
    const val DIALOG_READ = "dialog.read"
    const val DIALOG_INSERT = "dialog.insert"
    const val STORAGE = "storage"

    val ALL: Set<String> = linkedSetOf(
        IMAGE_GENERATE,
        TTS_SPEAK,
        HTTP_REQUEST,
        DIALOG_READ,
        DIALOG_INSERT,
        STORAGE,
    )
}

/** manifest.extensions[].type 的五类扩展点。 */
enum class PluginExtensionType(val wire: String) {
    /** 聊天界面悬浮层，config 指向 ui.json。 */
    UI_OVERLAY("ui_overlay"),

    /** 消息卡片装饰，插件返回要渲染的卡片描述。 */
    MESSAGE_DECORATOR("message_decorator"),

    /** 顶栏 / 底栏按钮。 */
    BAR_BUTTON("bar_button"),

    /** 独立插件页面。 */
    PAGE("page"),

    /** 提示词钩子，可改写 system prompt / 待发送内容。 */
    PROMPT_HOOK("prompt_hook"),
    ;

    companion object {
        fun fromWire(wire: String): PluginExtensionType? = entries.firstOrNull { it.wire == wire }

        val wireNames: List<String> = entries.map { it.wire }
    }
}

/** 各扩展点允许的 slot / stage 取值。 */
object PluginSlots {
    val UI_OVERLAY: Set<String> = linkedSetOf("chat_top", "chat_bottom", "chat_float")
    val BAR_BUTTON: Set<String> = linkedSetOf("top_bar", "bottom_bar")
    val PROMPT_HOOK_STAGE: Set<String> = linkedSetOf("before_send")
}
