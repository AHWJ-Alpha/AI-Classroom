package com.aiclassroom.classroom.domain

/**
 * GAL 运行时的本地求值器。
 *
 * 作品内容是模型生成的，所以这里**只解释受限 AST**，
 * 绝不 eval 任意脚本：变量只有 Bool / Int / Enum 三种类型。
 */
object GalEvaluator {

    /** 变量声明表：变量名 -> 类型标签，用于校验，防止运行时出现未声明变量。 */
    data class VarDecl(val name: String, val kind: VarKind)

    enum class VarKind { BOOL, INT, ENUM }

    /**
     * 求值。变量缺失时按类型给保守默认值（false / 0 / ""），
     * 并**不抛异常** —— 一个坏条件不应该让整部作品打不开。
     */
    fun evaluate(condition: Condition, vars: Map<String, GalValue>): Boolean = when (condition) {
        is Condition.Equals -> {
            val left = vars[condition.variable]
            left != null && left == condition.value
        }
        is Condition.GreaterOrEqual -> {
            val left = vars[condition.variable]
            if (left is GalValue.Int && condition.value is GalValue.Int) {
                left.value >= condition.value.value
            } else {
                false
            }
        }
        is Condition.And -> condition.items.all { evaluate(it, vars) }
        is Condition.Or -> condition.items.any { evaluate(it, vars) }
        is Condition.Not -> !evaluate(condition.item, vars)
    }

    /**
     * 应用副作用。只处理白名单操作；类型不匹配时**保持原值**并返回，
     * 由调用方记录一条诊断，而不是把存档写成非法状态。
     */
    fun apply(effect: Effect, vars: Map<String, GalValue>): Map<String, GalValue> = when (effect) {
        is Effect.Set -> vars + (effect.variable to effect.value)
        is Effect.Increment -> {
            val current = vars[effect.variable]
            if (current is GalValue.Int) {
                vars + (effect.variable to GalValue.Int(current.value + effect.delta))
            } else {
                vars
            }
        }
    }

    /**
     * 校验条件里引用的变量都已声明，且类型能对上。
     *
     * 返回问题列表（空表示通过）。这是程序侧检查，
     * 不依赖审查模型的自觉。
     */
    fun validate(condition: Condition, decls: Map<String, VarKind>): List<String> {
        val problems = mutableListOf<String>()
        fun walk(c: Condition) {
            when (c) {
                is Condition.Equals -> {
                    val kind = decls[c.variable]
                    if (kind == null) {
                        problems += "条件引用了未声明变量：" + c.variable
                    } else if (!valueMatches(kind, c.value)) {
                        problems += "变量 " + c.variable + " 的类型与比较值不符"
                    }
                }
                is Condition.GreaterOrEqual -> {
                    val kind = decls[c.variable]
                    if (kind == null) {
                        problems += "条件引用了未声明变量：" + c.variable
                    } else if (kind != VarKind.INT || c.value !is GalValue.Int) {
                        problems += "大小比较只能用于整数变量：" + c.variable
                    }
                }
                is Condition.And -> c.items.forEach(::walk)
                is Condition.Or -> c.items.forEach(::walk)
                is Condition.Not -> walk(c.item)
            }
        }
        walk(condition)
        return problems
    }

    private fun valueMatches(kind: VarKind, value: GalValue): Boolean = when (kind) {
        VarKind.BOOL -> value is GalValue.Bool
        VarKind.INT -> value is GalValue.Int
        VarKind.ENUM -> value is GalValue.Enum
    }
}
