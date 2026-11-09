package com.aiclassroom.feature.plugin.ui

/**
 * 5x7 点阵字模。纯数据、无 Android 依赖，渲染器与单测共用同一张表。
 *
 * 只认这 11 个字符（0-9 与冒号）——时钟要的就是它们。
 * 其它字符（字母、汉字、空格以外的符号）一律返回 null，渲染器跳过并记一次日志，绝不崩。
 */
object PixelFont {

    /** 每个字模的列数。 */
    const val COLS = 5

    /** 每个字模的行数。 */
    const val ROWS = 7

    /** 字模表：7 行 x 5 列，'1' 表示这一格有像素点。 */
    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
        // 题面给的表只有 0-9 与冒号；日期里的分隔符 "." 是这张表唯一必要的补充
        // （否则 "2026.11.09" 会渲染成一串带空档的数字）。其余字符仍然一律跳过。
        '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    )

    /** 这张表支持的字符（渲染器据此判断「要不要记一条不支持字符的日志」）。 */
    val SUPPORTED: Set<Char> = GLYPHS.keys

    /** 取某字符的字模；不支持的字符返回 null。 */
    fun glyphOf(ch: Char): List<String>? = GLYPHS[ch]

    fun isSupported(ch: Char): Boolean = GLYPHS.containsKey(ch)

    /** 一行点阵按列取布尔像素：越界坐标一律 false，画的时候不用再判边界。 */
    fun pixelAt(glyph: List<String>, row: Int, col: Int): Boolean {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return false
        val line = glyph.getOrNull(row) ?: return false
        return col < line.length && line[col] == '1'
    }

    /** 文本里所有不被支持的字符（去重、保持出现顺序），用于「只记一次日志」。 */
    fun unsupportedChars(text: String): List<Char> {
        val result = LinkedHashSet<Char>()
        for (ch in text) if (!isSupported(ch)) result += ch
        return result.toList()
    }
}
