package com.aiclassroom.classroom.store

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.json.*

/**
 * 领域对象与 JSON 之间的映射。
 *
 * 每个字段都显式读写，不用反射：schema 变更时这里会编译报错，
 * 比运行时才发现存档读不出来要好。
 * 读取一律走「缺失即默认」的宽容策略，旧存档不会因为新增字段而打不开。
 */
object StoreCodec {

    // ---------- 项目 ----------

    fun project(p: ClassroomProject): Json = Json.obj(
        "id" to Json.of(p.id.value),
        "title" to Json.of(p.title),
        "type" to Json.of(p.type.name),
        "lifecycle" to Json.of(p.lifecycle.name),
        "currentRunId" to Json.of(p.currentRunId?.value),
        "specVersion" to Json.of(p.specVersion),
        "outlineVersion" to Json.of(p.outlineVersion),
        "workVersion" to Json.of(p.workVersion),
        "createdAt" to Json.of(p.createdAt),
        "updatedAt" to Json.of(p.updatedAt),
        "sourceConversationId" to Json.of(p.sourceConversationId),
        "sourceDisplayNameSnapshot" to Json.of(p.sourceDisplayNameSnapshot),
        "binding" to Json.of(p.binding.name),
    )

    fun project(j: Json): ClassroomProject {
        val o = j.asObject()
        return ClassroomProject(
            id = ProjectId(o["id"].asString() ?: ""),
            title = o["title"].asString() ?: "",
            type = enumOr(o["type"].asString(), ProjectType.NOVEL),
            lifecycle = enumOr(o["lifecycle"].asString(), ProjectLifecycle.DRAFT),
            currentRunId = o["currentRunId"].asString()?.let { RunId(it) },
            specVersion = o["specVersion"].asInt() ?: 0,
            outlineVersion = o["outlineVersion"].asInt() ?: 0,
            workVersion = o["workVersion"].asInt() ?: 0,
            createdAt = o["createdAt"].asLong() ?: 0,
            updatedAt = o["updatedAt"].asLong() ?: 0,
            sourceConversationId = o["sourceConversationId"].asString(),
            sourceDisplayNameSnapshot = o["sourceDisplayNameSnapshot"].asString(),
            binding = enumOr(o["binding"].asString(), BindingStatus.UNBOUND),
        )
    }

    // ---------- 单元 ----------

    fun unit(u: ClassroomUnit): Json = Json.obj(
        "id" to Json.of(u.id.value),
        "projectId" to Json.of(u.projectId.value),
        "index" to Json.of(u.index),
        "type" to Json.of(u.type.name),
        "chapter" to Json.of(u.chapter),
        "goal" to Json.of(u.goal),
        "dependsOn" to Json.arr(u.dependsOn.map { Json.of(it.value) }),
        "requiredCharacters" to Json.arr(u.requiredCharacters.map { Json.of(it.value) }),
        "completionCriteria" to Json.arr(u.completionCriteria.map { Json.of(it) }),
        "inputStateVersion" to Json.of(u.inputStateVersion),
        "status" to Json.of(u.status.name),
        "currentRevisionId" to Json.of(u.currentRevisionId?.value),
        "officialVersion" to Json.of(u.officialVersion),
        "directive" to (u.directive?.let { d ->
            Json.obj(
                "goalOverride" to Json.of(d.goalOverride),
                "notes" to Json.of(d.notes),
                "updatedAt" to Json.of(d.updatedAt),
            )
        } ?: Json.Null),
    )

    fun unit(j: Json): ClassroomUnit {
        val o = j.asObject()
        return ClassroomUnit(
            id = UnitId(o["id"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            index = o["index"].asInt() ?: 0,
            type = enumOr(o["type"].asString(), ProjectType.NOVEL),
            chapter = o["chapter"].asString() ?: "",
            goal = o["goal"].asString() ?: "",
            dependsOn = o["dependsOn"].asArray().mapNotNull { it.asString()?.let(::UnitId) },
            requiredCharacters = o["requiredCharacters"].asArray().mapNotNull { it.asString()?.let(::CharacterId) },
            completionCriteria = o["completionCriteria"].asArray().mapNotNull { it.asString() },
            inputStateVersion = o["inputStateVersion"].asInt() ?: 0,
            status = enumOr(o["status"].asString(), UnitStatus.PLANNED),
            currentRevisionId = o["currentRevisionId"].asString()?.let { RevisionId(it) },
            officialVersion = o["officialVersion"].asInt() ?: 0,
            directive = o["directive"].takeIf { it !is Json.Null }?.let { d ->
                val m = d.asObject()
                UnitDirective(
                    goalOverride = m["goalOverride"].asString(),
                    notes = m["notes"].asString() ?: "",
                    updatedAt = m["updatedAt"].asLong() ?: 0,
                )
            },
        )
    }

    // ---------- 版本 ----------

    fun revision(r: UnitRevision): Json = Json.obj(
        "id" to Json.of(r.id.value),
        "unitId" to Json.of(r.unitId.value),
        "projectId" to Json.of(r.projectId.value),
        "version" to Json.of(r.version),
        "draftText" to Json.of(r.draftText),
        // 正文块必须落库：不存的话阅读器只能读原始 JSON 草稿。
        "content" to (r.content?.let(ContentCodec::encode) ?: Json.Null),
        "review" to (r.review?.let(::review) ?: Json.Null),
        "attemptId" to Json.of(r.attemptId?.value),
        "modelName" to Json.of(r.modelName),
        "promptTemplateVersion" to Json.of(r.promptTemplateVersion),
        "createdAt" to Json.of(r.createdAt),
        "committedAt" to (r.committedAt?.let { Json.of(it) } ?: Json.Null),
        "sourceHash" to Json.of(r.sourceHash),
        "supersededBy" to Json.of(r.supersededBy?.value),
    )

    fun revision(j: Json): UnitRevision {
        val o = j.asObject()
        return UnitRevision(
            id = RevisionId(o["id"].asString() ?: ""),
            unitId = UnitId(o["unitId"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            version = o["version"].asInt() ?: 0,
            draftText = o["draftText"].asString() ?: "",
            content = ContentCodec.decode(o["content"]),
            review = o["review"].takeIf { it !is Json.Null }?.let(::review),
            attemptId = o["attemptId"].asString()?.let { AttemptId(it) },
            modelName = o["modelName"].asString(),
            promptTemplateVersion = o["promptTemplateVersion"].asInt() ?: 1,
            createdAt = o["createdAt"].asLong() ?: 0,
            committedAt = o["committedAt"].asLong(),
            sourceHash = o["sourceHash"].asString() ?: "",
            supersededBy = o["supersededBy"].asString()?.let { RevisionId(it) },
        )
    }

    private fun review(r: ReviewReport): Json = Json.obj(
        "reviewedAt" to Json.of(r.reviewedAt),
        "templateVersion" to Json.of(r.templateVersion),
        "issues" to Json.arr(
            r.issues.map { i ->
                Json.obj(
                    "id" to Json.of(i.id),
                    "severity" to Json.of(i.severity.name),
                    "revisionId" to Json.of(i.revisionId.value),
                    "blockId" to Json.of(i.blockId),
                    "rule" to Json.of(i.rule),
                    "evidence" to Json.of(i.evidence),
                    "suggestedScope" to Json.of(i.suggestedScope),
                    "blocking" to Json.of(i.blocking),
                )
            },
        ),
    )

    private fun review(j: Json): ReviewReport {
        val o = j.asObject()
        return ReviewReport(
            issues = o["issues"].asArray().map { item ->
                val m = item.asObject()
                ReviewIssue(
                    id = m["id"].asString() ?: "",
                    severity = enumOr(m["severity"].asString(), ReviewIssue.Severity.WARN),
                    revisionId = RevisionId(m["revisionId"].asString() ?: ""),
                    blockId = m["blockId"].asString(),
                    rule = m["rule"].asString() ?: "",
                    evidence = m["evidence"].asString() ?: "",
                    suggestedScope = m["suggestedScope"].asString(),
                    blocking = m["blocking"].asBool() ?: false,
                )
            },
            reviewedAt = o["reviewedAt"].asLong() ?: 0,
            templateVersion = o["templateVersion"].asInt() ?: 1,
        )
    }

    // ---------- 角色状态 ----------

    fun characterState(s: CharacterState): Json = Json.obj(
        "characterId" to Json.of(s.characterId.value),
        "projectId" to Json.of(s.projectId?.value),
        "version" to Json.of(s.version),
        "emotion" to Json.of(s.emotion),
        "goal" to Json.of(s.goal),
        "stress" to Json.of(s.stress),
        "location" to Json.of(s.location),
        "relationships" to Json.Obj(s.relationships.entries.associate { it.key.value to Json.of(it.value) }),
        "knownFactIds" to Json.arr(s.knownFactIds.map { Json.of(it.value) }),
    )

    fun characterState(j: Json): CharacterState {
        val o = j.asObject()
        return CharacterState(
            characterId = CharacterId(o["characterId"].asString() ?: ""),
            projectId = o["projectId"].asString()?.let(::ProjectId),
            version = o["version"].asInt() ?: 0,
            emotion = o["emotion"].asString() ?: "",
            goal = o["goal"].asString() ?: "",
            stress = o["stress"].asInt() ?: 0,
            location = o["location"].asString() ?: "",
            relationships = o["relationships"].asObject()
                .mapValues { it.value.asString() ?: "" }
                .entries.associate { CharacterId(it.key) to it.value },
            knownFactIds = o["knownFactIds"].asArray().mapNotNull { it.asString()?.let(::FactId) }.toSet(),
        )
    }

    // ---------- 检查点 ----------

    fun checkpoint(c: Checkpoint): Json = Json.obj(
        "runId" to Json.of(c.runId.value),
        "unitId" to Json.of(c.unitId.value),
        "revisionId" to Json.of(c.revisionId.value),
        "workVersion" to Json.of(c.workVersion),
        "stateVersion" to Json.of(c.stateVersion),
        "outlineVersion" to Json.of(c.outlineVersion),
        "committedAt" to Json.of(c.committedAt),
        "checksum" to Json.of(c.checksum),
        "idempotencyKey" to Json.of(c.idempotencyKey),
    )

    fun checkpoint(j: Json): Checkpoint {
        val o = j.asObject()
        return Checkpoint(
            runId = RunId(o["runId"].asString() ?: ""),
            unitId = UnitId(o["unitId"].asString() ?: ""),
            revisionId = RevisionId(o["revisionId"].asString() ?: ""),
            workVersion = o["workVersion"].asInt() ?: 0,
            stateVersion = o["stateVersion"].asInt() ?: 0,
            outlineVersion = o["outlineVersion"].asInt() ?: 0,
            committedAt = o["committedAt"].asLong() ?: 0,
            checksum = o["checksum"].asString() ?: "",
            idempotencyKey = o["idempotencyKey"].asString() ?: "",
        )
    }

    // ---------- 兜底 ----------

    // ---------- 章节摘要 ----------

    fun summary(s: ChapterSummary): Json = Json.obj(
        "projectId" to Json.of(s.projectId.value),
        "unitId" to Json.of(s.unitId.value),
        "revisionId" to Json.of(s.revisionId.value),
        "index" to Json.of(s.index),
        "title" to Json.of(s.title),
        "keyEvents" to Json.arr(s.keyEvents.map { Json.of(it) }),
        "unresolvedItems" to Json.arr(s.unresolvedItems.map { Json.of(it) }),
        "objectivesCovered" to Json.arr(s.objectivesCovered.map { Json.of(it) }),
        "gist" to Json.of(s.gist),
        "createdAt" to Json.of(s.createdAt),
    )

    fun summary(j: Json): ChapterSummary {
        val o = j.asObject()
        return ChapterSummary(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            unitId = UnitId(o["unitId"].asString() ?: ""),
            revisionId = RevisionId(o["revisionId"].asString() ?: ""),
            index = o["index"].asInt() ?: 0,
            title = o["title"].asString() ?: "",
            keyEvents = o["keyEvents"].asArray().mapNotNull { it.asString() },
            unresolvedItems = o["unresolvedItems"].asArray().mapNotNull { it.asString() },
            objectivesCovered = o["objectivesCovered"].asArray().mapNotNull { it.asString() },
            gist = o["gist"].asString() ?: "",
            createdAt = o["createdAt"].asLong() ?: 0,
        )
    }

    // ---------- 作品版本清单 ----------

    fun manifest(m: WorkManifest): Json = Json.obj(
        "projectId" to Json.of(m.projectId.value),
        "workVersion" to Json.of(m.workVersion),
        "specVersion" to Json.of(m.specVersion),
        "outlineVersion" to Json.of(m.outlineVersion),
        "snapshotId" to Json.of(m.snapshotId),
        "createdAt" to Json.of(m.createdAt),
        "entries" to Json.arr(m.entries.map { e ->
            Json.obj(
                "unitId" to Json.of(e.unitId.value),
                "index" to Json.of(e.index),
                "revisionId" to Json.of(e.revisionId.value),
            )
        }),
    )

    fun manifest(j: Json): WorkManifest {
        val o = j.asObject()
        return WorkManifest(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            workVersion = o["workVersion"].asInt() ?: 0,
            specVersion = o["specVersion"].asInt() ?: 0,
            outlineVersion = o["outlineVersion"].asInt() ?: 0,
            snapshotId = o["snapshotId"].asString(),
            createdAt = o["createdAt"].asLong() ?: 0,
            entries = o["entries"].asArray().map { item ->
                val e = item.asObject()
                ManifestEntry(
                    unitId = UnitId(e["unitId"].asString() ?: ""),
                    index = e["index"].asInt() ?: 0,
                    revisionId = RevisionId(e["revisionId"].asString() ?: ""),
                )
            },
        )
    }

    // ---------- 事件 ----------

    fun event(e: ClassroomEvent): Json = Json.obj(
        "eventId" to Json.of(e.eventId),
        "sourceConversationId" to Json.of(e.sourceConversationId),
        "projectId" to Json.of(e.projectId.value),
        "projectTitle" to Json.of(e.projectTitle),
        "runId" to Json.of(e.runId?.value),
        "workVersion" to Json.of(e.workVersion),
        "type" to Json.of(e.type.name),
        "summary" to Json.of(e.summary),
        "detail" to Json.of(e.detail),
        "createdAt" to Json.of(e.createdAt),
        "delivery" to Json.of(e.delivery.name),
    )

    fun event(j: Json): ClassroomEvent {
        val o = j.asObject()
        return ClassroomEvent(
            eventId = o["eventId"].asString() ?: "",
            sourceConversationId = o["sourceConversationId"].asString() ?: "",
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            projectTitle = o["projectTitle"].asString() ?: "",
            runId = o["runId"].asString()?.let(::RunId),
            workVersion = o["workVersion"].asInt() ?: 0,
            type = enumOr(o["type"].asString(), EventType.STARTED),
            summary = o["summary"].asString() ?: "",
            detail = o["detail"].asString() ?: "",
            createdAt = o["createdAt"].asLong() ?: 0,
            delivery = enumOr(o["delivery"].asString(), DeliveryState.PENDING),
        )
    }

    // ---------- 上下文快照 ----------

    fun snapshot(s: ContextSnapshot): Json = Json.obj(
        "id" to Json.of(s.id.value),
        "projectId" to Json.of(s.projectId.value),
        "version" to Json.of(s.version),
        "sourceConversationId" to Json.of(s.sourceConversationId),
        "createdAt" to Json.of(s.createdAt),
        "mentorName" to Json.of(s.mentorName),
        "mentorPrompt" to Json.of(s.mentorPrompt),
        "userAlias" to Json.of(s.userAlias),
        "viewMode" to Json.of(s.viewMode),
        "activeCharacterId" to Json.of(s.activeCharacterId),
        "publicWorldBook" to Json.of(s.publicWorldBook),
        "authorWorldBook" to Json.of(s.authorWorldBook),
        "historyThroughIndex" to Json.of(s.historyThroughIndex),
        "facts" to Json.arr(s.facts.map { f ->
            Json.obj(
                "content" to Json.of(f.content),
                "visibility" to Json.of(f.visibility.name),
                "knownBy" to Json.arr(f.knownBy.map { Json.of(it) }),
                "unknownBy" to Json.arr(f.unknownBy.map { Json.of(it) }),
                "evidence" to Json.of(f.evidence),
            )
        }),
        "characters" to Json.arr(s.characters.map { c ->
            Json.obj(
                "projectCharacterId" to Json.of(c.projectCharacterId.value),
                "sourceCharacterKey" to Json.of(c.sourceCharacterKey),
                "name" to Json.of(c.name),
                "persona" to Json.of(c.persona),
                "speechStyle" to Json.of(c.speechStyle),
                "authorPrivate" to Json.of(c.authorPrivate),
            )
        }),
        "materials" to Json.arr(s.materials.map { m ->
            Json.obj(
                "name" to Json.of(m.name),
                "type" to Json.of(m.type),
                "content" to Json.of(m.content),
                "checksum" to Json.of(m.checksum),
            )
        }),
    )

    fun snapshot(j: Json): ContextSnapshot {
        val o = j.asObject()
        return ContextSnapshot(
            id = SnapshotId(o["id"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            version = o["version"].asInt() ?: 1,
            sourceConversationId = o["sourceConversationId"].asString(),
            createdAt = o["createdAt"].asLong() ?: 0,
            mentorName = o["mentorName"].asString() ?: "",
            mentorPrompt = o["mentorPrompt"].asString() ?: "",
            userAlias = o["userAlias"].asString() ?: "",
            viewMode = o["viewMode"].asString() ?: "author",
            activeCharacterId = o["activeCharacterId"].asString(),
            publicWorldBook = o["publicWorldBook"].asString() ?: "",
            authorWorldBook = o["authorWorldBook"].asString() ?: "",
            historyThroughIndex = o["historyThroughIndex"].asInt() ?: -1,
            // 旧存档只有字符串数组：来历不明的可见性一律按作者私密处理，
            // 宁可少给角色，也不能把作者秘密当公开事实交给人物。
            facts = o["facts"].asArray().map { item ->
                val f = item.asObject()
                SnapshotFact(
                    content = f["content"].asString() ?: "",
                    visibility = enumOr(f["visibility"].asString(), Visibility.AUTHOR_ONLY),
                    knownBy = f["knownBy"].asArray().mapNotNull { it.asString() }.toSet(),
                    unknownBy = f["unknownBy"].asArray().mapNotNull { it.asString() }.toSet(),
                    evidence = f["evidence"].asString() ?: "",
                )
            } + o["establishedFacts"].asArray().mapNotNull { it.asString() }.map {
                SnapshotFact(content = it, visibility = Visibility.AUTHOR_ONLY)
            },
            characters = o["characters"].asArray().map { item ->
                val c = item.asObject()
                SnapshotCharacter(
                    projectCharacterId = CharacterId(c["projectCharacterId"].asString() ?: ""),
                    sourceCharacterKey = c["sourceCharacterKey"].asString() ?: "",
                    name = c["name"].asString() ?: "",
                    persona = c["persona"].asString() ?: "",
                    speechStyle = c["speechStyle"].asString() ?: "",
                    authorPrivate = c["authorPrivate"].asString() ?: "",
                )
            },
            materials = o["materials"].asArray().map { item ->
                val m = item.asObject()
                SnapshotMaterial(
                    name = m["name"].asString() ?: "",
                    type = m["type"].asString() ?: "",
                    content = m["content"].asString() ?: "",
                    checksum = m["checksum"].asString() ?: "",
                )
            },
        )
    }

    // ---------- 人物档案 ----------

    fun character(c: CharacterProfile): Json = Json.obj(
        "id" to Json.of(c.id.value),
        "projectId" to Json.of(c.projectId?.value),
        "name" to Json.of(c.name),
        "identity" to Json.of(c.identity),
        "corePersonality" to Json.of(c.corePersonality),
        "values" to Json.of(c.values),
        "thinkingStyle" to Json.of(c.thinkingStyle),
        "speechStyle" to Json.of(c.speechStyle),
        "behaviorRules" to Json.arr(c.behaviorRules.map { Json.of(it) }),
        "positiveExamples" to Json.arr(c.positiveExamples.map { Json.of(it) }),
        "negativeExamples" to Json.arr(c.negativeExamples.map { Json.of(it) }),
        "authorPrivate" to Json.of(c.authorPrivate),
        "version" to Json.of(c.version),
    )

    fun character(j: Json): CharacterProfile {
        val o = j.asObject()
        return CharacterProfile(
            id = CharacterId(o["id"].asString() ?: ""),
            projectId = o["projectId"].asString()?.let(::ProjectId),
            name = o["name"].asString() ?: "",
            identity = o["identity"].asString() ?: "",
            corePersonality = o["corePersonality"].asString() ?: "",
            values = o["values"].asString() ?: "",
            thinkingStyle = o["thinkingStyle"].asString() ?: "",
            speechStyle = o["speechStyle"].asString() ?: "",
            behaviorRules = o["behaviorRules"].asArray().mapNotNull { it.asString() },
            positiveExamples = o["positiveExamples"].asArray().mapNotNull { it.asString() },
            negativeExamples = o["negativeExamples"].asArray().mapNotNull { it.asString() },
            authorPrivate = o["authorPrivate"].asString() ?: "",
            version = o["version"].asInt() ?: 1,
        )
    }

    // ---------- 事实 ----------

    fun fact(f: FactRevision): Json = Json.obj(
        "factId" to Json.of(f.factId.value),
        "projectId" to Json.of(f.projectId.value),
        "version" to Json.of(f.version),
        "content" to Json.of(f.content),
        "kind" to Json.of(f.kind.name),
        "sourceUnitId" to Json.of(f.sourceUnitId?.value),
        "sourceRevisionId" to Json.of(f.sourceRevisionId?.value),
        "routeKey" to Json.of(f.routeKey),
        "visibility" to Json.of(f.visibility.name),
        "knownBy" to Json.arr(f.knownBy.map { Json.of(it.value) }),
        "unknownBy" to Json.arr(f.unknownBy.map { Json.of(it.value) }),
        "evidence" to Json.of(f.evidence),
        "learnedAtEventId" to Json.of(f.learnedAtEventId),
        "effectiveAt" to Json.of(f.effectiveAt),
        "supersedes" to Json.of(f.supersedes ?: 0),
    )

    fun fact(j: Json): FactRevision {
        val o = j.asObject()
        return FactRevision(
            factId = FactId(o["factId"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            version = o["version"].asInt() ?: 1,
            content = o["content"].asString() ?: "",
            kind = enumOr(o["kind"].asString(), FactKind.FACT),
            sourceUnitId = o["sourceUnitId"].asString()?.let(::UnitId),
            sourceRevisionId = o["sourceRevisionId"].asString()?.let(::RevisionId),
            routeKey = o["routeKey"].asString(),
            visibility = enumOr(o["visibility"].asString(), Visibility.AUTHOR_ONLY),
            knownBy = o["knownBy"].asArray().mapNotNull { it.asString()?.let(::CharacterId) }.toSet(),
            unknownBy = o["unknownBy"].asArray().mapNotNull { it.asString()?.let(::CharacterId) }.toSet(),
            evidence = o["evidence"].asString() ?: "",
            learnedAtEventId = o["learnedAtEventId"].asString(),
            effectiveAt = o["effectiveAt"].asLong() ?: 0,
            supersedes = o["supersedes"].asInt()?.takeIf { it > 0 },
        )
    }

    // ---------- 任务与尝试 ----------

    fun run(r: GenerationRun): Json = Json.obj(
        "id" to Json.of(r.id.value),
        "projectId" to Json.of(r.projectId.value),
        "specVersion" to Json.of(r.specVersion),
        "outlineVersion" to Json.of(r.outlineVersion),
        "phase" to Json.of(r.phase.name),
        "status" to Json.of(r.status.name),
        "currentUnitId" to Json.of(r.currentUnitId?.value),
        "completedUnits" to Json.of(r.completedUnits),
        "totalUnits" to Json.of(r.totalUnits),
        "retryCount" to Json.of(r.retryCount),
        "requests" to Json.of(r.budgetUsed.requests),
        "promptTokens" to Json.of(r.budgetUsed.promptTokens),
        "completionTokens" to Json.of(r.budgetUsed.completionTokens),
        "error" to Json.of(r.error),
        "startedAt" to Json.of(r.startedAt),
        "updatedAt" to Json.of(r.updatedAt),
        "frozenProvider" to Json.of(r.frozenProvider),
        "frozenPrimaryModel" to Json.of(r.frozenPrimaryModel),
        "frozenFallbackModels" to Json.arr(r.frozenFallbackModels.map { Json.of(it) }),
    )

    fun run(j: Json): GenerationRun {
        val o = j.asObject()
        return GenerationRun(
            id = RunId(o["id"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            specVersion = o["specVersion"].asInt() ?: 0,
            outlineVersion = o["outlineVersion"].asInt() ?: 0,
            phase = enumOr(o["phase"].asString(), RunPhase.GENERATING),
            status = enumOr(o["status"].asString(), RunStatus.QUEUED),
            currentUnitId = o["currentUnitId"].asString()?.let(::UnitId),
            completedUnits = o["completedUnits"].asInt() ?: 0,
            totalUnits = o["totalUnits"].asInt() ?: 0,
            retryCount = o["retryCount"].asInt() ?: 0,
            budgetUsed = BudgetUsage(
                requests = o["requests"].asInt() ?: 0,
                promptTokens = o["promptTokens"].asLong() ?: 0,
                completionTokens = o["completionTokens"].asLong() ?: 0,
            ),
            error = o["error"].asString(),
            startedAt = o["startedAt"].asLong() ?: 0,
            updatedAt = o["updatedAt"].asLong() ?: 0,
            frozenProvider = o["frozenProvider"].asString(),
            frozenPrimaryModel = o["frozenPrimaryModel"].asString(),
            frozenFallbackModels = o["frozenFallbackModels"].asArray().mapNotNull { it.asString() },
        )
    }

    fun attempt(a: GenerationAttempt): Json = Json.obj(
        "id" to Json.of(a.id.value),
        "runId" to Json.of(a.runId.value),
        "unitId" to Json.of(a.unitId?.value),
        "phase" to Json.of(a.phase.name),
        "inputHash" to Json.of(a.inputHash),
        "requestedModel" to Json.of(a.requestedModel),
        "actualModel" to Json.of(a.actualModel),
        "finishReason" to Json.of(a.finishReason),
        "transportCompleted" to Json.of(a.transportCompleted),
        "httpStatus" to Json.of(a.httpStatus ?: 0),
        "error" to Json.of(a.error),
        "promptTokens" to Json.of(a.promptTokens ?: -1),
        "completionTokens" to Json.of(a.completionTokens ?: -1),
        "diagnosticRef" to Json.of(a.diagnosticRef),
        "createdAt" to Json.of(a.createdAt),
    )

    fun attempt(j: Json): GenerationAttempt {
        val o = j.asObject()
        return GenerationAttempt(
            id = AttemptId(o["id"].asString() ?: ""),
            runId = RunId(o["runId"].asString() ?: ""),
            unitId = o["unitId"].asString()?.let(::UnitId),
            phase = enumOr(o["phase"].asString(), RunPhase.GENERATING),
            inputHash = o["inputHash"].asString() ?: "",
            requestedModel = o["requestedModel"].asString(),
            actualModel = o["actualModel"].asString(),
            finishReason = o["finishReason"].asString(),
            transportCompleted = o["transportCompleted"].asBool() ?: false,
            httpStatus = o["httpStatus"].asInt()?.takeIf { it > 0 },
            error = o["error"].asString(),
            promptTokens = o["promptTokens"].asLong()?.takeIf { it >= 0 },
            completionTokens = o["completionTokens"].asLong()?.takeIf { it >= 0 },
            diagnosticRef = o["diagnosticRef"].asString(),
            createdAt = o["createdAt"].asLong() ?: 0,
        )
    }

    // ---------- 读者存档 / 进度 ----------

    fun playthrough(s: PlaythroughSave): Json = Json.obj(
        "id" to Json.of(s.id.value),
        "projectId" to Json.of(s.projectId.value),
        "workVersion" to Json.of(s.workVersion),
        "currentNodeId" to Json.of(s.currentNodeId),
        "variables" to Json.Obj(s.variables.entries.associate { (k, v) -> k to galValue(v) }),
        "choiceHistory" to Json.arr(s.choiceHistory.map { Json.of(it) }),
        "seenEndingIds" to Json.arr(s.seenEndingIds.map { Json.of(it) }),
        "updatedAt" to Json.of(s.updatedAt),
    )

    fun playthrough(j: Json): PlaythroughSave {
        val o = j.asObject()
        return PlaythroughSave(
            id = SaveId(o["id"].asString() ?: ""),
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            workVersion = o["workVersion"].asInt() ?: 0,
            currentNodeId = o["currentNodeId"].asString() ?: "",
            variables = o["variables"].asObject().mapNotNull { (k, v) -> galValueOrNull(v)?.let { k to it } }.toMap(),
            choiceHistory = o["choiceHistory"].asArray().mapNotNull { it.asString() },
            seenEndingIds = o["seenEndingIds"].asArray().mapNotNull { it.asString() },
            updatedAt = o["updatedAt"].asLong() ?: 0,
        )
    }

    private fun galValue(v: GalValue): Json = when (v) {
        is GalValue.Bool -> Json.obj("t" to Json.of("b"), "v" to Json.of(v.value))
        is GalValue.Int -> Json.obj("t" to Json.of("i"), "v" to Json.of(v.value))
        is GalValue.Enum -> Json.obj("t" to Json.of("e"), "v" to Json.of(v.value))
    }

    private fun galValueOrNull(j: Json): GalValue? {
        val o = j.asObject()
        return when (o["t"].asString()) {
            "b" -> GalValue.Bool(o["v"].asBool() ?: false)
            "i" -> GalValue.Int(o["v"].asInt() ?: 0)
            "e" -> GalValue.Enum(o["v"].asString() ?: "")
            else -> null
        }
    }

    fun readerProgress(p: ReaderProgress): Json = Json.obj(
        "projectId" to Json.of(p.projectId.value),
        "workVersion" to Json.of(p.workVersion),
        "unitId" to Json.of(p.unitId?.value),
        "offset" to Json.of(p.offset),
        "updatedAt" to Json.of(p.updatedAt),
    )

    fun readerProgress(j: Json): ReaderProgress {
        val o = j.asObject()
        return ReaderProgress(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            workVersion = o["workVersion"].asInt() ?: 0,
            unitId = o["unitId"].asString()?.let(::UnitId),
            offset = o["offset"].asInt() ?: 0,
            updatedAt = o["updatedAt"].asLong() ?: 0,
        )
    }

    fun learningProgress(p: LearningProgress): Json = Json.obj(
        "projectId" to Json.of(p.projectId.value),
        "objectiveId" to Json.of(p.objectiveId),
        "answer" to Json.of(p.answer),
        "graded" to Json.of(p.graded),
        "mastered" to Json.of(p.mastered),
        "evidence" to Json.of(p.evidence),
        "updatedAt" to Json.of(p.updatedAt),
    )

    fun learningProgress(j: Json): LearningProgress {
        val o = j.asObject()
        return LearningProgress(
            projectId = ProjectId(o["projectId"].asString() ?: ""),
            objectiveId = o["objectiveId"].asString() ?: "",
            answer = o["answer"].asString(),
            graded = o["graded"].asBool() ?: false,
            mastered = o["mastered"].asBool() ?: false,
            evidence = o["evidence"].asString() ?: "",
            updatedAt = o["updatedAt"].asLong() ?: 0,
        )
    }

    /** 未知枚举值回退到默认，旧存档遇到新枚举名不会打不开。 */
    inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
