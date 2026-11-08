package com.aiclassroom.feature.exam.tool.custom

import com.aiclassroom.feature.exam.tool.ToolHandler
import com.aiclassroom.feature.exam.tool.ToolRegistry
import com.aiclassroom.feature.exam.tool.ToolResult
import com.aiclassroom.feature.exam.tool.exam.JsonFormatException
import com.aiclassroom.feature.exam.tool.exam.JsonValue
import com.aiclassroom.feature.exam.tool.exam.asObjectOrNull
import com.aiclassroom.feature.exam.tool.image.DEFAULT_TIMEOUT_SECONDS
import com.aiclassroom.feature.exam.tool.image.ImageFailure
import com.aiclassroom.feature.exam.tool.image.ImageSink
import com.aiclassroom.feature.exam.tool.universal.UniversalCallPlan
import com.aiclassroom.feature.exam.tool.universal.UniversalCaller
import com.aiclassroom.feature.exam.tool.universal.formatNumber
import java.util.concurrent.CancellationException

/**
 * 用户自定义插件的执行体：一个 spec = 一个 ToolHandler。
 *
 * 三个字段（name / description / parametersJson）完全由用户提供，直接进 function calling 列表；
 * execute 只做两件事：把模型给的参数渲染进 profile.request.bodyTemplate，再交给已有的
 * [UniversalCaller] 走「提交 → 提取 → 落盘 / 回 JSON」全流程——自定义插件不复用第二套 HTTP 实现。
 *
 * 关掉开关的工具仍然注册（模型看得见名字），但调用在本地就被拒绝并回一条明确原因：
 * 这样用户能验证「关掉就不生效」，而不是遇到一个查无此名的黑洞。
 */
internal class CustomToolHandler(
    private val spec: CustomToolSpec,
    private val sink: ImageSink?,
) : ToolHandler {

    override val name: String = spec.name

    override val description: String = spec.description

    override val parametersJson: String = customToolParameters(spec)

    override suspend fun execute(argumentsJson: String): ToolResult {
        if (!spec.enabled) {
            return ToolResult.Error(
                "自定义插件「" + spec.name + "」已在「设置 → 插件」里关闭，本次没有发出任何请求；" +
                    "需要它请重新打开该插件的开关。"
            )
        }
        return try {
            ToolResult.Ok(runCustomTool(spec, sink, argumentsJson))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: ImageFailure) {
            ToolResult.Error(failure.message ?: ("自定义插件「" + spec.name + "」调用失败"))
        } catch (throwable: Throwable) {
            ToolResult.Error("自定义插件「" + spec.name + "」调用失败：" + (throwable.message ?: throwable::class.java.simpleName))
        }
    }
}

/** parameters 为空时补一个合法的空对象 schema，别让整次请求因为 null 参数定义被服务端拒绝。 */
internal fun customToolParameters(spec: CustomToolSpec): String =
    spec.parametersJson.trim().ifEmpty { CustomToolSpec.NO_PARAMETERS_JSON }

/**
 * 把所有 enabled / disabled 的自定义插件注册进 registry，返回注册数量。
 *
 * [specs] 用 lambda 传：主 app 每次重建 registry 都现读一遍 prefs，
 * 于是「保存后立刻能调」不依赖任何缓存刷新节奏。
 */
fun registerCustomTools(registry: ToolRegistry, specs: () -> List<CustomToolSpec>, sink: ImageSink?): Int {
    val loaded = try {
        specs()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        // 读配置失败不该让整个工具注册表消失：其它插件照常可用。
        return 0
    }
    var count = 0
    val seen = HashSet<String>()
    for (spec in loaded) {
        if (spec.name.isBlank() || !seen.add(spec.name)) continue
        registry.register(CustomToolHandler(spec, sink))
        count++
    }
    return count
}

/**
 * [测试] 按钮背后的真实请求：用当前表单配置发一次，把结果原文交回 UI。
 * 不静默吞错——失败原因（含服务端响应片段）原样返回，用户才知道该怎么改。
 */
suspend fun previewCustomTool(
    spec: CustomToolSpec,
    sink: ImageSink?,
    argumentsJson: String = "{}",
): String = try {
    runCustomTool(spec.copy(enabled = true), sink, argumentsJson)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: ImageFailure) {
    "调用失败：" + (failure.message ?: "未知错误")
} catch (throwable: Throwable) {
    "调用失败：" + (throwable.message ?: throwable::class.java.simpleName)
}

/** 真正的一次调用：profile 原样使用，参数名 → 模板占位符。 */
internal suspend fun runCustomTool(spec: CustomToolSpec, sink: ImageSink?, argumentsJson: String): String {
    val profile = spec.profile
    if (profile.baseUrl.trim().isEmpty()) {
        throw ImageFailure("自定义插件「" + spec.name + "」还没配置 Base URL（设置 → 插件 → 自定义插件 → 编辑）")
    }
    if (profile.request.path.trim().isEmpty()) {
        throw ImageFailure("自定义插件「" + spec.name + "」还没配置请求路径，例如 /version")
    }
    val values = LinkedHashMap<String, String>()
    values.putAll(profile.defaults)
    parseToolArguments(argumentsJson, spec.name).forEach { (key, value) ->
        templateValue(value)?.let { values[key] = it }
    }
    val plan = UniversalCallPlan(
        profileName = spec.name,
        baseUrl = profile.baseUrl,
        auth = profile.auth,
        request = profile.request,
        response = profile.response,
        async = profile.async,
        timeoutSeconds = if (profile.async.enabled) profile.async.timeoutSeconds else DEFAULT_TIMEOUT_SECONDS,
        values = values,
    )
    return UniversalCaller(sink).execute(plan)
}

/** 模型的参数一律是 JSON 对象；空串按「没有参数」处理（无参工具就是这样调的）。 */
internal fun parseToolArguments(argumentsJson: String, toolName: String): Map<String, JsonValue> {
    val raw = argumentsJson.trim()
    if (raw.isEmpty()) return emptyMap()
    val root = try {
        JsonValue.parse(raw)
    } catch (failure: JsonFormatException) {
        throw ImageFailure(toolName + " 的参数不是合法 JSON：" + failure.message)
    }
    return root.asObjectOrNull() ?: throw ImageFailure(toolName + " 的参数必须是 JSON 对象")
}

/** 参数值 → 模板文本。对象 / 数组按紧凑 JSON 传（模板里按字符串转义注入，仍然不会破坏请求体）。 */
private fun templateValue(value: JsonValue): String? = when (value) {
    is JsonValue.Null -> null
    is JsonValue.Str -> value.value
    is JsonValue.Num -> formatNumber(value.value)
    is JsonValue.Bool -> value.value.toString()
    is JsonValue.Obj, is JsonValue.Arr -> JsonValue.write(value)
}
