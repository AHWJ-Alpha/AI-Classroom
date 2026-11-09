package com.aiclassroom.feature.plugin.ui

import com.aiclassroom.feature.plugin.readTestResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixeltext 的三块纯逻辑：协议解析、自适应尺寸、字模与不支持字符。
 *
 * 这三块都不依赖 Android —— 渲染器只是把这里算出来的数字画成方块，
 * 所以「横竖屏不崩」这件事可以在 JVM 单测里先证明一遍。
 */
class PixelTextTest {

    private fun errorsOf(json: String): String {
        val result = PluginUiParser.parse(json)
        assertFalse("这份 ui.json 本应解析失败，却通过了", result.ok)
        return result.errors.joinToString("；")
    }

    private fun requireNode(json: String): UiNode {
        val result = PluginUiParser.parse(json)
        assertTrue("本应解析成功，错误：" + result.errors.joinToString("；"), result.ok)
        return requireNotNull(result.root)
    }

    // ---------------------------------------------------------------- 解析

    @Test
    fun `pixeltext 解析出全部可选字段`() {
        val node = requireNode(
            """
            { "type": "pixeltext", "textFrom": "state.time", "pixelSize": 8, "color": "onSurface",
              "align": "center", "scanlines": true, "glow": false,
              "border": "dashed", "borderColor": "outline", "padding": 24 }
            """.trimIndent(),
        )
        assertEquals(UiNodeType.PIXEL_TEXT, node.type)
        assertEquals("state.time", node.textFrom)
        assertEquals(8, node.pixelSize)
        assertEquals(UiColor.ON_SURFACE, node.textColor)
        assertEquals(UiAlign.CENTER, node.align)
        assertEquals(true, node.scanlines)
        assertEquals(false, node.glow)
        assertEquals(UiBorder.DASHED, node.border)
        assertEquals(UiColor.OUTLINE, node.borderColor)
        assertEquals(24, node.padding)
    }

    @Test
    fun `pixeltext 只给 textFrom 就能画 其余全可省`() {
        val node = requireNode("""{ "type": "pixeltext", "textFrom": "state.time" }""")
        assertNull("不给 pixelSize 才是自适应路径", node.pixelSize)
        assertNull(node.border)
        assertNull(node.scanlines)
    }

    @Test
    fun `pixeltext 的 color 是 textColor 的同义写法`() {
        assertEquals(
            UiColor.MUTED,
            requireNode("""{ "type": "pixeltext", "textFrom": "state.date", "textColor": "muted" }""").textColor,
        )
        assertTrue(
            PluginUiParser.parse(
                """{ "type": "pixeltext", "textFrom": "state.date", "color": "muted", "textColor": "muted" }""",
            ).ok,
        )
        assertTrue(
            errorsOf(
                """{ "type": "pixeltext", "textFrom": "state.date", "color": "muted", "textColor": "primary" }""",
            ).contains("color 与 textColor 不一致"),
        )
    }

    @Test
    fun `pixeltext 缺少 textFrom 报错`() {
        assertTrue(errorsOf("""{ "type": "pixeltext" }""").contains("缺少必需字段 textFrom"))
    }

    @Test
    fun `pixeltext 拒绝非法字段与越界取值`() {
        // 拼错的字段不能静默失效
        assertTrue(errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "colr": "muted" }""").contains("colr"))
        // 任意色值不行，只能是语义 token
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "color": "#E8E4D8" }""")
                .contains("root.color"),
        )
        // 像素点大小 0 或负数画不出东西
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "pixelSize": 0 }""")
                .contains("不能小于 1"),
        )
        // 太大就不是像素风了
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "pixelSize": 999 }""")
                .contains("pixelSize 最大"),
        )
        // 未知边框样式
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "border": "solid" }""").contains("border"),
        )
        // 只给 borderColor 不给 border：颜色没有落点
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "borderColor": "outline" }""")
                .contains("必须和 border 一起用"),
        )
        // scanlines 只能是布尔
        assertTrue(
            errorsOf("""{ "type": "pixeltext", "textFrom": "state.time", "scanlines": "yes" }""")
                .contains("scanlines"),
        )
    }

    @Test
    fun `pixeltext 是叶组件 带 children 会被拒`() {
        val errors = errorsOf(
            """{ "type": "pixeltext", "textFrom": "state.time", "children": [ { "type": "text", "text": "x" } ] }""",
        )
        assertTrue(errors, errors.contains("root.children 不是 pixeltext 支持的字段"))
    }

    // ---------------------------------------------------------------- 自适应尺寸

    @Test
    fun `竖屏 1080x2400 的时钟按宽度算出像素点大小`() {
        // 1080 宽、文本 "23:09"（5 个字符 = 5*5 + 4*1 = 29 个像素点），高度给足 1200
        val size = PixelTextMetrics.pixelSizeOf("23:09", maxWidth = 1080f, maxHeight = 1200f)
        assertEquals(1080f / 29f, size, 0.001f)
        // 反过来验证：这个 pixelSize 下文字确实放得进容器
        assertTrue(PixelTextMetrics.widthOf("23:09", size) <= 1080f)
        assertTrue(PixelTextMetrics.heightOf(size) <= 1200f)
    }

    @Test
    fun `横屏 2400x1080 的高度成为约束 但文字仍完整`() {
        // 2400 宽很富余，1080 高、扣掉边框与日期后时钟区约 700 高
        val size = PixelTextMetrics.pixelSizeOf("23:09", maxWidth = 2400f, maxHeight = 700f)
        // 高度约束：700 / 7 / 2.5 = 40
        assertEquals(40f, size, 0.001f)
        assertTrue(PixelTextMetrics.widthOf("23:09", size) <= 2400f)
        assertTrue(PixelTextMetrics.heightOf(size) <= 700f)
    }

    @Test
    fun `窄容器下按宽度收缩 长文本比短文本小`() {
        val short = PixelTextMetrics.pixelSizeOf("23:09", maxWidth = 400f, maxHeight = 800f)
        val long = PixelTextMetrics.pixelSizeOf("2026年11月8日", maxWidth = 400f, maxHeight = 800f)
        assertTrue("字符越多每个点越小：" + short + " / " + long, long < short)
        assertTrue(PixelTextMetrics.widthOf("2026年11月8日", long) <= 400f)
    }

    @Test
    fun `不同容器尺寸都能放得下 不溢出`() {
        val cases = listOf(1080f to 2400f, 2400f to 1080f, 720f to 1280f, 1280f to 720f, 320f to 480f)
        for ((w, h) in cases) {
            for (text in listOf("23:09", "0:00", "23:09:59")) {
                val size = PixelTextMetrics.pixelSizeOf(text, w, h)
                val label = text + " 在 " + w + "x" + h + " 下"
                assertTrue(label + " pixelSize 必须为正", size >= PixelTextMetrics.MIN_PIXEL_SIZE)
                assertTrue(
                    label + " 宽度放不下：" + PixelTextMetrics.widthOf(text, size),
                    PixelTextMetrics.widthOf(text, size) <= w + 0.01f,
                )
                assertTrue(
                    label + " 高度放不下：" + PixelTextMetrics.heightOf(size),
                    PixelTextMetrics.heightOf(size) <= h + 0.01f,
                )
            }
        }
    }

    @Test
    fun `宽度按字符数与字间距累加`() {
        // 1 个字符没有字间距；N 个字符有 N-1 个间距
        assertEquals(PixelFont.COLS, PixelTextMetrics.widthInPoints("8"))
        assertEquals(
            PixelFont.COLS * 5 + PixelTextMetrics.GLYPH_GAP * 4,
            PixelTextMetrics.widthInPoints("23:09"),
        )
        assertEquals(PixelFont.ROWS, PixelTextMetrics.heightInPoints())
        assertEquals(0, PixelTextMetrics.widthInPoints(""))
    }

    @Test
    fun `空文本或退化容器不会算出 0 或崩溃`() {
        assertEquals(PixelTextMetrics.MIN_PIXEL_SIZE, PixelTextMetrics.pixelSizeOf("", 1080f, 2400f), 0.001f)
        assertEquals(PixelTextMetrics.MIN_PIXEL_SIZE, PixelTextMetrics.pixelSizeOf("23:09", 0f, 0f), 0.001f)
    }

    // ---------------------------------------------------------------- 字模 / 不支持字符

    @Test
    fun `字模表覆盖 0-9 与冒号 每格都是 5x7`() {
        for (ch in "0123456789:.") {
            val glyph = PixelFont.glyphOf(ch)
            assertTrue("缺少字模：" + ch, glyph != null)
            assertEquals(PixelFont.ROWS, glyph!!.size)
            assertTrue(
                "行宽必须是 5 且只含 0/1：" + ch,
                glyph.all { line -> line.length == PixelFont.COLS && line.all { it == '0' || it == '1' } },
            )
        }
    }

    @Test
    fun `字母与汉字不被支持 但可以被识别出来记日志`() {
        assertFalse(PixelFont.isSupported('A'))
        assertFalse(PixelFont.isSupported('年'))
        assertNull(PixelFont.glyphOf('A'))

        val unsupported = PixelFont.unsupportedChars("23:09 年 A")
        assertEquals(listOf(' ', '年', 'A'), unsupported)
        // 日期分隔符是表里唯一额外补的字形，日期因此能整行用像素字画
        assertTrue(PixelFont.isSupported('.'))
        assertTrue(PixelFont.unsupportedChars("2026.11.09").isEmpty())
        // 重复字符只报一次
        assertEquals(listOf('年'), PixelFont.unsupportedChars("2026年2026年"))
        assertTrue(PixelFont.unsupportedChars("23:09").isEmpty())
    }

    @Test
    fun `越界坐标取像素不抛异常`() {
        val glyph = requireNotNull(PixelFont.glyphOf('8'))
        assertFalse(PixelFont.pixelAt(glyph, -1, 0))
        assertFalse(PixelFont.pixelAt(glyph, 0, -1))
        assertFalse(PixelFont.pixelAt(glyph, PixelFont.ROWS, 0))
        assertFalse(PixelFont.pixelAt(glyph, 0, PixelFont.COLS))
    }

    @Test
    fun `0 字模首行与末行合上 8 字模中缝存在`() {
        val zero = requireNotNull(PixelFont.glyphOf('0'))
        assertEquals("01110", zero[0])
        assertEquals("01110", zero[6])
        // 8 的中缝是识别度最高的那一行，掉一格就变 0 了
        val eight = requireNotNull(PixelFont.glyphOf('8'))
        assertEquals("01110", eight[3])
    }

    // ---------------------------------------------------------------- 插件的 clock.json

    @Test
    fun `翻页钟的 clock 配置换成像素字与虚线边框`() {
        val root = requireNode(readTestResource("plugins/immersive_flipclock/clock.json"))
        assertEquals(UiNodeType.COLUMN, root.type)
        assertEquals("整屏必须是息屏深底", UiColor.SCREEN, root.background)
        assertEquals(UiLength.Fill, root.width)
        assertEquals(UiLength.Fill, root.height)

        val time = root.children.first { it.type == UiNodeType.PIXEL_TEXT }
        assertEquals("state.time", time.textFrom)
        assertEquals(UiColor.ON_SURFACE, time.textColor)
        assertEquals("时间必须走自适应尺寸", null, time.pixelSize)
        assertEquals(true, time.scanlines)
        assertEquals(UiBorder.DASHED, time.border)
        assertEquals(UiColor.OUTLINE, time.borderColor)

        // 日期也是像素字：5x7 字模只有数字，所以取的是纯数字那一路。
        val date = root.children.filter { it.type == UiNodeType.PIXEL_TEXT }.last()
        assertEquals("state.dateDigits", date.textFrom)
        assertEquals(4, date.pixelSize)

        // 没有任何翻转卡片
        assertEquals(0, root.children.count { it.type == UiNodeType.FLIP_CARD })
    }

    @Test
    fun `沉浸插件不再出现翻页卡与说明文字`() {
        val root = requireNode(readTestResource("plugins/immersive/root.json"))
        val all = mutableListOf<UiNode>()
        fun walk(node: UiNode) {
            all += node
            node.children.forEach(::walk)
        }
        walk(root)
        assertEquals("沉浸插件不该再有 flipcard", 0, all.count { it.type == UiNodeType.FLIP_CARD })
        val clock = all.filter { it.type == UiNodeType.PIXEL_TEXT }
        assertEquals(listOf("state.time", "state.dateDigits"), clock.map { it.textFrom })
        assertEquals("只有时间那块挂虚线边框", listOf(UiBorder.DASHED, null), clock.map { it.border })
        assertTrue("整屏必须有息屏底", all.any { it.background == UiColor.SCREEN })
        // 用户明确要求去掉底部说明文字：任何固定文案都不该出现
        val literals = all.mapNotNull { it.text }
        assertTrue("不该有任何固定文案：" + literals, literals.isEmpty())
    }
}
