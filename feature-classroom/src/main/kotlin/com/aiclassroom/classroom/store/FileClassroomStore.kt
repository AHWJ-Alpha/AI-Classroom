package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.json.*
import com.aiclassroom.classroom.repository.ClassroomStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 基于单个 JSON 文档的存储实现。
 *
 * 为什么不是每张表一个文件：4.1.0 需要「正文版本、事实、状态、检查点、单元状态」
 * 在**一次提交里全部生效或全部不生效**。单文档 + 原子替换是最容易做对的做法，
 * 代价是整份文档重写 —— 对课堂这种文本量可以接受。
 *
 * 落盘协议：
 * 1. 先写 `store.json.tmp` 并 flush；
 * 2. 用 ATOMIC_MOVE 覆盖 `store.json`；
 * 3. 启动时若只剩 tmp（上次在重命名前崩了），把它当正式文件恢复；
 *    若两者都在，删掉 tmp（孤儿暂存，不留垃圾）。
 *
 * 事务：内存快照 + 失败回滚；只有最外层事务成功才落盘。
 */
class FileClassroomStore(private val root: File) : ClassroomStore {

    private val mainFile = File(root, FILE_NAME)
    private val tmpFile = File(root, FILE_NAME + ".tmp")

    /** 记录发生过多少次回滚，便于诊断与测试。 */
    var rollbackCount = 0
        private set

    private data class State(
        val projects: MutableMap<String, ClassroomProject> = linkedMapOf(),
        val specs: MutableMap<String, ProjectSpec> = linkedMapOf(),
        val outlines: MutableMap<String, ProjectOutline> = linkedMapOf(),
        val units: MutableMap<String, ClassroomUnit> = linkedMapOf(),
        val revisions: MutableMap<String, UnitRevision> = linkedMapOf(),
        val facts: MutableMap<String, FactRevision> = linkedMapOf(),
        val charStates: MutableMap<String, CharacterState> = linkedMapOf(),
        val chars: MutableMap<String, CharacterProfile> = linkedMapOf(),
        val runs: MutableMap<String, GenerationRun> = linkedMapOf(),
        val attempts: MutableMap<String, GenerationAttempt> = linkedMapOf(),
        val checkpoints: MutableMap<String, Checkpoint> = linkedMapOf(),
        val reader: MutableMap<String, ReaderProgress> = linkedMapOf(),
        val learning: MutableMap<String, LearningProgress> = linkedMapOf(),
        val saves: MutableMap<String, PlaythroughSave> = linkedMapOf(),
        val snapshots: MutableMap<String, ContextSnapshot> = linkedMapOf(),
        val events: MutableMap<String, ClassroomEvent> = linkedMapOf(),
    ) {
        fun snapshot(): State = State(
            LinkedHashMap(projects), LinkedHashMap(specs), LinkedHashMap(outlines),
            LinkedHashMap(units), LinkedHashMap(revisions), LinkedHashMap(facts),
            LinkedHashMap(charStates), LinkedHashMap(chars), LinkedHashMap(runs),
            LinkedHashMap(attempts), LinkedHashMap(checkpoints), LinkedHashMap(reader),
            LinkedHashMap(learning), LinkedHashMap(saves), LinkedHashMap(snapshots),
            LinkedHashMap(events),
        )
    }

    private var state = State()
    private var depth = 0

    init {
        root.mkdirs()
        recoverTemp()
        load()
    }

    // ---------- 持久化 ----------

    private fun recoverTemp() {
        if (tmpFile.exists()) {
            if (!mainFile.exists()) {
                // 上次在重命名前崩溃：tmp 就是最后一份完整数据。
                tmpFile.renameTo(mainFile)
            } else {
                // 孤儿暂存，清掉。
                tmpFile.delete()
            }
        }
    }

    private fun load() {
        if (!mainFile.exists()) return
        val parsed = runCatching { JsonParser.parse(mainFile.readText()) }.getOrNull() ?: return
        val o = parsed.asObject()
        state.projects.clear(); o["projects"].asArray().forEach { state.projects[StoreCodec.project(it).id.value] = StoreCodec.project(it) }
        state.specs.clear(); o["specs"].asArray().forEach { val s = specOf(it); state.specs[s.projectId.value + "#" + s.version] = s }
        state.outlines.clear(); o["outlines"].asArray().forEach { val s = outlineOf(it); state.outlines[s.projectId.value + "#" + s.version] = s }
        state.units.clear(); o["units"].asArray().forEach { val u = StoreCodec.unit(it); state.units[u.id.value] = u }
        state.revisions.clear(); o["revisions"].asArray().forEach { val r = StoreCodec.revision(it); state.revisions[r.id.value] = r }
        state.charStates.clear(); o["charStates"].asArray().forEach {
            val c = StoreCodec.characterState(it)
            state.charStates[(c.projectId?.value ?: "") + "#" + c.characterId.value] = c
        }
        state.chars.clear(); o["chars"].asArray().forEach {
            val c = StoreCodec.character(it)
            state.chars[(c.projectId?.value ?: "") + "#" + c.id.value] = c
        }
        state.facts.clear(); o["facts"].asArray().forEach { val f = StoreCodec.fact(it); state.facts[f.factId.value + "#" + f.version] = f }
        state.runs.clear(); o["runs"].asArray().forEach { val r = StoreCodec.run(it); state.runs[r.id.value] = r }
        state.attempts.clear(); o["attempts"].asArray().forEach { val a = StoreCodec.attempt(it); state.attempts[a.id.value] = a }
        state.checkpoints.clear(); o["checkpoints"].asArray().forEach { val c = StoreCodec.checkpoint(it); state.checkpoints[c.runId.value] = c }
        state.saves.clear(); o["saves"].asArray().forEach { val s = StoreCodec.playthrough(it); state.saves[s.id.value] = s }
        state.reader.clear(); o["reader"].asArray().forEach { val p = StoreCodec.readerProgress(it); state.reader[p.projectId.value] = p }
        state.learning.clear(); o["learning"].asArray().forEach { val p = StoreCodec.learningProgress(it); state.learning[p.projectId.value + "#" + p.objectiveId] = p }
        state.events.clear(); o["events"].asArray().forEach {
            val e = StoreCodec.event(it)
            state.events[e.eventId] = e
        }
        state.snapshots.clear(); o["snapshots"].asArray().forEach {
            val s = StoreCodec.snapshot(it)
            state.snapshots[s.projectId.value + "#" + s.version] = s
        }
    }

    private fun persist() {
        val doc = Json.obj(
            "schemaVersion" to Json.of(1),
            "projects" to Json.arr(state.projects.values.map(StoreCodec::project)),
            "specs" to Json.arr(state.specs.values.map(::specJson)),
            "outlines" to Json.arr(state.outlines.values.map(::outlineJson)),
            "units" to Json.arr(state.units.values.map(StoreCodec::unit)),
            "revisions" to Json.arr(state.revisions.values.map(StoreCodec::revision)),
            "charStates" to Json.arr(state.charStates.values.map(StoreCodec::characterState)),
            // 人物档案、事实、任务与尝试也必须落盘：
            // 不写的话重启后人物设定、事实证据、run 历史和诊断全部消失，
            // 而正文和检查点还在 —— 这种「一半持久化」比不持久化更危险。
            "chars" to Json.arr(state.chars.values.map(StoreCodec::character)),
            "facts" to Json.arr(state.facts.values.map(StoreCodec::fact)),
            "runs" to Json.arr(state.runs.values.map(StoreCodec::run)),
            "attempts" to Json.arr(state.attempts.values.map(StoreCodec::attempt)),
            "checkpoints" to Json.arr(state.checkpoints.values.map(StoreCodec::checkpoint)),
            // 读者存档 / 阅读位置 / 学习进度也要落盘：
            // 不写的话 GAL 的选择一重启就没了（实测发现过）。
            "saves" to Json.arr(state.saves.values.map(StoreCodec::playthrough)),
            "reader" to Json.arr(state.reader.values.map(StoreCodec::readerProgress)),
            "learning" to Json.arr(state.learning.values.map(StoreCodec::learningProgress)),
            "snapshots" to Json.arr(state.snapshots.values.map(StoreCodec::snapshot)),
            "events" to Json.arr(state.events.values.map(StoreCodec::event)),
        )
        tmpFile.writeText(JsonWriter.write(doc))
        Files.move(
            tmpFile.toPath(), mainFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
    }

    /** 只有最外层事务结束才落盘；嵌套事务交给最外层统一提交。 */
    private fun mutate(block: () -> Unit) {
        block()
        if (depth == 0) persist()
    }

    override fun <T> transaction(block: () -> T): T {
        val snapshot = state.snapshot()
        depth++
        return try {
            val result = block()
            depth--
            if (depth == 0) persist()
            result
        } catch (t: Throwable) {
            depth--
            state = snapshot
            rollbackCount++
            throw t
        }
    }

    // ---------- 项目 ----------

    override fun createProject(project: ClassroomProject) = mutate { state.projects[project.id.value] = project }
    override fun project(id: ProjectId) = state.projects[id.value]
    override fun projects() = state.projects.values.toList()
    override fun updateProject(project: ClassroomProject) = mutate { state.projects[project.id.value] = project }

    override fun saveSpec(spec: ProjectSpec) = mutate { state.specs[spec.projectId.value + "#" + spec.version] = spec }
    override fun spec(id: ProjectId, version: Int) = state.specs[id.value + "#" + version]
    override fun latestSpec(id: ProjectId) =
        state.specs.filterKeys { it.startsWith(id.value + "#") }.values.maxByOrNull { it.version }

    override fun saveSnapshot(snapshot: ContextSnapshot) = mutate {
        state.snapshots[snapshot.projectId.value + "#" + snapshot.version] = snapshot
    }

    override fun latestSnapshot(projectId: ProjectId) =
        state.snapshots.filterKeys { it.startsWith(projectId.value + "#") }.values.maxByOrNull { it.version }

    override fun saveOutline(outline: ProjectOutline) = mutate { state.outlines[outline.projectId.value + "#" + outline.version] = outline }
    override fun outline(id: ProjectId, version: Int) = state.outlines[id.value + "#" + version]
    override fun latestOutline(id: ProjectId) =
        state.outlines.filterKeys { it.startsWith(id.value + "#") }.values.maxByOrNull { it.version }

    override fun upsertUnit(unit: ClassroomUnit) = mutate { state.units[unit.id.value] = unit }
    override fun unit(unitId: UnitId) = state.units[unitId.value]
    override fun units(projectId: ProjectId) =
        state.units.values.filter { it.projectId == projectId }.sortedBy { it.index }

    // ---------- 版本 ----------

    override fun saveRevision(revision: UnitRevision) = mutate { state.revisions[revision.id.value] = revision }
    override fun revision(revisionId: RevisionId) = state.revisions[revisionId.value]
    override fun revisions(unitId: UnitId) =
        state.revisions.values.filter { it.unitId == unitId }.sortedBy { it.version }

    override fun officialRevision(unitId: UnitId) =
        state.revisions.values.filter { it.unitId == unitId && it.committedAt != null }.maxByOrNull { it.version }

    // ---------- 事实与角色 ----------

    override fun saveFact(fact: FactRevision) = mutate { state.facts[fact.factId.value + "#" + fact.version] = fact }
    override fun facts(projectId: ProjectId) = state.facts.values.filter { it.projectId == projectId }
    override fun latestFactVersion(factId: FactId, projectId: ProjectId) =
        state.facts.filterKeys { it.startsWith(factId.value + "#") }.values.maxByOrNull { it.version }

    // 角色按「项目#角色」分区：不同项目里同名的角色是两个不同的人，
    // 只按 characterId 存会让 A 项目的状态覆盖 B 项目。
    private fun charKey(projectId: ProjectId?, characterId: CharacterId) =
        (projectId?.value ?: "") + "#" + characterId.value

    override fun saveCharacterState(s: CharacterState) = mutate {
        state.charStates[charKey(s.projectId, s.characterId)] = s
    }

    override fun characterStates(projectId: ProjectId) =
        state.charStates.values.filter { it.projectId == projectId }

    override fun characterState(projectId: ProjectId, characterId: CharacterId) =
        state.charStates[charKey(projectId, characterId)]

    override fun saveCharacter(profile: CharacterProfile) = mutate {
        state.chars[charKey(profile.projectId, profile.id)] = profile
    }

    override fun characters(projectId: ProjectId) =
        state.chars.values.filter { it.projectId == projectId }

    // ---------- 任务 ----------

    override fun saveRun(run: GenerationRun) = mutate { state.runs[run.id.value] = run }
    override fun run(runId: RunId) = state.runs[runId.value]
    override fun activeRun(projectId: ProjectId) =
        state.runs.values.firstOrNull { it.projectId == projectId && it.status == RunStatus.RUNNING }

    override fun runs(projectId: ProjectId) =
        state.runs.values.filter { it.projectId == projectId }

    override fun saveAttempt(attempt: GenerationAttempt) = mutate { state.attempts[attempt.id.value] = attempt }
    override fun attempts(runId: RunId) = state.attempts.values.filter { it.runId == runId }

    override fun saveCheckpoint(checkpoint: Checkpoint) = mutate { state.checkpoints[checkpoint.runId.value] = checkpoint }
    override fun checkpoint(runId: RunId) = state.checkpoints[runId.value]

    // ---------- 进度 ----------

    override fun saveEvent(event: ClassroomEvent) = mutate { state.events[event.eventId] = event }
    override fun event(eventId: String) = state.events[eventId]
    override fun pendingEvents(sourceConversationId: String) =
        state.events.values.filter {
            it.sourceConversationId == sourceConversationId && it.delivery == DeliveryState.PENDING
        }.sortedBy { it.createdAt }

    override fun saveReaderProgress(progress: ReaderProgress) = mutate { state.reader[progress.projectId.value] = progress }
    override fun readerProgress(projectId: ProjectId) = state.reader[projectId.value]

    override fun saveLearningProgress(progress: LearningProgress) =
        mutate { state.learning[progress.projectId.value + "#" + progress.objectiveId] = progress }

    override fun learningProgress(projectId: ProjectId, objectiveId: String) =
        state.learning[projectId.value + "#" + objectiveId]

    override fun savePlaythrough(save: PlaythroughSave) = mutate { state.saves[save.id.value] = save }
    override fun playthroughs(projectId: ProjectId) = state.saves.values.filter { it.projectId == projectId }
    override fun playthrough(id: SaveId) = state.saves[id.value]

    // ---------- 规格/总纲的 JSON（字段多，单独放） ----------

    private fun specJson(s: ProjectSpec): Json = Json.obj(
        "projectId" to Json.of(s.projectId.value),
        "version" to Json.of(s.version),
        "rawRequest" to Json.of(s.rawRequest),
        "topic" to Json.of(s.topic),
        "goals" to Json.arr(s.goals.map { Json.of(it) }),
        "audience" to Json.of(s.audience),
        "language" to Json.of(s.language),
        "style" to Json.of(s.style),
        "expectedLength" to Json.of(s.expectedLength),
        "unitCount" to Json.of(s.unitCount),
        "runMode" to Json.of(s.runMode.name),
        "reviewPolicy" to Json.of(s.reviewPolicy.name),
        "hardConstraints" to Json.arr(s.hardConstraints.map { Json.of(it) }),
        "authorRules" to Json.arr(s.authorRules.map { Json.of(it) }),
        "publicWorld" to Json.of(s.publicWorld),
        "characterIds" to Json.arr(s.characterIds.map { Json.of(it.value) }),
        "modelPolicyRef" to Json.of(s.modelPolicyRef),
        "createdAt" to Json.of(s.createdAt),
    )

    private fun specOf(j: Json): ProjectSpec {
        val o = j.asObject()
        return ProjectSpec(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            version = o["version"].asInt() ?: 0,
            rawRequest = o["rawRequest"].asString() ?: "",
            topic = o["topic"].asString() ?: "",
            goals = o["goals"].asArray().mapNotNull { it.asString() },
            audience = o["audience"].asString() ?: "",
            language = o["language"].asString() ?: "zh",
            style = o["style"].asString() ?: "",
            expectedLength = o["expectedLength"].asString() ?: "",
            unitCount = o["unitCount"].asInt() ?: 0,
            runMode = StoreCodec.enumOr(o["runMode"].asString(), RunMode.CONFIRM_OUTLINE),
            reviewPolicy = StoreCodec.enumOr(o["reviewPolicy"].asString(), ReviewPolicy.STANDARD),
            hardConstraints = o["hardConstraints"].asArray().mapNotNull { it.asString() },
            authorRules = o["authorRules"].asArray().mapNotNull { it.asString() },
            publicWorld = o["publicWorld"].asString() ?: "",
            characterIds = o["characterIds"].asArray().mapNotNull { it.asString()?.let(::CharacterId) },
            modelPolicyRef = o["modelPolicyRef"].asString(),
            createdAt = o["createdAt"].asLong() ?: 0,
        )
    }

    private fun outlineJson(o0: ProjectOutline): Json = Json.obj(
        "projectId" to Json.of(o0.projectId.value),
        "version" to Json.of(o0.version),
        "globalGoal" to Json.of(o0.globalGoal),
        "mainLine" to Json.of(o0.mainLine),
        "chapters" to Json.arr(o0.chapters.map { Json.of(it) }),
        "units" to Json.arr(o0.units.map { u ->
            Json.obj(
                "unitId" to Json.of(u.unitId.value),
                "index" to Json.of(u.index),
                "title" to Json.of(u.title),
                "goal" to Json.of(u.goal),
                "dependsOn" to Json.arr(u.dependsOn.map { Json.of(it.value) }),
                "requiredCharacters" to Json.arr(u.requiredCharacters.map { Json.of(it.value) }),
                "completionCriteria" to Json.arr(u.completionCriteria.map { Json.of(it) }),
            )
        }),
        "createdAt" to Json.of(o0.createdAt),
    )

    private fun outlineOf(j: Json): ProjectOutline {
        val o = j.asObject()
        return ProjectOutline(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            version = o["version"].asInt() ?: 0,
            globalGoal = o["globalGoal"].asString() ?: "",
            mainLine = o["mainLine"].asString() ?: "",
            chapters = o["chapters"].asArray().mapNotNull { it.asString() },
            units = o["units"].asArray().map { item ->
                val m = item.asObject()
                PlannedUnit(
                    unitId = UnitId(m["unitId"].asString() ?: ""),
                    index = m["index"].asInt() ?: 0,
                    title = m["title"].asString() ?: "",
                    goal = m["goal"].asString() ?: "",
                    dependsOn = m["dependsOn"].asArray().mapNotNull { it.asString()?.let(::UnitId) },
                    requiredCharacters = m["requiredCharacters"].asArray().mapNotNull { it.asString()?.let(::CharacterId) },
                    completionCriteria = m["completionCriteria"].asArray().mapNotNull { it.asString() },
                )
            },
            createdAt = o["createdAt"].asLong() ?: 0,
        )
    }

    private companion object {
        const val FILE_NAME = "classroom_store.json"
    }
}
