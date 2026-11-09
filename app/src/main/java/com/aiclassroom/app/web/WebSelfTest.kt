package com.aiclassroom.app.web

import java.io.File

/**
 * 只用于验收的脚本化自查：把三个工具按验收清单逐个真跑一遍，逐条写进结果文件。
 *
 * 它自己不实现任何逻辑，全部走 [dispatch]——也就是线上那条完全相同的 toolRegistry.dispatch 路径，
 * 所以跑通就等于验证了注册、参数解析、安全闸门、WebView 引擎这几段真的接上了。
 * 由 MainActivity 在收到 web_selftest 这个调试 extra 时触发，正常使用永远进不来。
 */
object WebSelfTest {

    /** [dispatch] 返回内容的 JSON 文本；[seedWhitelist] 只在测试里预置白名单，与用户点「允许」等价。 */
    suspend fun run(
        dispatch: suspend (String, String) -> String,
        seedWhitelist: (List<String>) -> Unit,
        log: (String) -> Unit,
        output: File,
    ) {
        val report = StringBuilder()

        fun record(title: String, body: String) {
            report.append("===== ").append(title).append(" =====\n").append(body).append("\n\n")
            runCatching { output.writeText(report.toString(), Charsets.UTF_8) }
            log("[selftest] " + title + " -> " + body.take(300))
        }

        // 场景 0：先探一下离屏 WebView 本身通不通（逐个站点，用于区分「引擎坏」和「这个站连不上」）
        for (probe in listOf("https://example.com", "https://www.bing.com", "https://github.com/login")) {
            record("0. 连通性探测 " + probe, dispatch("web_capture", """{"url":"$probe","width":720,"height":1280,"waitMillis":1500}"""))
        }

        // 场景 1：截图真实登录页
        record(
            "1. web_capture 截图 github 登录页",
            dispatch("web_capture", """{"url":"https://github.com/login","width":1080,"height":1920,"waitMillis":3000}"""),
        )

        // 场景 2：截图 + 视觉模型分析（这条最能说明「AI 真的看懂了界面」）
        record(
            "2. web_capture + analyze 视觉分析登录页",
            dispatch(
                "web_capture",
                """{"url":"https://github.com/login","width":1080,"height":1920,"waitMillis":3000,""" +
                    """"analyze":true,"question":"这个页面上有哪些输入框和按钮？登录入口在哪里？请按你看到的说。"}""",
            ),
        )

        // 场景 3：搜索
        record(
            "3. web_search 通用搜索",
            dispatch("web_search", """{"query":"GitHub 怎么注册账号","limit":5}"""),
        )

        // 场景 4：未授权域名 —— 真实弹框，等用户点「不允许」，这里会拿到拒绝原因
        record(
            "4. web_act 未授权域名（应弹框并被拒）",
            dispatch(
                "web_act",
                """{"url":"https://example.com/login","actions":[{"type":"click","selector":"#next"}]}""",
            ),
        )

        // 场景 5/6 需要 github.com 在白名单里（等价于用户点了「允许」）
        seedWhitelist(listOf("github.com"))

        // 场景 5：密码框拒填
        record(
            "5. web_act 填密码框（应被拒）",
            dispatch(
                "web_act",
                """{"url":"https://github.com/login","actions":[{"type":"fill","selector":"#password","value":"hunter2"}]}""",
            ),
        )

        // 场景 6：敏感按钮（submit）应返回 needsConfirm
        record(
            "6. web_act 点 submit 按钮（应 needsConfirm）",
            dispatch(
                "web_act",
                """{"url":"https://github.com/login","actions":[{"type":"fill","selector":"#login_field","value":"test@example.com"},{"type":"click","selector":"input[type=submit]"}]}""",
            ),
        )

        // 场景 7：动作上限
        val many = (1..11).joinToString(",") { """{"type":"click","selector":"#b$it"}""" }
        record(
            "7. web_act 11 个动作（应被拒）",
            dispatch("web_act", """{"url":"https://github.com/login","actions":[$many]}"""),
        )

        // 场景 8：裸 JS 入口不存在
        record(
            "8. web_act script 类型（应被拒）",
            dispatch(
                "web_act",
                """{"url":"https://github.com/login","actions":[{"type":"script","selector":"alert(1)"}]}""",
            ),
        )

        report.append("===== DONE =====\n")
        runCatching { output.writeText(report.toString(), Charsets.UTF_8) }
        log("[selftest] 全部场景执行完毕")
    }
}
