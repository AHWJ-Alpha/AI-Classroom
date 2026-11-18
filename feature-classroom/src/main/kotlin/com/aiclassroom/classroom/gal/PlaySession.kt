package com.aiclassroom.classroom.gal

import com.aiclassroom.classroom.domain.*

/**
 * 本地游玩会话。
 *
 * 关键约束：**游玩不调用模型，也不改作品版本**。
 * 读者的选择只写进自己的存档（变量、当前节点、选择历史、已见结局），
 * 两份存档从同一个作品出发，互不影响。
 */
class PlaySession(private val graph: StoryGraph) {

    fun start(projectId: ProjectId, workVersion: Int, now: EpochMillis): PlaythroughSave {
        val initial = mutableMapOf<String, GalValue>()
        for (spec in graph.variables) {
            initial[spec.name] = when (spec.kind) {
                GalEvaluator.VarKind.BOOL -> GalValue.Bool(false)
                GalEvaluator.VarKind.INT -> GalValue.Int(0)
                GalEvaluator.VarKind.ENUM -> GalValue.Enum(spec.options.firstOrNull() ?: "")
            }
        }
        return PlaythroughSave(
            id = SaveId.new(),
            projectId = projectId,
            workVersion = workVersion,
            currentNodeId = graph.startId,
            variables = initial,
            choiceHistory = emptyList(),
            seenEndingIds = emptyList(),
            updatedAt = now,
        )
    }

    fun node(save: PlaythroughSave): StoryNode? = graph.node(save.currentNodeId)

    /** 当前节点里条件成立、可以点的选项。 */
    fun availableChoices(save: PlaythroughSave): List<ChoiceOption> {
        val node = node(save) ?: return emptyList()
        return node.choices.filter { option ->
            val cond = option.condition
            cond == null || GalEvaluator.evaluate(cond, save.variables)
        }
    }

    /** 直线推进；结局或需要选择时返回 null。 */
    fun advance(save: PlaythroughSave, now: EpochMillis): PlaythroughSave? {
        val node = node(save) ?: return null
        val next = node.nextId ?: return null
        val moved = save.copy(currentNodeId = next, updatedAt = now)
        return recordEndingIfAny(moved)
    }

    /** 选中一个选项：应用副作用、记录历史、跳到目标节点。 */
    fun choose(
        save: PlaythroughSave,
        optionId: String,
        now: EpochMillis,
    ): PlaythroughSave? {
        val option = availableChoices(save).firstOrNull { it.id == optionId } ?: return null
        var vars = save.variables
        option.effects.forEach { effect -> vars = GalEvaluator.apply(effect, vars) }
        val moved = save.copy(
            currentNodeId = option.targetNodeId,
            variables = vars,
            choiceHistory = save.choiceHistory + optionId,
            updatedAt = now,
        )
        return recordEndingIfAny(moved)
    }

    private fun recordEndingIfAny(save: PlaythroughSave): PlaythroughSave {
        val ending = graph.node(save.currentNodeId)?.endingId ?: return save
        if (ending in save.seenEndingIds) return save
        return save.copy(seenEndingIds = save.seenEndingIds + ending)
    }
}
