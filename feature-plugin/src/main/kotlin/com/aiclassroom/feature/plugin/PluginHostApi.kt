package com.aiclassroom.feature.plugin

/**
 * 宿主能力接口：本模块只定义契约，:app 负责实现（复用它已有的生图 / TTS / 网络 / 对话 / prefs）。
 *
 * 挂起函数在 JS 侧以同步外观暴露（内部阻塞等待），因此实现方必须保证：
 * - 不会阻塞在等待主线程的调度上（详见 BlockingSuspend 的限制说明）；
 * - 失败时返回 PluginResult.Failure 而不是抛异常，异常也会被兜住并转成 host_error。
 *
 * 第二批能力（classroom / exam / memory / knowledge / dialogs）返回的文本必须是紧凑 JSON，
 * 且由实现方按 [PluginLimits.MAX_RESULT_BYTES] 自行限量；HostFacade 还会再兜一层截断。
 */
interface PluginHostApi {

    /** 生图：复用主 app 已有的 image 能力，Success.path 返回落盘路径。 */
    suspend fun generateImage(prompt: String, width: Int?, height: Int?): PluginResult

    /** TTS：Success.path 返回音频文件路径。 */
    suspend fun speak(text: String): PluginResult

    /** HTTP：走已有 net 能力（受总开关约束）。 */
    suspend fun httpRequest(url: String, method: String, body: String?): PluginResult

    /** 读取当前课堂上下文 / 最近对话。 */
    suspend fun readDialog(limit: Int): PluginResult

    /** 往对话里插入内容。 */
    suspend fun insertDialog(role: String, text: String): PluginResult

    /** 插件私有存储（按插件 id 隔离的 key-value）。 */
    fun storageGet(pluginId: String, key: String): String?

    fun storagePut(pluginId: String, key: String, value: String)

    /** 插件要求刷新界面时调用。 */
    fun requestUiRefresh(pluginId: String)

    /**
     * 进入该插件的全屏层（需要 ui.fullscreen 权限，且插件声明了 FULLSCREEN 插槽）。
     * 宿主负责隐藏系统状态栏与导航栏；同一时刻只允许一个插件占用全屏层。
     */
    fun enterFullscreen(pluginId: String)

    /** 退出全屏层并恢复系统栏。插件没调过 enter 时是无操作。 */
    fun exitFullscreen(pluginId: String)

    /**
     * 弹系统图片选择器，把用户选中的图片**复制进插件私有目录**，返回落盘路径。
     * 需要 storage.readImage 权限；用户取消返回 code=cancelled。
     */
    suspend fun pickImage(pluginId: String): PluginResult

    /** 课堂列表与元信息（名称、消息数等），不含对话全文。 */
    suspend fun listClassrooms(): PluginResult

    /** 修改课堂：改标题 / 切换当前课堂。patchJson 形如 {"rename":{"index":0,"name":"…"}} 或 {"select":{"index":1}}。 */
    suspend fun writeClassroom(patchJson: String): PluginResult

    /** 当前课堂的考试（题目、作答、参考解析）。 */
    suspend fun readExam(): PluginResult

    /** 写入考试：改题 / 标记不会。patchJson 形如 {"questions":[{"index":0,"answer":"…","unknown":true}]}。 */
    suspend fun writeExam(patchJson: String): PluginResult

    /** 长期记忆与世界书。 */
    suspend fun readMemory(): PluginResult

    /** 追加一条长期记忆。 */
    suspend fun writeMemory(text: String): PluginResult

    /** 知识库条目。 */
    suspend fun readKnowledge(): PluginResult

    /** 跨课堂读对话；classroomId 为空表示全部课堂，limitPerClassroom 由宿主再夹一次上限。 */
    suspend fun readAllDialogs(classroomId: String?, limitPerClassroom: Int): PluginResult
}
