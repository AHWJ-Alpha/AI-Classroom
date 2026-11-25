package com.aiclassroom.classroom.commit

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 测试用的内存实现。
 *
 * [transaction] 真的做快照与回滚 —— 只有这样，提交器的「要么全生效要么全不生效」
 * 才能被验证，而不是测试替身自己假装通过了。
 */
class InMemoryStore : ClassroomStore {

    private val projectMap = linkedMapOf<ProjectId, ClassroomProject>()
    private val specMap = linkedMapOf<Pair<ProjectId, Int>, ProjectSpec>()
    private val outlineMap = linkedMapOf<Pair<ProjectId, Int>, ProjectOutline>()
    private val unitMap = linkedMapOf<UnitId, ClassroomUnit>()
    private val revisionMap = linkedMapOf<RevisionId, UnitRevision>()
    private val factMap = linkedMapOf<Pair<FactId, Int>, FactRevision>()
    private val charStateMap = linkedMapOf<String, CharacterState>()
    private val charMap = linkedMapOf<String, CharacterProfile>()
    private val draftMap = linkedMapOf<String, UnitDraft>()
    private val runMap = linkedMapOf<RunId, GenerationRun>()
    private val attemptMap = linkedMapOf<AttemptId, GenerationAttempt>()
    private val checkpointMap = linkedMapOf<RunId, Checkpoint>()
    private val readerMap = linkedMapOf<ProjectId, ReaderProgress>()
    private val learnMap = linkedMapOf<Pair<ProjectId, String>, LearningProgress>()
    private val saveMap = linkedMapOf<SaveId, PlaythroughSave>()

    /** 记录事务回滚次数，供测试断言。 */
    var rollbackCount = 0
        private set

    override fun createProject(project: ClassroomProject) { projectMap[project.id] = project }
    override fun project(id: ProjectId) = projectMap[id]
    override fun projects() = projectMap.values.toList()
    override fun updateProject(project: ClassroomProject) { projectMap[project.id] = project }

    override fun saveSpec(spec: ProjectSpec) { specMap[spec.projectId to spec.version] = spec }
    override fun spec(id: ProjectId, version: Int) = specMap[id to version]
    override fun latestSpec(id: ProjectId) = specMap.filterKeys { it.first == id }.maxByOrNull { it.key.second }?.value

    private val snapshotMap = linkedMapOf<Pair<ProjectId, Int>, ContextSnapshot>()
    override fun saveSnapshot(snapshot: ContextSnapshot) {
        snapshotMap[snapshot.projectId to snapshot.version] = snapshot
    }
    override fun latestSnapshot(projectId: ProjectId) =
        snapshotMap.filterKeys { it.first == projectId }.maxByOrNull { it.key.second }?.value

    override fun saveOutline(outline: ProjectOutline) { outlineMap[outline.projectId to outline.version] = outline }
    override fun outline(id: ProjectId, version: Int) = outlineMap[id to version]
    override fun latestOutline(id: ProjectId) =
        outlineMap.filterKeys { it.first == id }.maxByOrNull { it.key.second }?.value

    override fun upsertUnit(unit: ClassroomUnit) { unitMap[unit.id] = unit }
    override fun unit(unitId: UnitId) = unitMap[unitId]
    override fun units(projectId: ProjectId) =
        unitMap.values.filter { it.projectId == projectId }.sortedBy { it.index }

    override fun saveRevision(revision: UnitRevision) { revisionMap[revision.id] = revision }
    override fun revision(revisionId: RevisionId) = revisionMap[revisionId]
    override fun revisions(unitId: UnitId) =
        revisionMap.values.filter { it.unitId == unitId }.sortedBy { it.version }

    override fun officialRevision(unitId: UnitId) =
        revisionMap.values.filter { it.unitId == unitId && it.committedAt != null }
            .maxByOrNull { it.version }

    override fun saveFact(fact: FactRevision) { factMap[fact.factId to fact.version] = fact }
    override fun facts(projectId: ProjectId) = factMap.values.filter { it.projectId == projectId }
    override fun latestFactVersion(factId: FactId, projectId: ProjectId) =
        factMap.filterKeys { it.first == factId }.maxByOrNull { it.key.second }?.value

    private fun charKey(projectId: ProjectId?, characterId: CharacterId) =
        (projectId?.value ?: "") + "#" + characterId.value

    override fun saveCharacterState(state: CharacterState) {
        charStateMap[charKey(state.projectId, state.characterId)] = state
    }
    override fun characterStates(projectId: ProjectId) =
        charStateMap.values.filter { it.projectId == projectId }
    override fun characterState(projectId: ProjectId, characterId: CharacterId) =
        charStateMap[charKey(projectId, characterId)]
    override fun saveCharacter(profile: CharacterProfile) {
        charMap[charKey(profile.projectId, profile.id)] = profile
    }
    override fun characters(projectId: ProjectId) =
        charMap.values.filter { it.projectId == projectId }

    override fun deleteProject(projectId: ProjectId) {
        projectMap.remove(projectId)
        specMap.keys.filter { it.first == projectId }.forEach { specMap.remove(it) }
        outlineMap.keys.filter { it.first == projectId }.forEach { outlineMap.remove(it) }
        unitMap.values.filter { it.projectId == projectId }.map { it.id }.forEach { unitMap.remove(it) }
        revisionMap.values.filter { it.projectId == projectId }.map { it.id }.forEach { revisionMap.remove(it) }
        draftMap.keys.filter { it.startsWith(projectId.value + "#") }.forEach { draftMap.remove(it) }
        runMap.values.filter { it.projectId == projectId }.map { it.id }.forEach { runMap.remove(it) }
    }

    override fun saveRun(run: GenerationRun) { runMap[run.id] = run }
    override fun deleteRun(runId: RunId) { runMap.remove(runId) }
    override fun run(runId: RunId) = runMap[runId]
    override fun activeRun(projectId: ProjectId) =
        runMap.values.firstOrNull { it.projectId == projectId && it.status == RunStatus.RUNNING }

    override fun runs(projectId: ProjectId) =
        runMap.values.filter { it.projectId == projectId }

    override fun saveAttempt(attempt: GenerationAttempt) { attemptMap[attempt.id] = attempt }
    override fun attempts(runId: RunId) = attemptMap.values.filter { it.runId == runId }

    override fun saveCheckpoint(checkpoint: Checkpoint) { checkpointMap[checkpoint.runId] = checkpoint }
    override fun checkpoint(runId: RunId) = checkpointMap[runId]

    private val eventMap = linkedMapOf<String, ClassroomEvent>()
    private val stateSnapMap = linkedMapOf<UnitId, UnitStateSnapshot>()
    override fun saveStateSnapshot(snapshot: UnitStateSnapshot) { stateSnapMap[snapshot.unitId] = snapshot }
    override fun stateSnapshots(projectId: ProjectId) =
        stateSnapMap.values.filter { it.projectId == projectId }.sortedBy { it.unitIndex }

    private val summaryMap = linkedMapOf<UnitId, ChapterSummary>()
    override fun saveSummary(summary: ChapterSummary) { summaryMap[summary.unitId] = summary }
    override fun summaries(projectId: ProjectId) =
        summaryMap.values.filter { it.projectId == projectId }.sortedBy { it.index }
    override fun summaryForRevision(revisionId: RevisionId) =
        summaryMap.values.firstOrNull { it.revisionId == revisionId }

    private fun draftKey(projectId: ProjectId, unitId: UnitId) = projectId.value + "#" + unitId.value

    override fun draft(projectId: ProjectId, unitId: UnitId): UnitDraft? =
        draftMap[draftKey(projectId, unitId)]

    override fun saveDraft(draft: UnitDraft) {
        draftMap[draftKey(draft.projectId, draft.unitId)] = draft
    }

    override fun removeDraft(projectId: ProjectId, unitId: UnitId) {
        draftMap.remove(draftKey(projectId, unitId))
    }

    private val manifestMap = linkedMapOf<Pair<ProjectId, Int>, WorkManifest>()
    override fun saveManifest(manifest: WorkManifest) {
        manifestMap[manifest.projectId to manifest.workVersion] = manifest
    }
    override fun manifest(projectId: ProjectId, workVersion: Int) =
        manifestMap[projectId to workVersion]
    override fun latestManifest(projectId: ProjectId) =
        manifestMap.filterKeys { it.first == projectId }.maxByOrNull { it.key.second }?.value

    override fun saveEvent(event: ClassroomEvent) { eventMap[event.eventId] = event }
    override fun event(eventId: String) = eventMap[eventId]
    override fun pendingEvents(sourceConversationId: String) =
        eventMap.values.filter {
            it.sourceConversationId == sourceConversationId && it.delivery == DeliveryState.PENDING
        }.sortedBy { it.createdAt }

    override fun saveReaderProgress(progress: ReaderProgress) { readerMap[progress.projectId] = progress }
    override fun readerProgress(projectId: ProjectId) = readerMap[projectId]

    override fun saveLearningProgress(progress: LearningProgress) {
        learnMap[progress.projectId to progress.objectiveId] = progress
    }

    override fun learningProgress(projectId: ProjectId, objectiveId: String) =
        learnMap[projectId to objectiveId]

    override fun savePlaythrough(save: PlaythroughSave) { saveMap[save.id] = save }
    override fun playthroughs(projectId: ProjectId) = saveMap.values.filter { it.projectId == projectId }
    override fun playthrough(id: SaveId) = saveMap[id]

    override fun <T> transaction(block: () -> T): T {
        val snapshot = Snapshot(
            projectMap.toMap(), specMap.toMap(), outlineMap.toMap(), unitMap.toMap(),
            revisionMap.toMap(), factMap.toMap(), charStateMap.toMap(), charMap.toMap(),
            runMap.toMap(), attemptMap.toMap(), checkpointMap.toMap(), readerMap.toMap(),
            learnMap.toMap(), saveMap.toMap(),
        )
        return try {
            block()
        } catch (t: Throwable) {
            projectMap.reset(snapshot.projectMap); specMap.reset(snapshot.specMap)
            outlineMap.reset(snapshot.outlineMap); unitMap.reset(snapshot.unitMap)
            revisionMap.reset(snapshot.revisionMap); factMap.reset(snapshot.factMap)
            charStateMap.reset(snapshot.charStateMap); charMap.reset(snapshot.charMap)
            runMap.reset(snapshot.runMap); attemptMap.reset(snapshot.attemptMap)
            checkpointMap.reset(snapshot.checkpointMap); readerMap.reset(snapshot.readerMap)
            learnMap.reset(snapshot.learnMap); saveMap.reset(snapshot.saveMap)
            rollbackCount++
            throw t
        }
    }

    private data class Snapshot(
        val projectMap: Map<ProjectId, ClassroomProject>,
        val specMap: Map<Pair<ProjectId, Int>, ProjectSpec>,
        val outlineMap: Map<Pair<ProjectId, Int>, ProjectOutline>,
        val unitMap: Map<UnitId, ClassroomUnit>,
        val revisionMap: Map<RevisionId, UnitRevision>,
        val factMap: Map<Pair<FactId, Int>, FactRevision>,
        val charStateMap: Map<String, CharacterState>,
        val charMap: Map<String, CharacterProfile>,
        val runMap: Map<RunId, GenerationRun>,
        val attemptMap: Map<AttemptId, GenerationAttempt>,
        val checkpointMap: Map<RunId, Checkpoint>,
        val readerMap: Map<ProjectId, ReaderProgress>,
        val learnMap: Map<Pair<ProjectId, String>, LearningProgress>,
        val saveMap: Map<SaveId, PlaythroughSave>,
    )

    private fun <K, V> MutableMap<K, V>.reset(from: Map<K, V>) {
        clear()
        putAll(from)
    }
}
