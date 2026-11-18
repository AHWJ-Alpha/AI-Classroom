package com.aiclassroom.classroom.domain

/**
 * 快照里的一条已确立事实。
 *
 * 为什么不是纯字符串：可见性、知情人、证据一旦丢掉，
 * 后面就再也无法按角色边界过滤 —— 只能全部注入或全部不注入，两种都错。
 */
data class SnapshotFact(
    val content: String,
    val visibility: Visibility = Visibility.AUTHOR_ONLY,
    /** 来源里的角色名（角色卡名或明确姓名），与 SnapshotCharacter.sourceCharacterKey 对应。 */
    val knownBy: Set<String> = emptySet(),
    val unknownBy: Set<String> = emptySet(),
    val evidence: String = "",
) {

    /**
     * 某个观众能不能看到这条事实。
     *
     * viewerKeys 为 null 表示作者视角（全知）；否则传入该视角可见的角色名集合。
     * 判定顺序刻意是「先排除不知道的人，再看可见性」：
     * 一条 public 事实若显式列了 unknownBy，那个人依然不该知道。
     */
    fun visibleTo(viewerKeys: Set<String>?): Boolean {
        if (viewerKeys == null) return true
        fun matches(names: Set<String>) = names.any { name -> viewerKeys.any { it.equals(name, true) } }
        if (matches(unknownBy)) return false
        return when (visibility) {
            Visibility.PUBLIC -> true
            Visibility.RESTRICTED -> matches(knownBy)
            Visibility.AUTHOR_ONLY -> false
        }
    }
}

/**
 * 来源存档里的可见性字符串 -> 领域枚举。
 *
 * 主对话用的取值是 public / author_private / character_private，
 * 而课堂快照曾经用 != "author" 去过滤 —— 那个值从来不存在，
 * 结果作者私密记忆被当成公开写进了快照。这里统一成一处映射。
 */
object VisibilityRules {

    fun fromLegacy(raw: String?): Visibility = when (raw?.trim()?.lowercase()) {
        null, "" -> Visibility.AUTHOR_ONLY
        "public" -> Visibility.PUBLIC
        "character_private", "restricted" -> Visibility.RESTRICTED
        else -> Visibility.AUTHOR_ONLY
    }
}
