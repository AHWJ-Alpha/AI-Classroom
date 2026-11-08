package com.aiclassroom.feature.plugin

/** 宿主已实现的插件 API 版本；manifest.minApiVersion 不得超过它。 */
object PluginApiVersion {
    const val CURRENT = 1
}

/**
 * manifest.permissions 白名单，与 PluginHostApi 的能力一一对应。
 *
 * 前六个是第一版就有的能力；后半段（classroom / exam / memory / knowledge / dialogs / timer）
 * 是第二批补全的扩展能力面，纯新增：旧插件不声明就完全不受影响。
 */
object PluginPermissions {
    const val IMAGE_GENERATE = "image.generate"
    const val TTS_SPEAK = "tts.speak"
    const val HTTP_REQUEST = "http.request"
    const val DIALOG_READ = "dialog.read"
    const val DIALOG_INSERT = "dialog.insert"
    const val STORAGE = "storage"

    /** 读课堂列表与元信息（名称、消息数、创建时间；不读全文）。 */
    const val CLASSROOM_READ = "classroom.read"

    /** 修改课堂（改标题、切换当前课堂）。 */
    const val CLASSROOM_WRITE = "classroom.write"

    /** 读当前课堂的考试（题目、作答、参考解析）。 */
    const val EXAM_READ = "exam.read"

    /** 写入考试（改题、标记不会）。 */
    const val EXAM_WRITE = "exam.write"

    /** 读长期记忆与世界书。 */
    const val MEMORY_READ = "memory.read"

    /** 写长期记忆。 */
    const val MEMORY_WRITE = "memory.write"

    /** 读知识库条目。 */
    const val KNOWLEDGE_READ = "knowledge.read"

    /** 跨课堂读取对话内容（含其它分支/课堂）——敏感，必须显式声明。 */
    const val DIALOGS_READ_ALL = "dialogs.readAll"

    /** 使用定时器（manifest 里声明 timer 扩展点需要它）。 */
    const val TIMER = "timer"

    /** 全屏层：占用 FULLSCREEN 插槽、调用 host.enterFullscreen / exitFullscreen。 */
    const val UI_FULLSCREEN = "ui.fullscreen"

    /** 读取用户在设置里选中的图片（settings 里 type=image 的字段）。 */
    const val STORAGE_READ_IMAGE = "storage.readImage"

    val ALL: Set<String> = linkedSetOf(
        IMAGE_GENERATE,
        TTS_SPEAK,
        HTTP_REQUEST,
        DIALOG_READ,
        DIALOG_INSERT,
        STORAGE,
        CLASSROOM_READ,
        CLASSROOM_WRITE,
        EXAM_READ,
        EXAM_WRITE,
        MEMORY_READ,
        MEMORY_WRITE,
        KNOWLEDGE_READ,
        DIALOGS_READ_ALL,
        TIMER,
        UI_FULLSCREEN,
        STORAGE_READ_IMAGE,
    )
}

/** manifest.extensions[].type 的扩展点。 */
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

    /** 定时器触发器：每 intervalSeconds 秒调一次 entry。 */
    TIMER("timer"),

    /** 事件触发器：宿主在 event 对应的时机调 entry。 */
    EVENT("event"),

    /**
     * 界面时钟：每 intervalMs 毫秒调一次 entry，跑完立刻让宿主重绘该插件的界面。
     * 与 timer 的区别：timer 是纯后台触发器（最小 60 秒），ui_ticker 是渲染驱动（最小 250 毫秒）。
     */
    UI_TICKER("ui_ticker"),
    ;

    companion object {
        fun fromWire(wire: String): PluginExtensionType? = entries.firstOrNull { it.wire == wire }

        val wireNames: List<String> = entries.map { it.wire }
    }
}

/**
 * 各扩展点允许的 slot / stage 取值。
 *
 * 第一批 UI 插槽（GLOBAL_OVERLAY / MESSAGE_BODY_TAIL / TOP_BAR_ACTIONS）是纯新增，
 * 旧的 chat_top / chat_bottom / chat_float / top_bar / bottom_bar 原样保留：
 * 已经装好的老插件不受影响，新插件才有新的挂载点。
 */
object PluginSlots {
    /**
     * 悬浮层：GLOBAL_OVERLAY 覆盖在课堂页最上层，chat_* 为旧值（保留兼容）。
     * FULLSCREEN 是独占的整屏层：同一时刻只有一个插件占用它，见 PluginUiDriver。
     */
    val UI_OVERLAY: Set<String> =
        linkedSetOf("GLOBAL_OVERLAY", "FULLSCREEN", "chat_top", "chat_bottom", "chat_float")

    /** 全屏层插槽名（独占、可由插件自己 enter/exit）。 */
    const val FULLSCREEN = "FULLSCREEN"

    /** 按钮位：TOP_BAR_ACTIONS 在顶栏右侧动作区，top_bar / bottom_bar 为旧值。 */
    val BAR_BUTTON: Set<String> = linkedSetOf("TOP_BAR_ACTIONS", "top_bar", "bottom_bar")

    /** 消息装饰位：MESSAGE_BODY_TAIL 在每条 AI 消息正文下方。 */
    val MESSAGE_DECORATOR: Set<String> = linkedSetOf("MESSAGE_BODY_TAIL")

    val PROMPT_HOOK_STAGE: Set<String> = linkedSetOf("before_send")
}

/**
 * event 扩展点允许的事件名（白名单）。宿主只分发这里列出的六种事件，
 * 插件 manifest 里写别的名字一律校验失败。
 */
object PluginEventNames {
    const val MESSAGE_SENT = "message_sent"
    const val ASSISTANT_REPLIED = "assistant_replied"
    const val APP_STARTED = "app_started"
    const val CLASS_SWITCHED = "class_switched"
    const val EXAM_SUBMITTED = "exam_submitted"

    /** 新一轮考试刚被建立（做题开始），宿主在考试存档落到课堂之后分发。 */
    const val EXAM_STARTED = "exam_started"

    /** 正式分发的事件名白名单（六个）。 */
    val ALL: Set<String> = linkedSetOf(
        MESSAGE_SENT,
        ASSISTANT_REPLIED,
        APP_STARTED,
        CLASS_SWITCHED,
        EXAM_SUBMITTED,
        EXAM_STARTED,
    )
}
