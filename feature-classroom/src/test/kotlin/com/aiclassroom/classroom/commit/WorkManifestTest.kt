package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.CommitResult
import com.aiclassroom.classroom.store.FileClassroomStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * 作品版本清单：一版一份、写下不可变。
 *
 * 之前 workVersion 只是个数字，用户打开「第 1 版」看到的其实是当前正文。
 */
class WorkManifestTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val runId = RunId("r1")

    private fun store(): InMemoryStore {
        val s = InMemoryStore()
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.saveSpec(
            ProjectSpec(
                projectId = projectId, version = 7, rawRequest = "写故事", topic = "校园",
                goals = listOf("讲完"), audience = "读者", language = "zh", style = "平实",
                expectedLength = "短", unitCount = 2, runMode = RunMode.CONFIRM_OUTLINE,
                reviewPolicy = ReviewPolicy.STANDARD, hardConstraints = emptyList(),
                authorRules = emptyList(), publicWorld = "", createdAt = now,
            ),
        )
        return s
    }

    private fun unit(s: InMemoryStore, id: String, index: Int) {
        s.upsertUnit(
            ClassroomUnit(
                UnitId(id), projectId, index, ProjectType.NOVEL, "第" + index + "章", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.DRAFT_READY,
            ),
        )
    }

    private fun revision(unitId: String) = UnitRevision(
        id = RevisionId("rev-" + unitId), unitId = UnitId(unitId), projectId = projectId, version = 0,
        draftText = "正文",
        content = UnitContent(
            schemaVersion = 1, unitId = UnitId(unitId), planVersion = 1, title = "章",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(), events = emptyList(),
            blocks = listOf(ContentBlock.Narration("b1", "正文")),
        ),
        createdAt = now, sourceHash = "h",
    )

    private fun commit(s: InMemoryStore, unitId: String, key: String) =
        UnitCommitter(s) { now }.commit(
            CommitRequest(
                projectId = projectId, unitId = UnitId(unitId), revision = revision(unitId),
                statePatch = null, runId = runId, idempotencyKey = key,
            ),
        )

    @Test
    fun `提交后产生与项目版本号一致的清单`() {
        val s = store()
        unit(s, "u1", 1)
        commit(s, "u1", "k1")

        val project = s.project(projectId)!!
        val manifest = s.manifest(projectId, project.workVersion)
        assertNotNull(manifest)
        assertEquals(1, manifest.entries.size)
        assertEquals(UnitId("u1"), manifest.entries.first().unitId)
        assertEquals(7, manifest.specVersion)
    }

    @Test
    fun `旧版本清单不会被后续提交改写`() {
        val s = store()
        unit(s, "u1", 1)
        unit(s, "u2", 2)
        commit(s, "u1", "k1")
        val first = s.latestManifest(projectId)!!

        commit(s, "u2", "k2")
        val second = s.latestManifest(projectId)!!

        assertEquals(2, second.entries.size)
        assertEquals(1, s.manifest(projectId, first.workVersion)!!.entries.size, "旧清单必须原样保留")
    }

    @Test
    fun `清单记录的是那一版实际使用的 revision`() {
        val s = store()
        unit(s, "u1", 1)
        val result = commit(s, "u1", "k1")
        assertIs<CommitResult.Committed>(result)

        val manifest = s.latestManifest(projectId)!!
        assertEquals((result as CommitResult.Committed).revision.id, manifest.revisionOf(UnitId("u1")))
    }

    @Test
    fun `清单能落盘并读回`() {
        val dir = Files.createTempDirectory("manifest").toFile()
        val s = FileClassroomStore(dir)
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, createdAt = now, updatedAt = now,
            ),
        )
        s.saveManifest(
            WorkManifest(
                projectId = projectId, workVersion = 3, specVersion = 1, outlineVersion = 1,
                snapshotId = "s1", createdAt = now,
                entries = listOf(ManifestEntry(UnitId("u1"), 1, RevisionId("r1"))),
            ),
        )

        val reopened = FileClassroomStore(dir)
        val back = reopened.manifest(projectId, 3)!!
        assertEquals(1, back.entries.size)
        assertEquals(RevisionId("r1"), back.entries.first().revisionId)
    }
}
