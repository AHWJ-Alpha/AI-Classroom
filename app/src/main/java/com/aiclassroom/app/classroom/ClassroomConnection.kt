package com.aiclassroom.app.classroom

import com.aiclassroom.classroom.domain.ProjectId
import com.aiclassroom.classroom.gateway.ModelPolicy

/** 一次任务要用的连接：策略 + 凭据。凭据不落库、不进快照。 */
data class ClassroomConnection(
    val policy: ModelPolicy,
    val apiKey: String,
)

/**
 * 项目来源解析结果。
 *
 * 关键约束：课堂执行的连接必须来自**项目自己的来源对话**，
 * 而不是「用户此刻正打开哪个对话」。否则从「全部项目」打开 A 的项目，
 * 会用 B 的模型与凭据去跑，结果还写回 A —— 这是不可接受的串线。
 */
sealed interface SourceResolution {

    data class Ready(
        val connection: ClassroomConnection,
        val conversationId: String,
        val conversationName: String,
    ) : SourceResolution

    /** 旧项目，还没关联任何对话。 */
    data object Unbound : SourceResolution

    /** 曾经关联过，但来源对话已被删除。 */
    data object SourceMissing : SourceResolution

    /** 来源还在，但缺少可用配置（没有模型或没有密钥）。 */
    data class ConfigMissing(val reason: String) : SourceResolution

    /** 面向用户的一句话说明；Ready 返回空串。 */
    fun describe(): String = when (this) {
        is Ready -> ""
        is Unbound -> "这个项目还没有关联对话，请先关联后再生成。"
        is SourceMissing -> "来源对话已被删除，无法解析模型配置。"
        is ConfigMissing -> "来源对话的配置不可用：" + reason
    }
}
