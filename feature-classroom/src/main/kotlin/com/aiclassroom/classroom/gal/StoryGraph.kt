package com.aiclassroom.classroom.gal

import com.aiclassroom.classroom.domain.*

/** GAL 的一个节点：一段内容 + 出口。 */
data class StoryNode(
    val id: String,
    val title: String,
    /** 展示的内容块。 */
    val blocks: List<ContentBlock>,
    /** 无选项时为直线出口；结局节点为 null。 */
    val nextId: String? = null,
    /** 有选项时走这里。 */
    val choices: List<ChoiceOption> = emptyList(),
    /** 结局节点的名字；null 表示不是结局。 */
    val endingId: String? = null,
)

/** 变量声明：类型 + 可选枚举取值集合。 */
data class VarSpec(
    val name: String,
    val kind: GalEvaluator.VarKind,
    val options: List<String> = emptyList(),
)

data class StoryGraph(
    val nodes: List<StoryNode>,
    val variables: List<VarSpec>,
    val startId: String,
) {
    fun node(id: String): StoryNode? = nodes.firstOrNull { it.id == id }
    fun variable(name: String): VarSpec? = variables.firstOrNull { it.name == name }
}

data class GraphProblem(val code: String, val message: String, val nodeId: String? = null)

/**
 * 图结构校验。
 *
 * 这些必须由程序做：选项指向不存在的节点、变量拼错、走不到结局，
 * 都是「读者点进去才发现」的问题，不能靠审查模型自觉。
 */
object StoryGraphValidator {

    fun validate(graph: StoryGraph): List<GraphProblem> {
        val problems = mutableListOf<GraphProblem>()
        val decls = graph.variables.associate { it.name to it.kind }

        if (graph.nodes.isEmpty()) {
            problems += GraphProblem("empty", "图里没有任何节点")
            return problems
        }
        if (graph.node(graph.startId) == null) {
            problems += GraphProblem("bad-start", "起始节点不存在：" + graph.startId)
        }

        graph.nodes.groupBy { it.id }.filterValues { it.size > 1 }.forEach { (id, _) ->
            problems += GraphProblem("duplicate-node", "节点 id 重复：" + id, id)
        }

        for (node in graph.nodes) {
            // 出口必须存在
            node.nextId?.let { next ->
                if (graph.node(next) == null) {
                    problems += GraphProblem("dangling", "节点指向不存在的下一节点：" + next, node.id)
                }
            }
            // 既没有出口也没有选项也不是结局 = 死路
            if (node.nextId == null && node.choices.isEmpty() && node.endingId == null) {
                problems += GraphProblem("dead-end", "既不是结局也没有出口", node.id)
            }

            for (option in node.choices) {
                if (graph.node(option.targetNodeId) == null) {
                    problems += GraphProblem("dangling-choice", "选项指向不存在的节点：" + option.targetNodeId, node.id)
                }
                option.condition?.let { cond ->
                    GalEvaluator.validate(cond, decls).forEach { msg ->
                        problems += GraphProblem("bad-condition", msg, node.id)
                    }
                }
                for (effect in option.effects) {
                    val spec = when (effect) {
                        is Effect.Set -> graph.variable(effect.variable)
                        is Effect.Increment -> graph.variable(effect.variable)
                    }
                    if (spec == null) {
                        problems += GraphProblem("unknown-var", "选项改动了未声明的变量", node.id)
                    } else if (effect is Effect.Increment && spec.kind != GalEvaluator.VarKind.INT) {
                        problems += GraphProblem("bad-increment", "只有整数变量可以自增：" + spec.name, node.id)
                    }
                }
            }
        }

        // 可达性：从起点走不到的节点，多半是写断的支线。
        val reachable = reachableFrom(graph, graph.startId)
        graph.nodes.filterNot { it.id in reachable }.forEach { node ->
            problems += GraphProblem("unreachable", "从起点走不到这个节点", node.id)
        }

        // 必须至少有一个可达结局，否则读者永远读不完。
        val reachableEndings = graph.nodes.filter { it.id in reachable && it.endingId != null }
        if (reachableEndings.isEmpty()) {
            problems += GraphProblem("no-ending", "没有任何可达结局")
        }

        return problems
    }

    fun reachableFrom(graph: StoryGraph, startId: String): Set<String> {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(startId))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            val node = graph.node(id) ?: continue
            node.nextId?.let { queue.addLast(it) }
            node.choices.forEach { queue.addLast(it.targetNodeId) }
        }
        return seen
    }
}
