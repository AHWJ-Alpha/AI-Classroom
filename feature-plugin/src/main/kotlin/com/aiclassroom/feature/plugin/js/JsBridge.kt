package com.aiclassroom.feature.plugin.js

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/** 把一个 Kotlin lambda 包成 JS 函数对象。 */
internal fun jsFunction(name: String, body: (Context, Scriptable, Array<out Any?>) -> Any?): BaseFunction =
    object : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? =
            body(cx, scope, args)

        override fun getFunctionName(): String = name
    }

/** 建一个普通 JS 对象；null 值写成 JS null。 */
internal fun jsObject(cx: Context, scope: Scriptable, properties: Map<String, Any?>): ScriptableObject {
    val target = cx.newObject(scope) as ScriptableObject
    for ((key, value) in properties) {
        ScriptableObject.putProperty(target, key, if (value == null) null else Context.javaToJS(value, scope))
    }
    return target
}

internal fun jsString(value: Any?): String? = when (value) {
    null, is Undefined -> null
    is String -> value
    is CharSequence -> value.toString()
    else -> Context.toString(value)
}

internal fun jsInt(value: Any?): Int? = when (value) {
    null, is Undefined -> null
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}

internal fun jsProperty(target: Any?, name: String): Any? =
    if (target is Scriptable) ScriptableObject.getProperty(target, name) else null
