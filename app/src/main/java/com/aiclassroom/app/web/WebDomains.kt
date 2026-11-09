package com.aiclassroom.app.web

import java.util.Locale

/**
 * 网页操作的域名白名单。
 *
 * 规则只有一条但很硬：web_act（替用户点页面、填表）只在白名单域名下执行。
 * 白名单由用户维护，AI 没有写入口；第一次碰到新域名必须当场问用户。
 *
 * 存储层做成接口，Android 侧用 SharedPreferences 实现，测试可以直接用内存实现。
 * 值统一存成逗号分隔的 host（小写、无端口、无 www 前缀归一），空串 = 白名单为空。
 */
interface WebDomainStore {
    fun loadAllowed(): List<String>
    fun saveAllowed(domains: List<String>)
}

/** 纯内存实现，供单测 / 无 Context 场景使用。 */
class InMemoryWebDomainStore(initial: List<String> = emptyList()) : WebDomainStore {
    private var domains: List<String> = initial.map { WebDomains.normalize(it) }.filter { it.isNotBlank() }

    override fun loadAllowed(): List<String> = domains

    override fun saveAllowed(domains: List<String>) {
        this.domains = domains.map { WebDomains.normalize(it) }.filter { it.isNotBlank() }.distinct()
    }
}

object WebDomains {

    /** 把用户/AI 给的东西归一成可比对的 host：去协议、去路径、去端口、去 www.、转小写。 */
    fun normalize(raw: String): String {
        var value = raw.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return ""
        value = value.substringAfter("://", value)
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        // 去掉 user:pass@ 前缀
        value = value.substringAfterLast('@')
        // 去掉端口；IPv6 的 [::1]:8080 那种先按 ']' 切断再说
        value = if (value.startsWith("[")) {
            value.substringBefore(']').removePrefix("[")
        } else {
            value.substringBefore(':')
        }
        value = value.removePrefix("www.")
        return value.trim('.')
    }

    /** 从 URL 里抠出 host；不是 http/https 时返回空串。 */
    fun hostOf(url: String): String {
        val trimmed = url.trim().lowercase(Locale.ROOT)
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return ""
        return normalize(trimmed)
    }

    /**
     * host 是否被允许：允许列表里任何一项等于它、或是它的父域（例子：
     * 白名单有 example.com 时，login.example.com 也算被允许——用户授权的是「这个网站」）。
     * 反过来不成立：白名单写 login.example.com 不会连带放行 example.com。
     */
    fun isAllowed(host: String, allowed: List<String>): Boolean {
        val target = normalize(host)
        if (target.isEmpty()) return false
        return allowed.map { normalize(it) }.any { entry ->
            entry.isNotEmpty() && (target == entry || target.endsWith("." + entry))
        }
    }

    /** 把逗号/换行/空格分隔的一串 host 拆成列表并去重。 */
    fun parseList(raw: String): List<String> = raw
        .split(',', '\n', ' ', '\t', ';', '，')
        .map { normalize(it) }
        .filter { it.isNotBlank() }
        .distinct()

    fun joinList(domains: List<String>): String = domains.joinToString(",")
}
