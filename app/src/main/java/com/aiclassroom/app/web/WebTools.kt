package com.aiclassroom.app.web

import android.graphics.Bitmap
import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.CancellationException

/**
 * web 工具集的运行时依赖，全部由主 app 注入（本包不需要认识 Compose / Activity）：
 * 引擎拿 Context 建离屏 WebView，白名单读写走 SharedPreferences 实现，
 * 视觉分析直接借主 app 已有的 callVisionWithFallback 链路。
 */
class WebToolDeps(
    val engine: WebCaptureEngine,
    val domains: WebDomainStore,
    val consent: WebConsentBroker,
    /** 把 PNG 存到 filesDir/images，返回绝对路径。失败给 Result.failure。 */
    val saveScreenshot: suspend (ByteArray, String) -> Result<String>,
    /** 交给视觉模型：system / prompt / dataUrl → 纯文本回答。 */
    val analyze: suspend (system: String, prompt: String, dataUrl: String) -> String,
    val log: (String) -> Unit,
)

/** 三个工具的公共外壳：参数解析 + 结果拼装的样板代码，各工具只关心自己的逻辑。 */
abstract class WebToolBase protected constructor(protected val deps: WebToolDeps) : ToolHandler {

    protected fun fail(message: String): ToolResult {
        deps.log("[web] " + name + " -> REJECTED " + message.take(200))
        return ToolResult.Error(message)
    }

    protected fun ok(json: JSONObject): ToolResult = ToolResult.Ok(json.toString())

    protected fun parseArguments(argumentsJson: String): JSONObject? = runCatching {
        JSONObject(argumentsJson.ifBlank { "{}" })
    }.getOrNull()

    companion object {
        const val SAFETY_NOTE =
            "【安全边界】涉及支付、下单、登录、删除、转账等操作时，必须先向用户说明你要做什么并请求确认；" +
                "确认前不要执行。密码一律由用户自己输入，你只负责指出位置。"
    }
}

/**
 * web_capture：离屏 WebView 打开页面并截图，可选交给视觉模型分析。
 *
 * 这条工具解决的是「AI 教用户登录注册」的核心问题：以前只能拿到 HTML 源码，
 * 现在 AI 真的能看到渲染后的界面。
 */
class WebCaptureTool(deps: WebToolDeps) : WebToolBase(deps) {

    override val name: String = "web_capture"

    override val description: String =
        "打开一个网页，用无头 WebView 渲染后截图，并把截图保存到本机。" +
            "和 net_request 的区别：net_request 只拿得到 HTML 源码，现代网站靠 JS 渲染，" +
            "拿到的往往是空壳；web_capture 拿的是用户真正看到的那个界面。" +
            "想看长页面下半部分就把 scrollY 设大，想等慢页面渲染完就把 waitMillis 调大。" +
            "analyze=true 时截图会额外交给视觉模型分析，并用 question 提问——" +
            "这是教用户登录注册、找按钮在哪的正确做法：先把页面看懂，再告诉他点哪里。" +
            SAFETY_NOTE

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "url": {"type": "string", "description": "完整 URL，必须以 http:// 或 https:// 开头"},
            "width": {"type": "integer", "description": "截图宽度像素，默认 1080，范围 240-4000"},
            "height": {"type": "integer", "description": "截图高度像素，默认 1920，范围 240-4000"},
            "scrollY": {"type": "integer", "description": "截图前滚动到的纵向位置，默认 0（页面顶部）；看长页面下半部分时设大"},
            "waitMillis": {"type": "integer", "description": "页面加载完后再等多久让 JS 渲染，默认 2500，上限 15000"},
            "clipX": {"type": "integer", "description": "只截这块区域：左边界（截图自身像素）。要单独截登录二维码这种小目标时填"},
            "clipY": {"type": "integer", "description": "只截这块区域：上边界（截图自身像素）"},
            "clipW": {"type": "integer", "description": "只截这块区域：宽度（截图自身像素）。与 clipH 同时 > 0 才生效"},
            "clipH": {"type": "integer", "description": "只截这块区域：高度（截图自身像素）"},
            "analyze": {"type": "boolean", "description": "是否把截图交给视觉模型分析，默认 false"},
            "question": {"type": "string", "description": "analyze=true 时要问视觉模型的问题，例如「登录入口在哪」"}
          },
          "required": ["url"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = parseArguments(argumentsJson) ?: return fail("参数不是合法 JSON。")
        val url = args.optString("url").trim()
        if (url.isEmpty()) return fail("缺少 url。")
        val width = args.optInt("width", WebCaptureEngine.DEFAULT_WIDTH)
        val height = args.optInt("height", WebCaptureEngine.DEFAULT_HEIGHT)
        val scrollY = args.optInt("scrollY", 0).coerceAtLeast(0)
        val waitMillis = args.optLong("waitMillis", WebCaptureEngine.DEFAULT_WAIT_MILLIS)
        // 裁剪区域（可选）：用来单独截登录二维码这种小目标 ——
        // 整页截图里二维码只有几十像素，用户手机上根本扫不出来。
        val clipX = args.optInt("clipX", 0)
        val clipY = args.optInt("clipY", 0)
        val clipW = args.optInt("clipW", 0)
        val clipH = args.optInt("clipH", 0)

        val started = System.currentTimeMillis()
        val captured = deps.engine.capture(url, width, height, waitMillis, scrollY, clipX, clipY, clipW, clipH)
        val bitmap = captured.getOrElse { error ->
            return fail("网页截图失败：" + (error.message ?: error.javaClass.simpleName))
        }
        // PNG 编码在 IO 线程做，别占着主线程。
        val bytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        }
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        bitmap.recycle()

        val name = "web_" + System.currentTimeMillis() + ".png"
        val savedPath = deps.saveScreenshot(bytes, name).getOrElse { error ->
            return fail("截图已生成，但保存失败：" + (error.message ?: error.javaClass.simpleName))
        }
        deps.log("[web] web_capture $url -> $savedPath (${bitmapWidth}x${bitmapHeight}, ${bytes.size} bytes, ${System.currentTimeMillis() - started}ms)")

        val result = JSONObject()
            .put("ok", true)
            .put("path", savedPath)
            .put("url", url)
            .put("width", bitmapWidth)
            .put("height", bitmapHeight)
            .put("scrollY", scrollY)
            .put("bytes", bytes.size)

        if (args.optBoolean("analyze", false)) {
            val question = args.optString("question").trim()
                .ifBlank { "请描述这个页面上都有什么：有哪些输入框、按钮和链接，用户想完成操作该点哪里。" }
            val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
            val analysis = deps.analyze(
                "你是一个帮用户看懂网页界面的助手。只描述截图上真实可见的元素，不要臆测看不见的东西。",
                question,
                dataUrl,
            )
            result.put("question", question)
            result.put("analysis", analysis)
        }
        return ok(result)
    }
}

/**
 * web_act：在受控条件下替用户点页面 / 填表。
 *
 * 三道闸门串在一条线上，任何一道不过就不操作：
 * 1. 域名白名单（未授权域名先问用户，同意后才记进白名单）；
 * 2. 密码框硬拒绝 + 敏感关键词要确认（[WebGuard] 两道判定：传参前、页面加载后）；
 * 3. confirmRisk 重试机制：第一次命中敏感操作返回 needsConfirm 让用户拍板。
 */
class WebActTool(deps: WebToolDeps) : WebToolBase(deps) {

    override val name: String = "web_act"

    override val description: String =
        "在网页里替用户执行受控操作：填表单（fill）、点按钮（click）、等待（wait）。" +
            "只支持这三种动作，不接受裸 JavaScript。" +
            "【硬性限制】" +
            "① 只在用户授权过的域名下执行；第一次访问某个域名时会当场请求用户确认，用户拒绝就什么都不会做。" +
            "② 密码框一律拒绝填写——密码必须由用户自己输入，你只负责指出位置。" +
            "③ 动作或页面文案里出现支付/下单/删除/确认等敏感词时，本次调用直接返回 needsConfirm 并说明原因，" +
            "你必须把原因原样转述给用户、等他明确同意后，才可以用 confirmRisk=true 重试。" +
            "用户没同意就重试视为越权。" +
            "④ 单次最多 10 个动作、总等待不超过 30 秒。" +
            "涉及支付、登录、删除等操作时，必须先向用户说明并请求确认。"

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "url": {"type": "string", "description": "要操作的页面地址，必须 http/https"},
            "actions": {
              "type": "array",
              "maxItems": 10,
              "description": "按顺序执行的动作，最多 10 个",
              "items": {
                "type": "object",
                "properties": {
                  "type": {"type": "string", "enum": ["fill", "click", "wait"], "description": "动作类型"},
                  "selector": {"type": "string", "description": "CSS selector，fill / click 必填"},
                  "value": {"type": "string", "description": "fill 时写入的文本"},
                  "ms": {"type": "integer", "description": "wait 时等待的毫秒数，上限 10000"}
                },
                "required": ["type"]
              }
            },
            "confirmRisk": {
              "type": "boolean",
              "description": "只有在用户已在对话里明确同意这次敏感操作后才传 true；第一次遇到敏感操作时不要传"
            },
            "waitMillis": {"type": "integer", "description": "页面加载后再等多久让 JS 渲染，默认 2500，上限 15000"}
          },
          "required": ["url", "actions"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = parseArguments(argumentsJson) ?: return fail("参数不是合法 JSON。")
        val url = args.optString("url").trim()
        if (url.isEmpty()) return fail("缺少 url。")
        val host = WebDomains.hostOf(url)
        if (host.isEmpty()) return fail("url 必须是 http / https 地址，收到：" + url.take(120))

        val rawActions = args.optJSONArray("actions") ?: return fail("缺少 actions 数组。")
        val actions = ArrayList<WebGuard.Action>(rawActions.length())
        for (index in 0 until rawActions.length()) {
            val item = rawActions.optJSONObject(index) ?: return fail("第 " + (index + 1) + " 个动作不是对象。")
            actions += WebGuard.Action(
                type = item.optString("type").trim(),
                selector = item.optString("selector").trim(),
                value = item.optString("value"),
                ms = item.optLong("ms", 0L),
            )
        }
        val confirmRisk = args.optBoolean("confirmRisk", false)
        val waitMillis = args.optLong("waitMillis", WebCaptureEngine.DEFAULT_WAIT_MILLIS)

        // —— 闸门 1：域名白名单 ——
        val allowed = deps.domains.loadAllowed()
        if (!WebDomains.isAllowed(host, allowed)) {
            val granted = deps.consent.ask(
                "允许 AI 操作这个网站吗？",
                "AI 想在你的手机上打开并操作：" + host + "\n\n网址：" + url +
                    "\n\n点「允许这一次」会把这个域名记进白名单，之后同类操作不再重复询问。" +
                    "你可以随时在「设置 → 插件 → 网页工具」里删掉它。",
            )
            if (!granted) {
                return fail(
                    "用户没有授权域名 " + host + "，本次没有执行任何操作（页面都没打开）。" +
                        "如果需要，请先在对话里向用户说明你要在这个网站做什么，等他同意后再试。"
                )
            }
            deps.domains.saveAllowed((allowed + host).distinct())
            deps.log("[web] web_act 用户授权域名 " + host)
        }

        // —— 闸门 2a：传参前的静态判定（密码硬拒 / 敏感词要确认） ——
        when (val pre = WebGuard.preflight(actions)) {
            is WebGuard.Check.Block -> return fail(pre.reason)
            is WebGuard.Check.NeedsConfirm -> {
                if (!confirmRisk) {
                    deps.log("[web] web_act -> NEEDS_CONFIRM " + pre.reason.take(160))
                    return ok(needsConfirm(url, pre.reason, actions.size))
                }
            }
            WebGuard.Check.Pass -> Unit
        }

        // —— 闸门 2b：页面加载后拿真实 DOM 再判一次，然后才动第一下 ——
        val executed = deps.engine.withPage(url, waitMillis = waitMillis, timeoutMs = WebCaptureEngine.ACT_TIMEOUT_MS) { page ->
            val selectors = actions.mapNotNull { it.selector.takeIf { s -> s.isNotBlank() } }.distinct()
            if (selectors.isNotEmpty()) {
                val probeRaw = page.eval(WebScripts.probe(selectors)).getOrElse { error ->
                    return@withPage ActionOutcome.Rejected("探测页面元素失败：" + (error.message ?: "未知错误"))
                }
                val probed = parseProbe(probeRaw)
                val passwordSelector = probed.firstOrNull { it["type"] == "password" }?.get("selector")
                if (passwordSelector != null && actions.any { it.type.equals("fill", true) && it.selector == passwordSelector }) {
                    return@withPage ActionOutcome.Rejected(
                        "拒绝填写密码框（selector=" + passwordSelector + "）：密码必须由用户自己输入，AI 只负责指出位置。"
                    )
                }
                when (val post = WebGuard.inspectProbe(probed)) {
                    is WebGuard.Check.NeedsConfirm -> {
                        if (!confirmRisk) return@withPage ActionOutcome.NeedsConfirm(post.reason)
                    }
                    is WebGuard.Check.Block -> return@withPage ActionOutcome.Rejected(post.reason)
                    WebGuard.Check.Pass -> Unit
                }
            }

            val done = ArrayList<String>(actions.size)
            actions.forEachIndexed { index, action ->
                val at = (index + 1).toString()
                when (action.type.lowercase()) {
                    "fill" -> {
                        val raw = page.eval(WebScripts.fill(action.selector, action.value)).getOrElse { error ->
                            return@withPage ActionOutcome.Rejected("第 " + at + " 个动作（fill）执行失败：" + (error.message ?: "未知错误"))
                        }
                        val obj = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull()
                        if (obj?.optBoolean("ok") != true) {
                            val detail = obj?.optString("error").orEmpty()
                            if (detail == "password-field") {
                                return@withPage ActionOutcome.Rejected(
                                    "拒绝填写密码框（selector=" + action.selector + "）：密码必须由用户自己输入，AI 只负责指出位置。"
                                )
                            }
                            return@withPage ActionOutcome.Rejected(
                                "第 " + at + " 个动作（fill）没成功：" + detail.ifBlank { raw.take(120) }
                            )
                        }
                        done += "fill " + action.selector + " ✓"
                    }
                    "click" -> {
                        val raw = page.eval(WebScripts.click(action.selector)).getOrElse { error ->
                            return@withPage ActionOutcome.Rejected("第 " + at + " 个动作（click）执行失败：" + (error.message ?: "未知错误"))
                        }
                        val obj = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull()
                        if (obj?.optBoolean("ok") != true) {
                            return@withPage ActionOutcome.Rejected(
                                "第 " + at + " 个动作（click）没成功：" + obj?.optString("error").orEmpty().ifBlank { raw.take(120) }
                            )
                        }
                        done += "click " + action.selector + " ✓"
                        // 点完给页面一点时间响应，后续动作才能落在新出现的元素上。
                        page.waitFor(600)
                    }
                    "wait" -> {
                        page.waitFor(action.ms)
                        done += "wait " + action.ms + "ms ✓"
                    }
                }
            }
            val summary = page.eval(WebScripts.pageSummary()).getOrDefault("")
            ActionOutcome.Done(done, summary)
        }

        return executed.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is ActionOutcome.Rejected -> fail(outcome.reason)
                    is ActionOutcome.NeedsConfirm -> ok(needsConfirm(url, outcome.reason, actions.size))
                    is ActionOutcome.Done -> {
                        val summary = runCatching { JSONObject(outcome.summary.ifBlank { "{}" }) }.getOrNull()
                        deps.log("[web] web_act $url -> " + outcome.steps.joinToString("; "))
                        ok(
                            JSONObject()
                                .put("ok", true)
                                .put("url", url)
                                .put("executed", JSONArray(outcome.steps))
                                .put("page", summary ?: JSONObject())
                        )
                    }
                }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                fail("网页操作失败：" + (error.message ?: error.javaClass.simpleName))
            },
        )
    }

    private fun needsConfirm(url: String, reason: String, actionCount: Int): JSONObject = JSONObject()
        .put("ok", false)
        .put("needsConfirm", true)
        .put("url", url)
        .put("actions", actionCount)
        .put("reason", reason)
        .put("instruction", "这次没有执行任何操作。请把上面的原因原样告诉用户，等他明确同意后，再带 confirmRisk=true 重试同样的调用。")

    private fun parseProbe(raw: String): List<Map<String, String>> {
        val array = runCatching { JSONArray(raw.ifBlank { "[]" }) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Map<String, String>>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (!item.optBoolean("found", false)) continue
            out += mapOf(
                "selector" to item.optString("selector"),
                "tag" to item.optString("tag"),
                "type" to item.optString("type"),
                "text" to item.optString("text"),
                "label" to item.optString("label"),
                "formText" to item.optString("formText"),
                "formAction" to item.optString("formAction"),
            )
        }
        return out
    }

    private sealed class ActionOutcome {
        data class Done(val steps: List<String>, val summary: String) : ActionOutcome()
        data class Rejected(val reason: String) : ActionOutcome()
        data class NeedsConfirm(val reason: String) : ActionOutcome()
    }
}

/** 把三个网页工具注册进同一个 [ToolRegistry]；返回工具名列表，方便宿主打日志。 */
fun registerWebTools(registry: ToolRegistry, deps: WebToolDeps): List<String> {
    val tools: List<ToolHandler> = listOf(
        WebCaptureTool(deps),
        WebActTool(deps),
        WebSearchTool(deps),
    )
    tools.forEach(registry::register)
    return tools.map { it.name }
}
