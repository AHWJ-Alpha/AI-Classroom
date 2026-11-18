package com.aiclassroom.app.classroom

import com.aiclassroom.app.model.Classroom
import com.aiclassroom.app.model.ClassroomConfig
import com.aiclassroom.classroom.domain.ClassroomProject
import com.aiclassroom.classroom.domain.BindingStatus
import com.aiclassroom.classroom.domain.ProjectId
import com.aiclassroom.classroom.domain.ProjectLifecycle
import com.aiclassroom.classroom.domain.ProjectType
import com.aiclassroom.classroom.store.FileClassroomStore
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「从 B 打开 A 的项目」必须用 A 的配置 —— 这是联动里最容易串线的一处。
 */
class ClassroomSourceResolverTest {

    private val now = 1_700_000_000_000L

    private fun conversation(
        id: String,
        name: String,
        baseUrl: String,
        model: String,
        key: String,
    ) = Classroom(
        name = name,
        topic = "t",
        messages = mutableListOf(),
        branches = mutableListOf(),
        memories = mutableListOf(),
        corePremises = mutableListOf(),
        memoryCards = mutableListOf(),
        chapters = mutableListOf(),
        files = mutableListOf(),
        config = ClassroomConfig(
            provider = "OpenAI",
            apiKey = key,
            baseUrl = baseUrl,
            selectedModel = model,
            modelChain = model,
        ),
        id = id,
    )

    private fun storeWith(project: ClassroomProject): FileClassroomStore {
        val s = FileClassroomStore(Files.createTempDirectory("src").toFile())
        s.createProject(project)
        return s
    }

    private fun project(sourceId: String?) = ClassroomProject(
        id = ProjectId("p1"), title = "A 的项目", type = ProjectType.TEACHING,
        lifecycle = ProjectLifecycle.DRAFT, createdAt = now, updatedAt = now,
        sourceConversationId = sourceId,
        binding = if (sourceId != null) BindingStatus.BOUND else BindingStatus.UNBOUND,
    )

    @Test
    fun `用来源对话的地址与模型 而不是当前对话`() {
        val a = conversation("conv-a", "数学", "https://a.example/v1", "model-a", "key-a")
        val b = conversation("conv-b", "语文", "https://b.example/v1", "model-b", "key-b")
        val resolver = ClassroomSourceResolver({ listOf(a, b) }, storeWith(project("conv-a")))

        val ready = resolver.resolve(ProjectId("p1")) as SourceResolution.Ready
        assertEquals("https://a.example/v1", ready.connection.policy.baseUrl)
        assertEquals("model-a", ready.connection.policy.primaryModel)
        assertEquals("key-a", ready.connection.apiKey)
        assertEquals("数学", ready.conversationName)
    }

    @Test
    fun `未关联项目明确返回 Unbound 而不是借用当前对话`() {
        val a = conversation("conv-a", "数学", "https://a.example/v1", "m", "k")
        val resolver = ClassroomSourceResolver({ listOf(a) }, storeWith(project(null)))
        assertTrue(resolver.resolve(ProjectId("p1")) is SourceResolution.Unbound)
    }

    @Test
    fun `来源被删除返回 SourceMissing`() {
        val resolver = ClassroomSourceResolver({ emptyList() }, storeWith(project("gone")))
        assertTrue(resolver.resolve(ProjectId("p1")) is SourceResolution.SourceMissing)
    }

    @Test
    fun `来源缺密钥返回 ConfigMissing 不静默兜底`() {
        val a = conversation("conv-a", "数学", "https://a.example/v1", "m", "")
        val resolver = ClassroomSourceResolver({ listOf(a) }, storeWith(project("conv-a")))
        val missing = resolver.resolve(ProjectId("p1")) as SourceResolution.ConfigMissing
        assertTrue(missing.reason.contains("密钥"))
    }

    @Test
    fun `项目不存在时按来源缺失处理`() {
        val resolver = ClassroomSourceResolver({ emptyList() }, storeWith(project("conv-a")))
        assertTrue(resolver.resolve(ProjectId("nope")) is SourceResolution.SourceMissing)
    }
}
