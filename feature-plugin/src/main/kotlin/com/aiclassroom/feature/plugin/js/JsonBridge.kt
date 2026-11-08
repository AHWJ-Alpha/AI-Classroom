package com.aiclassroom.feature.plugin.js

import com.aiclassroom.feature.plugin.Json
import com.aiclassroom.feature.plugin.JsonNode
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/** 把模块内极简 JSON 树转成真正的 JS 值，供事件 payload 直接以对象形式交给插件。 */
internal fun jsFromJson(cx: Context, scope: Scriptable, node: JsonNode?): Any? = when (node) {
    null, is JsonNode.Null -> null
    is JsonNode.Bool -> node.value
    is JsonNode.Num -> node.value
    is JsonNode.Str -> node.value
    is JsonNode.Arr -> cx.newArray(scope, node.items.map { jsFromJson(cx, scope, it) }.toTypedArray())
    is JsonNode.Obj -> {
        val target = cx.newObject(scope) as ScriptableObject
        for ((key, value) in node.fields) {
            val js = jsFromJson(cx, scope, value)
            ScriptableObject.putProperty(target, key, if (js === Undefined.instance) null else js)
        }
        target
    }
}

/** 解析 payload 文本；坏 JSON 不抛给插件，退化成空对象（宿主日志另有记录）。 */
internal fun jsPayload(cx: Context, scope: Scriptable, payloadJson: String): Any? = try {
    jsFromJson(cx, scope, Json.parse(payloadJson))
} catch (t: Throwable) {
    cx.newObject(scope)
}
