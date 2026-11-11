package com.aiclassroom.app.web

import java.util.Locale

/**
 * web_act 的判定核心：动作合法性、密码框、敏感操作。
 *
 * 故意做成纯函数（不碰 WebView、不碰网络），这样「密码不许填」「submit 要确认」
 * 这些拦截能在打开页面前就完成——既快，也不会因为先把页面点坏了再报错。
 * 页面加载完之后还会拿真实 DOM 再问一次（很多按钮的文案只有渲染后才知道），
 * 两道判定叠加，任一道命中都拦。
 */
object WebGuard {

    /** 用户定的硬清单：命中这些词就属于「要花他的钱 / 动他的账号 / 删他的东西」。 */
    val SENSITIVE_KEYWORDS: List<String> = listOf(
        "pay", "payment", "checkout", "order", "submit", "delete", "remove",
        "confirm", "transfer", "withdraw",
        "密码", "支付", "付款", "下单", "确认支付", "删除", "转账",
    )

    /** selector / name / id 里出现这些片段，就当它是密码框——宁可不填。 */
    private val PASSWORD_HINTS: List<String> = listOf(
        "password", "passwd", "pwd", "pass-word", "密码", "口令",
    )

    const val MAX_ACTIONS = 10
    const val MAX_TOTAL_WAIT_MS = 30_000L
    const val MAX_SINGLE_WAIT_MS = 10_000L

    data class Action(
        val type: String,
        val selector: String,
        val value: String,
        val ms: Long,
        /** type=js 时要执行的脚本；其余动作忽略。 */
        val script: String = "",
    )

    sealed class Check {
        object Pass : Check()
        data class Block(val reason: String) : Check()
        data class NeedsConfirm(val reason: String) : Check()
    }

    /** 命中任一敏感关键词就返回命中的那个词，方便原样告诉用户「因为出现了『支付』」。 */
    fun sensitiveHit(text: String): String? {
        if (text.isBlank()) return null
        val lowered = text.lowercase(Locale.ROOT)
        return SENSITIVE_KEYWORDS.firstOrNull { lowered.contains(it.lowercase(Locale.ROOT)) }
    }

    /** selector 是否像密码框。 */
    fun looksLikePassword(selector: String): Boolean {
        val lowered = selector.lowercase(Locale.ROOT)
        return PASSWORD_HINTS.any { lowered.contains(it) }
    }

    /**
     * 动作结构校验。返回 null 表示合法，否则返回可直接回填给模型的错误说明。
     */
    fun validateActions(actions: List<Action>): String? {
        if (actions.isEmpty()) return "actions 不能为空：至少给一个 fill / click / wait 动作。"
        if (actions.size > MAX_ACTIONS) {
            return "一次最多 " + MAX_ACTIONS + " 个动作，收到 " + actions.size + " 个。请拆成多次调用，并在每次之前确认页面对不对。"
        }
        var totalWait = 0L
        actions.forEachIndexed { index, action ->
            val at = "第 " + (index + 1) + " 个动作"
            when (action.type.lowercase(Locale.ROOT)) {
                "fill" -> {
                    if (action.selector.isBlank()) return at + "（fill）缺少 selector。"
                    if (action.value.isEmpty()) return at + "（fill）缺少 value。"
                }
                "click" -> {
                    if (action.selector.isBlank()) return at + "（click）缺少 selector。"
                }
                "wait" -> {
                    if (action.ms <= 0) return at + "（wait）缺少 ms。"
                    if (action.ms > MAX_SINGLE_WAIT_MS) {
                        return at + "（wait）单次等待上限 " + MAX_SINGLE_WAIT_MS + " 毫秒，收到 " + action.ms + "。"
                    }
                    totalWait += action.ms
                }
                else -> return at + " 的类型是「" + action.type + "」，只支持 fill / click / wait 三种；" +
                    "不接受裸 JavaScript 或其它动作。"
            }
        }
        if (totalWait > MAX_TOTAL_WAIT_MS) {
            return "所有 wait 加起来 " + totalWait + " 毫秒，超过单次调用 " + MAX_TOTAL_WAIT_MS + " 毫秒的上限。"
        }
        return null
    }

    /**
     * 点开页面之前的第一道判定：只看 AI 传进来的结构，不需要网络。
     * 优先判密码（硬拒绝），再判敏感操作（要确认）。
     */
    fun preflight(actions: List<Action>): Check {
        validateActions(actions)?.let { return Check.Block(it) }
        actions.forEachIndexed { index, action ->
            val at = "第 " + (index + 1) + " 个动作"
            if (action.type.equals("fill", true) && looksLikePassword(action.selector)) {
                return Check.Block(
                    "拒绝填写密码框（" + at + "，selector=" + action.selector + "）。" +
                        "密码必须由用户自己输入，AI 只负责指出位置。" +
                        "请先截图告诉用户密码框在哪，然后停下来等他自己填。"
                )
            }
        }
        actions.forEachIndexed { index, action ->
            val at = "第 " + (index + 1) + " 个动作"
            val hit = sensitiveHit(action.selector + " " + action.value)
            if (hit != null) {
                return Check.NeedsConfirm(
                    at + "（" + action.type + "）命中敏感关键词「" + hit + "」，selector=" + action.selector +
                        "。这类操作可能涉及支付、下单、删除或账号安全，必须先向用户说明并请求确认。"
                )
            }
        }
        return Check.Pass
    }

    /**
     * 页面加载后的第二道判定：拿真实 DOM（按钮文案、表单文案、表单 action）再查一遍。
     * [probed] 里每一项是 WebScripts.probe 的一行。
     */
    fun inspectProbe(probed: List<Map<String, String>>): Check {
        for (item in probed) {
            val haystack = listOf(
                item["text"].orEmpty(),
                item["label"].orEmpty(),
                item["formText"].orEmpty(),
                item["formAction"].orEmpty(),
            ).joinToString(" ")
            val hit = sensitiveHit(haystack) ?: continue
            return Check.NeedsConfirm(
                "页面上「" + item["selector"].orEmpty() + "」附近的文案命中敏感关键词「" + hit + "」" +
                    "（原文片段：" + haystack.replace(Regex("\\s+"), " ").take(80) + "）。" +
                    "这可能是支付 / 下单 / 删除类操作，必须先向用户说明并请求确认。"
            )
        }
        return Check.Pass
    }
}
