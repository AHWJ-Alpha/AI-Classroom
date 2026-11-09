package com.aiclassroom.app.web

import com.aiclassroom.feature.exam.tool.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * web_search：给定一句话去搜，返回 {title, url, snippet} 列表。
 *
 * 刻意不接外部搜索 API（那需要用户再配一把 key，还得挑服务商），走的是「直接抓搜索结果页」：
 * 有 site 就在站内搜，没 site 就用 Bing。解析只用正则抠链接和标题，
 * 不引 JSoup 之类的重依赖——搜索页的 HTML 结构本来就不稳定，多扛一层解析库只是多背一层风险。
 *
 * 与 web_act 不同，搜索是只读的：不点按钮、不填表、不留状态，所以不吃域名白名单。
 * 但它照样受「网络工具」总开关约束。
 */
class WebSearchTool(deps: WebToolDeps) : WebToolBase(deps) {

    override val name: String = "web_search"

    override val description: String =
        "上网搜一条信息，返回前几条结果的 {title, url, snippet}。" +
            "给了 site 就在那个站内搜（例如 site=github.com），没给就用通用搜索。" +
            "适合「帮我查一下怎么注册 XX」「这个报错是什么意思」。" +
            "只做搜索、不点开结果里的页面；想看看结果页长什么样，用 web_capture 抓具体网址。" +
            SAFETY_NOTE

    override val parametersJson: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "搜索词，用自然语言写清楚要查什么"},
            "site": {"type": "string", "description": "可选，限定在某个站点内搜，例如 example.com"},
            "limit": {"type": "integer", "description": "返回条数，默认 10，上限 10"}
          },
          "required": ["query"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = parseArguments(argumentsJson) ?: return fail("参数不是合法 JSON。")
        val query = args.optString("query").trim()
        if (query.isEmpty()) return fail("缺少 query。")
        val site = WebDomains.normalize(args.optString("site"))
        val limit = args.optInt("limit", MAX_RESULTS).coerceIn(1, MAX_RESULTS)

        val attempts = if (site.isEmpty()) {
            listOf("https://www.bing.com/search?q=" + encode(query) + "&setlang=zh-CN")
        } else {
            listOf(
                "https://" + site + "/search?q=" + encode(query),
                "https://" + site + "/search?keyword=" + encode(query),
                "https://" + site + "/search?wd=" + encode(query),
                "https://www.bing.com/search?q=" + encode("site:" + site + " " + query) + "&setlang=zh-CN",
            )
        }

        var lastError = ""
        for (url in attempts) {
            val html = runCatching { fetch(url) }.getOrElse { error ->
                if (error is CancellationException) throw error
                lastError = (error.message ?: error.javaClass.simpleName) + " (" + url + ")"
                ""
            }
            if (html.isBlank()) continue
            val results = parseResults(html, site, url).take(limit)
            if (results.isEmpty()) {
                lastError = "抓到页面但没解析出结果 (" + url + ")"
                continue
            }
            deps.log("[web] web_search \"" + query + "\" site=" + site.ifBlank { "-" } + " -> " + results.size + " 条")
            return ok(
                JSONObject()
                    .put("ok", true)
                    .put("query", query)
                    .put("site", site)
                    .put("source", url)
                    .put("count", results.size)
                    .put("results", JSONArray(results))
            )
        }
        return fail(
            "没有搜到结果。" + (if (lastError.isNotBlank()) "最后一次失败原因：" + lastError else "") +
                "可以换个说法再试，或者用 web_capture 直接打开目标网站看看。"
        )
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** 拉一页搜索结果 HTML。上限 512KB，正常搜索页远小于这个数。 */
    private fun fetch(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
            )
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP " + status)
            val stream = connection.inputStream
            stream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_HTML_BYTES) break
                    out.write(buffer, 0, read)
                }
                String(out.toByteArray(), charsetOf(connection.contentType))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun charsetOf(contentType: String?): java.nio.charset.Charset {
        val match = Regex("charset=([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(contentType.orEmpty())
        return runCatching { java.nio.charset.Charset.forName(match?.groupValues?.get(1) ?: "UTF-8") }
            .getOrDefault(Charsets.UTF_8)
    }

    /**
     * 抓 <a href="...">标题</a>，配上附近的一小段文字当 snippet。
     * 宁可多抓几条再由 [filterNoise] 减掉导航项，也不做脆弱的 DOM 结构假设。
     */
    private fun parseResults(html: String, site: String, sourceUrl: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        val pattern = Regex("<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in pattern.findAll(html)) {
            if (out.size >= MAX_RESULTS * 2) break
            val href = unescape(match.groupValues[1]).trim()
            val title = stripTags(match.groupValues[2])
            if (title.length < 4) continue
            val resolved = resolveUrl(href, sourceUrl, site) ?: continue
            if (!seen.add(resolved)) continue
            // 链接后面紧跟的那段文字最像摘要，取 240 字符清洗一下。
            val tailStart = (match.range.last + 1).coerceAtMost(html.length)
            val tailEnd = (tailStart + 400).coerceAtMost(html.length)
            val snippet = stripTags(html.substring(tailStart, tailEnd)).take(180)
            out += JSONObject()
                .put("title", title.take(140))
                .put("url", resolved)
                .put("snippet", snippet)
        }
        return filterNoise(out)
    }

    /** 搜索页自带一堆导航 / 登录 / 「更多」链接，在这里挡掉，别让模型把菜单当答案。 */
    private fun filterNoise(items: List<JSONObject>): List<JSONObject> {
        val noisyHosts = listOf("bing.com", "microsoft.com", "msn.com", "go.microsoft.com")
        return items.filter { item ->
            val url = item.optString("url").lowercase(Locale.ROOT)
            val title = item.optString("title").lowercase(Locale.ROOT)
            if (url.startsWith("javascript:") || url.startsWith("#")) return@filter false
            if (noisyHosts.any { url.contains("://" + it + "/") || url.contains("://www." + it + "/") } &&
                !url.contains("/search?")
            ) {
                // 搜索结果本身指向 bing 自家页面时才丢；站点搜索的落地页放行。
                if (!url.contains("bing.com/search") && !url.contains("bing.com/ck")) return@filter false
            }
            val noiseTitles = listOf("登录", "注册", "sign in", "sign up", "log in", "更多", "more", "隐私", "privacy")
            if (title in noiseTitles) return@filter false
            true
        }
    }

    /** 相对链接补全；站内搜索时把明显跑出站外的链接丢掉。 */
    private fun resolveUrl(href: String, sourceUrl: String, site: String): String? {
        if (href.isEmpty()) return null
        if (href.startsWith("//")) return "https:" + href
        if (href.startsWith("http://") || href.startsWith("https://")) {
            // Bing 的跳转链接：解出真正的目标地址。
            if (href.contains("bing.com/ck/a")) {
                val target = Regex("[?&]u=a1([^&]+)").find(href)?.groupValues?.get(1) ?: return null
                val decoded = runCatching {
                    String(Base64Url.decode(target), Charsets.UTF_8)
                }.getOrNull() ?: return null
                return decoded.takeIf { it.startsWith("http") }
            }
            return href
        }
        if (href.startsWith("/")) {
            val origin = runCatching { URI(sourceUrl).let { it.scheme + "://" + it.host } }.getOrNull() ?: return null
            return origin + href
        }
        return null
    }

    private object Base64Url {
        fun decode(text: String): ByteArray =
            java.util.Base64.getUrlDecoder().decode(text.padEnd((text.length + 3) / 4 * 4, '='))
    }

    private fun stripTags(raw: String): String = unescape(
        raw.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
    ).trim()

    private fun unescape(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    companion object {
        private const val MAX_RESULTS = 10
        private const val MAX_HTML_BYTES = 512 * 1024
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
