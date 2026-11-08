package com.aiclassroom.feature.plugin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginUiParserTest {

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

    @Test
    fun `合法布局与叶组件全部解析`() {
        val root = requireNode(
            """
            {
              "type": "column", "anchor": "bottom_right", "offsetX": -16, "offsetY": -80,
              "padding": 8, "gap": 8, "width": "fill", "height": "wrap",
              "visibleWhen": "state.showBubble", "background": "surfaceVariant", "radius": "card",
              "animation": { "type": "fade", "durationMs": 300 },
              "children": [
                { "type": "stack", "children": [ { "type": "spacer", "width": 4, "height": 4 } ] },
                { "type": "row", "children": [] },
                { "type": "box", "children": [] },
                { "type": "card", "children": [] },
                { "type": "bubble", "children": [] },
                { "type": "divider" },
                { "type": "text", "text": "喵～", "size": "title", "color": "primary", "weight": "bold", "maxLines": 2 },
                { "type": "text", "textFrom": "state.line", "size": "caption", "color": "muted" },
                { "type": "image", "src": "assets/cat.png", "width": 96, "height": 96, "onTap": "onPetTap" },
                { "type": "icon", "name": "heart", "size": 20, "color": "danger" },
                { "type": "button", "label": "摸摸", "style": "primary", "onTap": "onPetTap" },
                { "type": "switch", "checkedFrom": "state.enabled", "onChange": "onToggle" },
                { "type": "slider", "valueFrom": "state.level", "min": 0, "max": 100, "onChange": "onLevelChange" },
                { "type": "progress", "valueFrom": "state.exp", "color": "primary" }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(UiNodeType.COLUMN, root.type)
        assertEquals(UiAnchor.BOTTOM_RIGHT, root.anchor)
        assertEquals(-16, root.offsetX)
        assertEquals(-80, root.offsetY)
        assertEquals(UiLength.Fill, root.width)
        assertEquals(UiLength.Wrap, root.height)
        assertEquals(UiColor.SURFACE_VARIANT, root.background)
        assertEquals(UiRadius.CARD, root.radius)
        assertEquals(UiAnimation.Fade(300), root.animation)
        assertEquals(14, root.children.size)
        // 每种组件都真的被识别成对应类型，而不是被吞成别的东西
        assertEquals(
            listOf(
                UiNodeType.STACK, UiNodeType.ROW, UiNodeType.BOX, UiNodeType.CARD, UiNodeType.BUBBLE,
                UiNodeType.DIVIDER, UiNodeType.TEXT, UiNodeType.TEXT, UiNodeType.IMAGE, UiNodeType.ICON,
                UiNodeType.BUTTON, UiNodeType.SWITCH, UiNodeType.SLIDER, UiNodeType.PROGRESS,
            ),
            root.children.map { it.type },
        )
        val text = root.children[6]
        assertEquals("喵～", text.text)
        assertEquals(UiTextSize.TITLE, text.textSize)
        assertEquals(UiColor.PRIMARY, text.textColor)
        assertEquals(UiWeight.BOLD, text.weight)
        assertEquals(2, text.maxLines)
        assertEquals("state.line", root.children[7].textFrom)
        assertEquals("assets/cat.png", root.children[8].src)
        assertEquals(UiLength.Fixed(96), root.children[8].width)
        assertEquals("onPetTap", root.children[8].onTap)
        assertEquals(UiIcon.HEART, root.children[9].name)
        assertEquals(20, root.children[9].iconSize)
        assertEquals(UiButtonStyle.PRIMARY, root.children[10].style)
        assertEquals("state.enabled", root.children[11].checkedFrom)
        assertEquals("onToggle", root.children[11].onChange)
        assertEquals(0, root.children[12].min)
        assertEquals(100, root.children[12].max)
        assertEquals("state.exp", root.children[13].valueFrom)
    }

    @Test
    fun `sprite 动画解析成按帧循环`() {
        val root = requireNode(
            """
            { "type": "image", "src": "assets/cat_idle.png",
              "animation": { "type": "sprite", "frames": ["assets/cat_idle.png", "assets/cat_tap.png"], "fps": 4 } }
            """.trimIndent(),
        )
        val animation = root.animation as? UiAnimation.Sprite
        assertNotNull(animation)
        assertEquals(listOf("assets/cat_idle.png", "assets/cat_tap.png"), animation!!.frames)
        assertEquals(4, animation.fps)
        assertEquals(250, animation.durationMs)
    }

    @Test
    fun `顶层包一层 root 也接受`() {
        val root = requireNode("""{ "root": { "type": "text", "text": "hi" } }""")
        assertEquals(UiNodeType.TEXT, root.type)
        assertEquals("hi", root.text)
    }

    @Test
    fun `非法颜色 token 被拒绝并指出节点与字段`() {
        val errors = errorsOf("""{ "type": "text", "text": "hi", "color": "#FF00FF" }""")
        assertTrue(errors, errors.contains("root.color"))
        assertTrue(errors, errors.contains("primary|muted|danger|onSurface"))
        assertTrue(errors, errors.contains("#FF00FF"))
    }

    @Test
    fun `背景色不接受前景 token`() {
        val errors = errorsOf("""{ "type": "card", "background": "danger", "children": [] }""")
        assertTrue(errors, errors.contains("root.background"))
        assertTrue(errors, errors.contains("surface|surfaceVariant|primary|transparent"))
    }

    @Test
    fun `非法圆角与字号 token 被拒绝`() {
        val radius = errorsOf("""{ "type": "card", "radius": "12dp" }""")
        assertTrue(radius, radius.contains("root.radius"))
        val size = errorsOf("""{ "type": "text", "text": "hi", "size": "huge" }""")
        assertTrue(size, size.contains("root.size"))
    }

    @Test
    fun `超过深度上限报错并给出行路径`() {
        var json = """{ "type": "text", "text": "deep" }"""
        repeat(9) { json = """{ "type": "column", "children": [$json] }""" }
        val errors = errorsOf(json)
        assertTrue(errors, errors.contains("超过最大嵌套深度"))
        assertTrue(errors, errors.contains("root.children[0].children[0].children[0]"))
        // 8 层是合法的边界值
        var ok = """{ "type": "text", "text": "deep" }"""
        repeat(7) { ok = """{ "type": "column", "children": [$ok] }""" }
        assertTrue(PluginUiParser.parse(ok).errors.joinToString("；"), PluginUiParser.parse(ok).ok)
    }

    @Test
    fun `超过节点数上限报错`() {
        val children = (0 until 220).joinToString(",") { """{ "type": "spacer", "width": 1, "height": 1 }""" }
        val errors = errorsOf("""{ "type": "row", "children": [$children] }""")
        assertTrue(errors, errors.contains("节点数超过上限 200 个"))
    }

    @Test
    fun `未知组件类型列出可选项`() {
        val errors = errorsOf("""{ "type": "marquee" }""")
        assertTrue(errors, errors.contains("root.type 未知组件 'marquee'"))
        assertTrue(errors, errors.contains("bubble"))
    }

    @Test
    fun `未知字段不被静默忽略`() {
        val errors = errorsOf("""{ "type": "text", "text": "hi", "colr": "primary" }""")
        assertTrue(errors, errors.contains("root.colr 不是 text 支持的字段"))
    }

    @Test
    fun `表达式只接受 state 与 msg 的单层取值`() {
        assertTrue(errorsOf("""{ "type": "card", "visibleWhen": "state.a && state.b" }""")
            .contains("只支持 state.xxx / msg.xxx"))
        assertTrue(errorsOf("""{ "type": "card", "visibleWhen": "window.x" }""")
            .contains("只支持 state.xxx / msg.xxx"))
        assertTrue(PluginUiParser.parse("""{ "type": "card", "visibleWhen": "msg.chars" }""").ok)
    }

    @Test
    fun `text 缺少 text 与 textFrom 报错`() {
        val errors = errorsOf("""{ "type": "text", "size": "body" }""")
        assertTrue(errors, errors.contains("必须提供 text 或 textFrom"))
    }

    @Test
    fun `text 与 textFrom 不能同时给`() {
        val errors = errorsOf("""{ "type": "text", "text": "a", "textFrom": "state.a" }""")
        assertTrue(errors, errors.contains("只能二选一"))
    }

    @Test
    fun `图片路径不能穿越出插件包`() {
        val errors = errorsOf("""{ "type": "image", "src": "../../etc/passwd" }""")
        assertTrue(errors, errors.contains("root.src 必须是包内相对路径"))
    }

    @Test
    fun `开关与滑杆必须给回调`() {
        val toggle = errorsOf("""{ "type": "switch", "checkedFrom": "state.on" }""")
        assertTrue(toggle, toggle.contains("缺少必需字段 onChange"))
        val slider = errorsOf("""{ "type": "slider", "valueFrom": "state.v", "min": 10, "max": 10, "onChange": "onV" }""")
        assertTrue(slider, slider.contains("min 必须小于 max"))
    }

    @Test
    fun `顶层不是对象时报错`() {
        assertTrue(errorsOf("""[{ "type": "text", "text": "hi" }]""").contains("顶层必须是对象"))
        assertTrue(errorsOf("{").contains("不是合法 JSON"))
        assertTrue(PluginUiParser.parse("").errors.joinToString("；").contains("内容为空"))
    }
}
