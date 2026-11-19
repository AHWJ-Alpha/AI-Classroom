package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.store.FileClassroomStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 按版本读：清单写下来之后必须真的被用上。
 */
class ManifestResolverTest {

    private val now = 1_700_000_000_000L
    private val projectId = ProjectId("p1")
    private val unitA = UnitId("u1")
    private val unitB = UnitId("u2")

    private fun store(): FileClassroomStore {
        val s = FileClassroomStore(Files.createTempDirectory("mr").toFile())
        s.createProject(
            ClassroomProject(
                id = projectId, title = "t", type = ProjectType.NOVEL,
                lifecycle = ProjectLifecycle.ACTIVE, workVersion = 2,
                createdAt = now, updatedAt = now,
            ),
        )
        s.saveRevision(revision("r1", unitA, "第一版正文"))
        s.saveRevision(revision("r2", unitA, "第二版正文"))
        s.upsertUnit(
            ClassroomUnit(
                unitA, projectId, 1, ProjectType.NOVEL, "第一章", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
                currentRevisionId = RevisionId("r2"), officialVersion = 2,
            ),
        )
        // 第 1 版只有 u1，第 2 版仍是 u1 但换了 revision。
        s.saveManifest(
            WorkManifest(
                projectId, workVersion = 1, specVersion = 1, outlineVersion = 1,
                snapshotId = null, createdAt = now,
                entries = listOf(ManifestEntry(unitA, 1, RevisionId("r1"))),
            ),
        )
        s.saveManifest(
            WorkManifest(
                projectId, workVersion = 2, specVersion = 1, outlineVersion = 1,
                snapshotId = null, createdAt = now,
                entries = listOf(ManifestEntry(unitA, 1, RevisionId("r2"))),
            ),
        )
        return s
    }

    private fun revision(id: String, unitId: UnitId, text: String) = UnitRevision(
        id = RevisionId(id), unitId = unitId, projectId = projectId, version = 1,
        draftText = text,
        content = UnitContent(
            schemaVersion = 1, unitId = unitId, planVersion = 1, title = "第一章",
            objectivesCovered = emptyList(), unresolvedItems = emptyList(), events = emptyList(),
            blocks = listOf(ContentBlock.Narration("b1", text)),
        ),
        createdAt = now, committedAt = now, sourceHash = "h",
    )

    @Test
    fun `版本号从新到旧列出`() {
        assertEquals(listOf(2, 1), ManifestResolver.versions(store(), projectId))
    }

    @Test
    fun `读旧版本拿到的是那一版的正文`() {
        val s = store()
        val at1 = ManifestResolver.revisionAt(s, projectId, unitA, 1)
        val found = assertIs<ManifestResolver.Resolution.Found>(at1)
        assertEquals("第一版正文", found.revision.draftText)

        val at2 = ManifestResolver.revisionAt(s, projectId, unitA, 2)
        assertEquals("第二版正文", assertIs<ManifestResolver.Resolution.Found>(at2).revision.draftText)
    }

    @Test
    fun `该版本没有这一节时明确缺失 而不是给当前正文`() {
        val s = store()
        val missing = assertIs<ManifestResolver.Resolution.Missing>(
            ManifestResolver.revisionAt(s, projectId, unitB, 1),
        )
        assertTrue(missing.reason.contains("没有这一节"))
    }

    @Test
    fun `旧项目没有清单时退回当前版本并标注`() {
        val s = FileClassroomStore(Files.createTempDirectory("mr2").toFile())
        s.saveRevision(revision("r9", unitA, "旧正文"))
        s.upsertUnit(
            ClassroomUnit(
                unitA, projectId, 1, ProjectType.NOVEL, "第一章", "目标",
                emptyList(), emptyList(), listOf("完成"), 0, UnitStatus.READY,
                currentRevisionId = RevisionId("r9"), officialVersion = 1,
            ),
        )
        val legacy = assertIs<ManifestResolver.Resolution.Legacy>(
            ManifestResolver.revisionAt(s, projectId, unitA, null),
        )
        assertEquals("旧正文", legacy.revision.draftText)
    }
}
