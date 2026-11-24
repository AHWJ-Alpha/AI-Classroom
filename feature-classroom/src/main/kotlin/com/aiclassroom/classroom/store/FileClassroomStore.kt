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
class FileClassroomStore(
    private val root: File,
    /** 测试用：注入写盘失败，验证事务回滚与深度不被破坏。 */
    private val writeText: (File, String) -> Unit = { file, text -> file.writeText(text) },
) : ClassroomStore {

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
        val manifests: MutableMap<String, WorkManifest> = linkedMapOf(),
        /** 分段草稿：key = projectId#unitId。 */
        val drafts: MutableMap<String, UnitDraft> = linkedMapOf(),
        val summaries: MutableMap<String, ChapterSummary> = linkedMapOf(),
        val stateSnaps: MutableMap<String, UnitStateSnapshot> = linkedMapOf(),
    ) {
        fun snapshot(): State = State(
            LinkedHashMap(projects), LinkedHashMap(specs), LinkedHashMap(outlines),
            LinkedHashMap(units), LinkedHashMap(revisions), LinkedHashMap(facts),
            LinkedHashMap(charStates), LinkedHashMap(chars), LinkedHashMap(runs),
            LinkedHashMap(attempts), LinkedHashMap(checkpoints), LinkedHashMap(reader),
            LinkedHashMap(learning), LinkedHashMap(saves), LinkedHashMap(snapshots),
            LinkedHashMap(events), LinkedHashMap(manifests), LinkedHashMap(drafts),
            LinkedHashMap(summaries), LinkedHashMap(stateSnaps),
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
        val text = runCatching { mainFile.readText() }.getOrNull()
        val parsed = text?.let { runCatching { JsonParser.parse(it) }.getOrNull() }
        if (parsed == null) {
            // 存档读不出来时先留档，再从空状态启动：
            // 直接当空仓库继续写会把唯一副本覆盖掉，用户的正文就真的没了。
            preserveCorruptFile()
            return
        }
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
        state.stateSnaps.clear(); o["stateSnaps"].asArray().forEach {
            val s = StoreCodec.stateSnapshot(it)
            state.stateSnaps[s.unitId.value] = s
        }
        state.summaries.clear(); o["summaries"].asArray().forEach {
            val s = StoreCodec.summary(it)
            state.summaries[s.unitId.value] = s
        }
        state.manifests.clear(); o["manifests"].asArray().forEach {
            val m = StoreCodec.manifest(it)
            state.manifests[m.projectId.value + "#" + m.workVersion] = m
        }
        state.events.clear(); o["events"].asArray().forEach {
            val e = StoreCodec.event(it)
            state.events[e.eventId] = e
        }
        state.snapshots.clear(); o["snapshots"].asArray().forEach {
            val s = StoreCodec.snapshot(it)
            state.snapshots[s.projectId.value + "#" + s.version] = s
        }
        // 分段草稿：旧存档没有这张表，读成空即可。
        state.drafts.clear(); o["drafts"].asArray().forEach {
            val d = StoreCodec.draft(it)
            state.drafts[d.projectId.value + "#" + d.unitId.value] = d
        }
    }

    /** 把读不出来的存档改名保留，供人工恢复；不删除、不覆盖。 */
    private fun preserveCorruptFile() {
        val stamp = System.currentTimeMillis()
        val target = File(root, FILE_NAME + ".corrupt-" + stamp)
        runCatching { mainFile.renameTo(target) }
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
            "manifests" to Json.arr(state.manifests.values.map(StoreCodec::manifest)),
            "summaries" to Json.arr(state.summaries.values.map(StoreCodec::summary)),
            "stateSnaps" to Json.arr(state.stateSnaps.values.map(StoreCodec::stateSnapshot)),
            // 分段草稿必须落盘：万字一节要多次调用，进程一断就只能从头写。
            "drafts" to Json.arr(state.drafts.values.map(StoreCodec::draft)),
        )
        writeText(tmpFile, JsonWriter.write(doc))
        Files.move(
            tmpFile.toPath(), mainFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
    }

    /** 只有最外层事务结束才落盘；嵌套事务交给最外层统一提交。 */
    private fun mutate(block: () -> Unit) {
        if (depth > 0) {
            // 事务内部：交给最外层统一提交。
            block()
            return
        }
        val snapshot = state.snapshot()
        block()
        try {
            persist()
        } catch (t: Throwable) {
            // 落盘失败不能留下「内存改了、磁盘没改」的假成功。
            state = snapshot
            rollbackCount++
            throw t
        }
    }

    @Synchronized
    override fun <T> transaction(block: () -> T): T {
        val snapshot = state.snapshot()
        depth++
        val result: T
        try {
            result = block()
        } catch (t: Throwable) {
            // 只在这里递减一次：以前 persist 失败会走进下面的 catch 再减一次，
            // depth 变成 -1 之后所有写入都不再落盘，现象是「改动看着在、重启就没了」。
            depth--
            state = snapshot
            rollbackCount++
            throw t
        }
        depth--
        if (depth == 0) {
            try {
                persist()
            } catch (t: Throwable) {
                // 落盘失败：内存回到事务前，磁盘仍是上一份完整数据，两边一致。
                state = snapshot
                rollbackCount++
                throw t
            }
        }
        return result
    }

    // ---------- 项目 ----------

    @Synchronized
    override fun createProject(project: ClassroomProject) = mutate { state.projects[project.id.value] = project }
    @Synchronized
    override fun project(id: ProjectId) = state.projects[id.value]
    @Synchronized
    override fun projects() = state.projects.values.toList()
    @Synchronized
    override fun updateProject(project: ClassroomProject) = mutate { state.projects[project.id.value] = project }

    override fun deleteProject(projectId: ProjectId) = mutate {
        val key = projectId.value
        val unitIds = state.units.values.filter { it.projectId == projectId }.map { it.id.value }.toSet()
        state.projects.remove(key)
        state.specs.keys.filter { it.startsWith(key + "#") }.forEach { state.specs.remove(it) }
        state.outlines.keys.filter { it.startsWith(key + "#") }.forEach { state.outlines.remove(it) }
        unitIds.forEach { state.units.remove(it) }
        state.revisions.values.filter { it.projectId == projectId }.map { it.id.value }
            .forEach { state.revisions.remove(it) }
        state.drafts.keys.filter { it.startsWith(key + "#") }.forEach { state.drafts.remove(it) }
        state.summaries.values.filter { it.projectId == projectId }.map { it.unitId.value }
            .forEach { state.summaries.remove(it) }
        state.stateSnaps.values.filter { it.projectId == projectId }.map { it.unitId.value }
            .forEach { state.stateSnaps.remove(it) }
        state.manifests.keys.filter { it.startsWith(key + "#") }.forEach { state.manifests.remove(it) }
        state.facts.keys.filter { it.startsWith(key + "#") }.forEach { state.facts.remove(it) }
        state.chars.keys.filter { it.startsWith(key + "#") }.forEach { state.chars.remove(it) }
        state.charStates.keys.filter { it.startsWith(key + "#") }.forEach { state.charStates.remove(it) }
        state.snapshots.keys.filter { it.startsWith(key + "#") }.forEach { state.snapshots.remove(it) }
        state.runs.values.filter { it.projectId == projectId }.map { it.id.value }
            .forEach { state.runs.remove(it) }
        state.attempts.keys.filter { it.startsWith(key + "#") || unitIds.any { u -> it.contains(u) } }
            .forEach { state.attempts.remove(it) }
        state.checkpoints.values.filter { it.unitId.value in unitIds }.map { it.unitId.value }
            .forEach { state.checkpoints.remove(it) }
        state.reader.remove(key)
        state.saves.values.filter { it.projectId == projectId }.map { it.id.value }
            .forEach { state.saves.remove(it) }
    }

    @Synchronized
    override fun saveSpec(spec: ProjectSpec) = mutate { state.specs[spec.projectId.value + "#" + spec.version] = spec }
    @Synchronized
    override fun spec(id: ProjectId, version: Int) = state.specs[id.value + "#" + version]
    @Synchronized
    override fun latestSpec(id: ProjectId) =
        state.specs.filterKeys { it.startsWith(id.value + "#") }.values.maxByOrNull { it.version }

    @Synchronized
    override fun saveSnapshot(snapshot: ContextSnapshot) = mutate {
        state.snapshots[snapshot.projectId.value + "#" + snapshot.version] = snapshot
    }

    @Synchronized
    override fun latestSnapshot(projectId: ProjectId) =
        state.snapshots.filterKeys { it.startsWith(projectId.value + "#") }.values.maxByOrNull { it.version }

    @Synchronized
    override fun saveOutline(outline: ProjectOutline) = mutate { state.outlines[outline.projectId.value + "#" + outline.version] = outline }
    @Synchronized
    override fun outline(id: ProjectId, version: Int) = state.outlines[id.value + "#" + version]
    @Synchronized
    override fun latestOutline(id: ProjectId) =
        state.outlines.filterKeys { it.startsWith(id.value + "#") }.values.maxByOrNull { it.version }

    @Synchronized
    override fun upsertUnit(unit: ClassroomUnit) = mutate { state.units[unit.id.value] = unit }
    @Synchronized
    override fun unit(unitId: UnitId) = state.units[unitId.value]
    @Synchronized
    override fun units(projectId: ProjectId) =
        state.units.values.filter { it.projectId == projectId }.sortedBy { it.index }

    // ---------- 版本 ----------

    @Synchronized
    override fun saveRevision(revision: UnitRevision) = mutate { state.revisions[revision.id.value] = revision }
    @Synchronized
    override fun revision(revisionId: RevisionId) = state.revisions[revisionId.value]
    @Synchronized
    override fun revisions(unitId: UnitId) =
        state.revisions.values.filter { it.unitId == unitId }.sortedBy { it.version }

    @Synchronized
    override fun officialRevision(unitId: UnitId) =
        state.revisions.values.filter { it.unitId == unitId && it.committedAt != null }.maxByOrNull { it.version }

    // ---------- 事实与角色 ----------

    @Synchronized
    override fun saveFact(fact: FactRevision) = mutate { state.facts[fact.factId.value + "#" + fact.version] = fact }
    @Synchronized
    override fun facts(projectId: ProjectId) = state.facts.values.filter { it.projectId == projectId }
    @Synchronized
    override fun latestFactVersion(factId: FactId, projectId: ProjectId) =
        state.facts.values
            .filter { it.factId == factId && it.projectId == projectId }
            .maxByOrNull { it.version }

    // 角色按「项目#角色」分区：不同项目里同名的角色是两个不同的人，
    // 只按 characterId 存会让 A 项目的状态覆盖 B 项目。
    private fun charKey(projectId: ProjectId?, characterId: CharacterId) =
        (projectId?.value ?: "") + "#" + characterId.value

    @Synchronized
    override fun saveCharacterState(s: CharacterState) = mutate {
        state.charStates[charKey(s.projectId, s.characterId)] = s
    }

    @Synchronized
    override fun characterStates(projectId: ProjectId) =
        state.charStates.values.filter { it.projectId == projectId }

    @Synchronized
    override fun characterState(projectId: ProjectId, characterId: CharacterId) =
        state.charStates[charKey(projectId, characterId)]

    @Synchronized
    override fun saveCharacter(profile: CharacterProfile) = mutate {
        state.chars[charKey(profile.projectId, profile.id)] = profile
    }

    @Synchronized
    override fun characters(projectId: ProjectId) =
        state.chars.values.filter { it.projectId == projectId }

    // ---------- 任务 ----------

    @Synchronized
    override fun saveRun(run: GenerationRun) = mutate { state.runs[run.id.value] = run }
    @Synchronized
    override fun run(runId: RunId) = state.runs[runId.value]
    @Synchronized
    override fun activeRun(projectId: ProjectId) =
        state.runs.values.firstOrNull { it.projectId == projectId && it.status == RunStatus.RUNNING }

    @Synchronized
    override fun runs(projectId: ProjectId) =
        state.runs.values.filter { it.projectId == projectId }

    @Synchronized
    override fun saveAttempt(attempt: GenerationAttempt) = mutate { state.attempts[attempt.id.value] = attempt }
    @Synchronized
    override fun attempts(runId: RunId) = state.attempts.values.filter { it.runId == runId }

    @Synchronized
    override fun saveCheckpoint(checkpoint: Checkpoint) = mutate { state.checkpoints[checkpoint.runId.value] = checkpoint }
    @Synchronized
    override fun checkpoint(runId: RunId) = state.checkpoints[runId.value]

    // ---------- 进度 ----------

    @Synchronized
    override fun saveStateSnapshot(snapshot: UnitStateSnapshot) = mutate {
        state.stateSnaps[snapshot.unitId.value] = snapshot
    }

    override fun stateSnapshots(projectId: ProjectId) =
        state.stateSnaps.values.filter { it.projectId == projectId }.sortedBy { it.unitIndex }

    override fun saveSummary(summary: ChapterSummary) = mutate {
        state.summaries[summary.unitId.value] = summary
    }

    override fun summaries(projectId: ProjectId) =
        state.summaries.values.filter { it.projectId == projectId }.sortedBy { it.index }

    override fun summaryForRevision(revisionId: RevisionId) =
        state.summaries.values.firstOrNull { it.revisionId == revisionId }

    private fun draftKey(projectId: ProjectId, unitId: UnitId) = projectId.value + "#" + unitId.value

    override fun draft(projectId: ProjectId, unitId: UnitId): UnitDraft? =
        state.drafts[draftKey(projectId, unitId)]

    override fun saveDraft(draft: UnitDraft) = mutate {
        state.drafts[draftKey(draft.projectId, draft.unitId)] = draft
    }

    override fun removeDraft(projectId: ProjectId, unitId: UnitId) = mutate {
        state.drafts.remove(draftKey(projectId, unitId))
    }

    override fun saveManifest(manifest: WorkManifest) = mutate {
        state.manifests[manifest.projectId.value + "#" + manifest.workVersion] = manifest
    }

    override fun manifest(projectId: ProjectId, workVersion: Int) =
        state.manifests[projectId.value + "#" + workVersion]

    override fun latestManifest(projectId: ProjectId) =
        state.manifests.filterKeys { it.startsWith(projectId.value + "#") }
            .values.maxByOrNull { it.workVersion }

    override fun saveEvent(event: ClassroomEvent) = mutate { state.events[event.eventId] = event }
    @Synchronized
    override fun event(eventId: String) = state.events[eventId]
    @Synchronized
    override fun pendingEvents(sourceConversationId: String) =
        state.events.values.filter {
            it.sourceConversationId == sourceConversationId && it.delivery == DeliveryState.PENDING
        }.sortedBy { it.createdAt }

    @Synchronized
    override fun saveReaderProgress(progress: ReaderProgress) = mutate { state.reader[progress.projectId.value] = progress }
    @Synchronized
    override fun readerProgress(projectId: ProjectId) = state.reader[projectId.value]

    @Synchronized
    override fun saveLearningProgress(progress: LearningProgress) =
        mutate { state.learning[progress.projectId.value + "#" + progress.objectiveId] = progress }

    @Synchronized
    override fun learningProgress(projectId: ProjectId, objectiveId: String) =
        state.learning[projectId.value + "#" + objectiveId]

    @Synchronized
    override fun savePlaythrough(save: PlaythroughSave) = mutate { state.saves[save.id.value] = save }
    @Synchronized
    override fun playthroughs(projectId: ProjectId) = state.saves.values.filter { it.projectId == projectId }
    @Synchronized
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
        "budget" to (s.budget?.let { Json.obj("maxRequests" to (it.maxRequests?.let(Json::of) ?: Json.Null),
            "maxTokens" to (it.maxTokens?.let(Json::of) ?: Json.Null)) } ?: Json.Null),
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
            budget = o["budget"].takeIf { it is Json.Obj }?.asObject()?.let {
                Budget(it["maxRequests"].asInt(), it["maxTokens"].asLong())
            },
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
        "milestones" to Json.arr(o0.milestones.map { Json.of(it) }),
        "arcs" to Json.Obj(o0.arcs.mapKeys { it.key.value }.mapValues { Json.of(it.value) }),
        "foreshadows" to Json.arr(o0.foreshadows.map { Json.of(it) }),
        "ending" to Json.of(o0.ending),
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
            milestones = o["milestones"].asArray().mapNotNull { it.asString() },
            arcs = o["arcs"].asObject().mapNotNull { (k,v) -> v.asString()?.let { CharacterId(k) to it } }.toMap(),
            foreshadows = o["foreshadows"].asArray().mapNotNull { it.asString() },
            ending = o["ending"].asString().orEmpty(),
        )
    }

    private companion object {
        const val FILE_NAME = "classroom_store.json"
    }
}
