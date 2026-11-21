package com.aiclassroom.classroom.content

import com.aiclassroom.classroom.domain.ContentBlock

/**
 * 正文字数口径 —— 全项目唯一入口。
 *
 * 只数叙述与对白里的**非空白、非标点**码点：
 * 标题、说话者标签、JSON、事件、摘要都不算。累积与验收都调用这里，
 * 不接受模型自报字数，也不接受调用方传入的 chars。
 */
object TextCount {

    fun units(blocks: List<ContentBlock>): Int {
        var total = 0
        for (block in blocks) total += units(block)
        return total
    }

    fun units(block: ContentBlock): Int = when (block) {
        is ContentBlock.Narration -> count(block.text)
        is ContentBlock.Dialogue -> count(block.text)
        // 标题、讲解、例题、练习、选项都不是正文。
        else -> 0
    }

    fun count(text: String): Int {
        var n = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!Character.isWhitespace(cp) && !isPunctuation(cp)) n++
            i += Character.charCount(cp)
        }
        return n
    }

    /** 接受下限：默认目标的 90%（向上取整）；用户写了「至少 N 字」就用 N。 */
    fun acceptMin(target: Int, atLeast: Int? = null): Int =
        atLeast ?: ((target * 90 + 99) / 100)

    /** 接受上限：目标的 120%（向下取整）。超过就停止扩写，不丢段落凑数字。 */
    fun acceptMax(target: Int): Int = target * 120 / 100

    /** 标点判定按 Unicode 一般类别 P*，覆盖中英文标点与引号。 */
    private fun isPunctuation(cp: Int): Boolean {
        val type = Character.getType(cp).toByte()
        return type == Character.CONNECTOR_PUNCTUATION ||
            type == Character.DASH_PUNCTUATION ||
            type == Character.START_PUNCTUATION ||
            type == Character.END_PUNCTUATION ||
            type == Character.INITIAL_QUOTE_PUNCTUATION ||
            type == Character.FINAL_QUOTE_PUNCTUATION ||
            type == Character.OTHER_PUNCTUATION
    }
}
