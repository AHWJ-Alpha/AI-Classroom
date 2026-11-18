package com.aiclassroom.app.classroom

import com.aiclassroom.app.model.Classroom
import com.aiclassroom.classroom.domain.ProjectId
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 项目 -> 来源对话 -> 连接 的解析。
 *
 * 单独成类是为了能被测：这段逻辑一旦放回 MainActivity，
 * 「从 B 打开 A 的项目却用了 B 的配置」这种错误就只能靠人眼发现。
 */
internal class ClassroomSourceResolver(
    private val conversations: () -> List<Classroom>,
    private val store: ClassroomStore,
) {

    /**
     * 项目对应的来源对话本体。
     *
     * 同步来源设定时必须用**这个**对话的 config/files/记忆，
     * 不能用用户此刻打开的对话 —— 否则在 B 打开 A 的项目点同步，会把 B 的资料写进 A。
     */
    fun conversationOf(projectId: ProjectId): Classroom? {
        val sourceId = store.project(projectId)?.sourceConversationId ?: return null
        return conversations().firstOrNull { it.id == sourceId }
    }

    fun resolve(projectId: ProjectId): SourceResolution {
        val project = store.project(projectId) ?: return SourceResolution.SourceMissing
        val sourceId = project.sourceConversationId ?: return SourceResolution.Unbound
        val source = conversations().firstOrNull { it.id == sourceId }
            ?: return SourceResolution.SourceMissing

        if (source.config.baseUrl.isBlank()) {
            return SourceResolution.ConfigMissing("来源对话没有填写接口地址")
        }
        if (source.config.apiKey.isBlank()) {
            return SourceResolution.ConfigMissing("来源对话没有填写密钥")
        }
        val policy = ClassroomGatewayFactory.policyOf(source.config)
        if (!policy.usable) {
            return SourceResolution.ConfigMissing("来源对话没有可用模型")
        }
        return SourceResolution.Ready(
            connection = ClassroomConnection(policy = policy, apiKey = source.config.apiKey),
            conversationId = source.id,
            conversationName = source.name,
        )
    }
}
