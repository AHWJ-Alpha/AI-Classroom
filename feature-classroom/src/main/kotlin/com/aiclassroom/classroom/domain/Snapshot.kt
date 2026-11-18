package com.aiclassroom.classroom.domain

/**
 * 来源上下文快照。
 *
 * 创建项目时从来源对话拍一张，之后每个 run 都用这张冻结的数据生成；
 * 来源后来改了角色卡或加了消息，不会悄悄改变正在跑的任务。
 *
 * 明确不放的东西：
 * - **任何 API Key**：凭据只在宿主配置里，执行时解析；
 * - 作者私密段落不会进角色视角的提示词（见 [SnapshotCharacter.authorPrivate] 的用法）。
 */
data class ContextSnapshot(
    val id: SnapshotId,
    val projectId: ProjectId,
    val version: Int,
    val sourceConversationId: String? = null,
    val createdAt: EpochMillis,
    /** 讲师 / 叙述者身份。 */
    val mentorName: String = "",
    val mentorPrompt: String = "",
    val userAlias: String = "",
    /** 视角：author（作者全知）或 character（某个角色）。 */
    val viewMode: String = "author",
    /** 角色视角时对应的来源角色。 */
    val activeCharacterId: String? = null,
    /** 所有人可知的公共背景。 */
    val publicWorldBook: String = "",
    /** 作者私密世界书：**绝不进入角色视角或正文生成**。 */
    val authorWorldBook: String = "",
    val characters: List<SnapshotCharacter> = emptyList(),
    val materials: List<SnapshotMaterial> = emptyList(),
    /** 纳入的历史边界（消息下标）；之后的新消息不在快照里。 */
    val historyThroughIndex: Int = -1,
    /** 创建时已经确立的事实摘要，供生成参考。 */
    val establishedFacts: List<String> = emptyList(),
)

/**
 * 快照里的角色。
 *
 * [sourceCharacterKey] 是来源对话里的角色标识（角色卡文件名或旧记忆名），
 * [projectCharacterId] 是项目内的稳定 ID —— 一次创建后持续复用，
 * 不能每次生成重新映射，否则人物状态会串。
 */
data class SnapshotCharacter(
    val projectCharacterId: CharacterId,
    val sourceCharacterKey: String,
    val name: String,
    /** 固定人格描述（可进入生成）。 */
    val persona: String = "",
    val speechStyle: String = "",
    /** 作者私密段落：只在作者视角使用。 */
    val authorPrivate: String = "",
)

/** 快照里的知识资料。存内容而不是文件名 —— 同名文件换了内容不能悄悄改变设定。 */
data class SnapshotMaterial(
    val name: String,
    val type: String = "",
    val content: String = "",
    /** 内容指纹，用于判断「来源资料是否变了」。 */
    val checksum: String = "",
) {
    companion object {
        /** 只保留前 N 个字符作为生成素材，避免把整本书塞进提示词。 */
        fun excerpt(content: String, limit: Int = 4000): String =
            if (content.length <= limit) content else content.take(limit) + "…（已截断）"
    }
}
