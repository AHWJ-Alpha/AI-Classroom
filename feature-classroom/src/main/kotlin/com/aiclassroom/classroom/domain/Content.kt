package com.aiclassroom.classroom.domain

/**
 * 中立的内容块。
 *
 * 不存 Ren'Py 命令，也不依赖任何游戏引擎 —— 阅读器在本地把这些块渲染成文字，
 * 未来的插件可以读同一份结构再导出成别的格式。
 */
sealed interface ContentBlock {
    val id: String

    /** 旁白/叙述。 */
    data class Narration(override val id: String, val text: String) : ContentBlock

    /** 角色台词。speakerId 必须是已声明的角色，不能是自由文本。 */
    data class Dialogue(override val id: String, val speakerId: CharacterId, val text: String) : ContentBlock

    /** 标题/小节。 */
    data class Heading(override val id: String, val text: String, val level: Int = 2) : ContentBlock

    /** 教学：讲解。 */
    data class Explanation(override val id: String, val text: String) : ContentBlock

    /** 教学：示例（可以带代码块）。 */
    data class Example(override val id: String, val text: String, val language: String? = null) : ContentBlock

    /** 教学：练习。答案单独存放，提交作答前不展示。 */
    data class Exercise(
        override val id: String,
        val prompt: String,
        val answer: String,
    ) : ContentBlock

    /** 文字 GAL：选项。 */
    data class Choice(override val id: String, val prompt: String, val options: List<ChoiceOption>) : ContentBlock
}

/** GAL 的一个选项：条件成立时才出现，选中后执行白名单副作用并跳转。 */
data class ChoiceOption(
    val id: String,
    val text: String,
    val condition: Condition? = null,
    val effects: List<Effect> = emptyList(),
    val targetNodeId: String,
)

/** 单元的结构化正文。 */
data class UnitContent(
    val schemaVersion: Int = 1,
    val unitId: UnitId,
    val planVersion: Int,
    val title: String,
    val blocks: List<ContentBlock>,
    /** 本单元覆盖了哪些目标（通常指向 PlannedUnit.goal 的分解项）。 */
    val objectivesCovered: List<String> = emptyList(),
    /** 本单元实际发生的事件，状态补丁必须引用这些事件。 */
    val events: List<UnitEvent> = emptyList(),
    val unresolvedItems: List<String> = emptyList(),
)

/** 已发生事件：状态补丁的证据来源。 */
data class UnitEvent(
    val id: String,
    val sourceBlockIds: List<String>,
    val description: String,
)

/**
 * 受限条件表达式。
 *
 * 只允许这些节点，禁止任意 JS/Python/Kotlin eval —— 作品内容是模型生成的，
 * 不能让它拿到执行能力。
 */
sealed interface Condition {
    data class Equals(val variable: String, val value: GalValue) : Condition
    data class GreaterOrEqual(val variable: String, val value: GalValue) : Condition
    data class And(val items: List<Condition>) : Condition
    data class Or(val items: List<Condition>) : Condition
    data class Not(val item: Condition) : Condition
}

/** GAL 变量只允许这三种类型。 */
sealed interface GalValue {
    data class Bool(val value: Boolean) : GalValue
    data class Int(val value: kotlin.Int) : GalValue
    data class Enum(val value: String) : GalValue
}

/** 白名单副作用操作。 */
sealed interface Effect {
    data class Set(val variable: String, val value: GalValue) : Effect
    data class Increment(val variable: String, val delta: kotlin.Int) : Effect
}


