package com.aiclassroom.feature.plugin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三个装饰组件（waveform / disk / glow）的协议测试。
 *
 * 重点不只是「能不能解析」，而是**能不能拒绝**：
 * 这三个组件是唯一一批带数值参数的视觉组件，数值一旦失控（100 根竖线、amplitude=3.0、
 * intensity=1.0）就会从「微微有一点」变成闪屏。所以边界值、非法值、以及
 * 「颜色只许语义 token」这三条，每条都单独一个用例。
 */
class PluginUiDecorationTest {

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

    // ------------------------------------------------------------ waveform

    @Test
    fun `waveform 合法参数全部解析`() {
        val node = requireNode(
            """
            {
              "type": "waveform",
              "width": 200, "height": 48,
              "bars": 24, "color": "primary",
              "amplitude": 0.35, "speed": 1.0,
              "activeFrom": "state.isPlaying"
            }
            """.trimIndent(),
        )
        assertEquals(UiNodeType.WAVEFORM, node.type)
        assertEquals(24, node.bars)
        assertEquals(UiColor.PRIMARY, node.textColor)
        assertEquals(3500, node.amplitudePermyriad)
        assertEquals(PluginUiParser.DEFAULT_SPEED_PERMYRIAD, node.speedPermyriad)
        assertEquals("state.isPlaying", node.activeFrom)
    }

    @Test
    fun `waveform 省略可选参数时用约定的默认值`() {
        val node = requireNode("""{ "type": "waveform", "width": "fill", "height": 48 }""")
        assertEquals(PluginUiParser.DEFAULT_WAVEFORM_BARS, node.bars)
        assertEquals(PluginUiParser.DEFAULT_WAVEFORM_AMPLITUDE_PERMYRIAD, node.amplitudePermyriad)
        assertEquals(PluginUiParser.DEFAULT_SPEED_PERMYRIAD, node.speedPermyriad)
        assertNull(node.textColor)
    }

    @Test
    fun `waveform 竖线条数上限 48 是闭区间`() {
        val ok = requireNode(
            """{ "type": "waveform", "width": 200, "height": 48, "bars": 48 }""",
        )
        assertEquals(PluginUiParser.MAX_WAVEFORM_BARS, ok.bars)

        val message = errorsOf(
            """{ "type": "waveform", "width": 200, "height": 48, "bars": 49 }""",
        )
        assertTrue(message, message.contains(".bars"))
        assertTrue(message, message.contains("48"))
    }

    @Test
    fun `waveform 竖线条数不能是 0 或负数`() {
        assertTrue(errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "bars": 0 }""").contains(".bars"))
        assertTrue(errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "bars": -3 }""").contains(".bars"))
    }

    @Test
    fun `waveform amplitude 必须在 0 到 1 之间`() {
        assertEquals(0, requireNode(
            """{ "type": "waveform", "width": 200, "height": 48, "amplitude": 0 }""",
        ).amplitudePermyriad)
        assertEquals(10000, requireNode(
            """{ "type": "waveform", "width": 200, "height": 48, "amplitude": 1.0 }""",
        ).amplitudePermyriad)

        val tooBig = errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "amplitude": 1.5 }""")
        assertTrue(tooBig, tooBig.contains(".amplitude"))
        val negative = errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "amplitude": -0.2 }""")
        assertTrue(negative, negative.contains(".amplitude"))
    }

    @Test
    fun `waveform speed 有下限也有上限`() {
        // 下限 0.1 倍：再慢就看不出在动。
        assertEquals(PluginUiParser.MIN_SPEED_PERMYRIAD, requireNode(
            """{ "type": "waveform", "width": 200, "height": 48, "speed": 0.1 }""",
        ).speedPermyriad)
        assertEquals(PluginUiParser.MAX_SPEED_PERMYRIAD, requireNode(
            """{ "type": "waveform", "width": 200, "height": 48, "speed": 5.0 }""",
        ).speedPermyriad)

        assertTrue(errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "speed": 0 }""").contains(".speed"))
        assertTrue(errorsOf("""{ "type": "waveform", "width": 200, "height": 48, "speed": 6.0 }""").contains(".speed"))
    }

    @Test
    fun `waveform 的 height 必须给且不能是 fill`() {
        val noHeight = errorsOf("""{ "type": "waveform", "width": 200 }""")
        assertTrue(noHeight, noHeight.contains("height"))
        val fillHeight = errorsOf("""{ "type": "waveform", "width": 200, "height": "fill" }""")
        assertTrue(fillHeight, fillHeight.contains(".height"))
    }

    @Test
    fun `waveform 的 width 必须给且不能是 wrap`() {
        assertTrue(errorsOf("""{ "type": "waveform", "height": 48 }""").contains("width"))
        assertTrue(errorsOf("""{ "type": "waveform", "width": "wrap", "height": 48 }""").contains(".width"))
    }

    @Test
    fun `waveform 不接受任意色值`() {
        val message = errorsOf(
            """{ "type": "waveform", "width": 200, "height": 48, "color": "#FF00FF" }""",
        )
        assertTrue(message, message.contains(".color"))
        assertTrue(message, message.contains("语义 token"))
    }

    // ---------------------------------------------------------------- disk

    @Test
    fun `disk 合法参数全部解析`() {
        val node = requireNode(
            """
            {
              "type": "disk",
              "size": 200,
              "coverFrom": "user:cover",
              "spinningFrom": "state.isPlaying",
              "color": "surfaceVariant"
            }
            """.trimIndent(),
        )
        assertEquals(UiNodeType.DISK, node.type)
        assertEquals(200, node.sizeDp)
        assertEquals("user:cover", node.coverFrom)
        assertEquals("state.isPlaying", node.activeFrom)
        assertEquals(UiColor.SURFACE_VARIANT, node.textColor)
    }

    @Test
    fun `disk 省略 size 时用默认直径`() {
        assertEquals(PluginUiParser.DEFAULT_DISK_SIZE, requireNode("""{ "type": "disk" }""").sizeDp)
    }

    @Test
    fun `disk 尺寸边界`() {
        assertEquals(PluginUiParser.MAX_DECORATION_SIZE, requireNode(
            """{ "type": "disk", "size": 480 }""",
        ).sizeDp)
        assertTrue(errorsOf("""{ "type": "disk", "size": 481 }""").contains(".size"))
        assertTrue(errorsOf("""{ "type": "disk", "size": 4 }""").contains(".size"))
    }

    @Test
    fun `disk 的封面来源必须是包内相对路径或 user 引用`() {
        assertEquals("assets/cover.png", requireNode(
            """{ "type": "disk", "coverFrom": "assets/cover.png" }""",
        ).coverFrom)
        val absolute = errorsOf("""{ "type": "disk", "coverFrom": "/etc/passwd" }""")
        assertTrue(absolute, absolute.contains(".coverFrom"))
        val escape = errorsOf("""{ "type": "disk", "coverFrom": "../../secret.png" }""")
        assertTrue(escape, escape.contains(".coverFrom"))
        val emptyUser = errorsOf("""{ "type": "disk", "coverFrom": "user:" }""")
        assertTrue(emptyUser, emptyUser.contains(".coverFrom"))
    }

    @Test
    fun `disk 宽高不一致或 fill 会被拒绝`() {
        val mismatch = errorsOf("""{ "type": "disk", "width": 100, "height": 200 }""")
        assertTrue(mismatch, mismatch.contains("width"))
        val fill = errorsOf("""{ "type": "disk", "width": "fill", "height": "fill" }""")
        assertTrue(fill, fill.contains("fill"))
        // 宽高一致是合法的：没写 size 时直径就从 width 推出来。
        assertEquals(120, requireNode("""{ "type": "disk", "width": 120, "height": 120 }""").sizeDp)
    }

    @Test
    fun `disk 不接受任意色值`() {
        val message = errorsOf("""{ "type": "disk", "color": "0xFF00FF" }""")
        assertTrue(message, message.contains(".color"))
    }

    // ---------------------------------------------------------------- glow

    @Test
    fun `glow 合法参数全部解析`() {
        val node = requireNode(
            """{ "type": "glow", "radius": 120, "color": "primary", "intensity": 0.25 }""",
        )
        assertEquals(UiNodeType.GLOW, node.type)
        assertEquals(120, node.sizeDp)
        assertEquals(UiColor.PRIMARY, node.textColor)
        assertEquals(2500, node.intensityPermyriad)
        // glow 的 radius 是半径 dp，不是圆角 token。
        assertNull(node.radius)
    }

    @Test
    fun `glow 省略参数时用低强度默认值`() {
        val node = requireNode("""{ "type": "glow" }""")
        assertEquals(PluginUiParser.DEFAULT_GLOW_RADIUS, node.sizeDp)
        assertEquals(PluginUiParser.DEFAULT_GLOW_INTENSITY_PERMYRIAD, node.intensityPermyriad)
    }

    @Test
    fun `glow intensity 超过上限会拒绝`() {
        assertEquals(PluginUiParser.MAX_GLOW_INTENSITY_PERMYRIAD, requireNode(
            """{ "type": "glow", "intensity": 0.6 }""",
        ).intensityPermyriad)
        val tooStrong = errorsOf("""{ "type": "glow", "intensity": 1.0 }""")
        assertTrue(tooStrong, tooStrong.contains(".intensity"))
        val negative = errorsOf("""{ "type": "glow", "intensity": -0.1 }""")
        assertTrue(negative, negative.contains(".intensity"))
    }

    @Test
    fun `glow 的 radius 是 dp 数字`() {
        assertEquals(240, requireNode("""{ "type": "glow", "radius": 240 }""").sizeDp)
        val asToken = errorsOf("""{ "type": "glow", "radius": "card" }""")
        assertTrue(asToken, asToken.contains(".radius"))
        val tooBig = errorsOf("""{ "type": "glow", "radius": 500 }""")
        assertTrue(tooBig, tooBig.contains(".radius"))
    }

    @Test
    fun `glow 不接受任意色值`() {
        val message = errorsOf("""{ "type": "glow", "color": "red" }""")
        assertTrue(message, message.contains(".color"))
    }

    // ------------------------------------------------------------- 通用约束

    @Test
    fun `三个装饰组件都不接收子节点`() {
        val message = errorsOf(
            """{ "type": "waveform", "width": 200, "height": 48, "children": [] }""",
        )
        assertTrue(message, message.contains(".children"))
        assertFalse(UiNodeType.WAVEFORM.isContainer)
        assertFalse(UiNodeType.DISK.isContainer)
        assertFalse(UiNodeType.GLOW.isContainer)
    }

    @Test
    fun `拼错的字段名会报错而不是静默失效`() {
        val message = errorsOf(
            """{ "type": "waveform", "width": 200, "height": 48, "colr": "primary" }""",
        )
        assertTrue(message, message.contains(".colr"))
    }

    @Test
    fun `decorative 是合法语义色且属于前景色白名单`() {
        assertTrue(UiColor.DECORATIVE in UiColor.FOREGROUNDS)
        assertFalse(UiColor.DECORATIVE in UiColor.BACKGROUNDS)
        assertEquals(UiColor.DECORATIVE, requireNode(
            """{ "type": "glow", "color": "decorative" }""",
        ).textColor)
    }
}
