# feature-plugin

纯 Kotlin/JVM（JVM 17、java-library + kotlin jvm）的插件运行时核心，不含任何 Android / Compose 依赖。
界面与宿主能力由 `:app` 实现 `PluginHostApi` 后注入。

## 1. 插件包格式

插件就是一个 ZIP（后缀 `.aip`，也接受 `.zip`）：

```
pet_cat.aip
├── manifest.json    必需
├── logic.js         可选（有行为就需要）
├── ui.json          可选（有界面就需要）
└── assets/          可选（图片、音频）
```

解析入口：`PluginPackage.read(file)` / `PluginPackage.read(bytes)`，
拒绝绝对路径、`..` 穿越、单文件 > 8MB、整包 > 16MB、文件数 > 256（防 zip bomb）。

## 2. manifest.json

```json
{
  "id": "pet_cat",
  "name": "宠物猫",
  "version": "1.0.0",
  "description": "一只会说话、会动的猫",
  "author": "",
  "minApiVersion": 1,
  "permissions": ["image.generate", "tts.speak", "dialog.read", "dialog.insert", "storage"],
  "extensions": [
    { "type": "ui_overlay", "slot": "chat_bottom", "config": "ui.json" },
    { "type": "prompt_hook", "stage": "before_send", "config": "logic.js", "entry": "onBeforePrompt" },
    { "type": "bar_button", "slot": "top_bar", "label": "总结", "entry": "onButtonTap" },
    { "type": "message_decorator", "entry": "decorateMessage" },
    { "type": "page", "label": "单词卡片", "entry": "renderPage" }
  ]
}
```

校验规则见 `ManifestValidator`（一次性收集全部错误）：

| 字段 | 规则 |
| --- | --- |
| id | 必需，`^[a-z0-9_]{2,32}$` |
| name | 必需，非空，<= 64 |
| version | 必需，`MAJOR.MINOR.PATCH[-pre][+build]` |
| description / author | 可选字符串，<= 512 / 64 |
| minApiVersion | 必需整数，1..`PluginApiVersion.CURRENT` |
| permissions | 可选，必须在白名单内且不重复（image.generate / tts.speak / http.request / dialog.read / dialog.insert / storage） |
| extensions | 可选，<= 32；type ∈ 五类；按类型校验 slot / label / stage / entry / config |
| extensions[].config | 必须是包内真实文件（省略时默认 `logic.js`），ui_overlay 还要求 `.json` |
| extensions[].entry | 合法函数名，且能在 `logic.js` 中静态找到（装载后还会运行时兜底） |

`ui_overlay` 的 slot：chat_top / chat_bottom / chat_float；`bar_button` 的 slot：top_bar / bottom_bar；
`prompt_hook` 的 stage：before_send。

## 3. JS 引擎（Mozilla Rhino）

`org.mozilla:rhino:1.7.14`，纯 Java，Android 可用，无 NDK 依赖。每个插件一个独立 Scope：

- `initSafeStandardObjects`：没有 `Packages` / `java` / `JavaAdapter`；
- 全禁 `ClassShutter`：任何 Java 类对脚本不可见，宿主也无法把 Java 对象塞进脚本；
- 解释模式 + 指令计数观察者：单次调用超过 `scriptTimeoutMillis`（默认 3000ms）立即中断；
- 能力只能通过注入的 `host` 对象获取。

## 4. 宿主能力（JS 同步外观）

```js
var r = host.generateImage({ prompt: '一只猫', width: 512, height: 512 });
if (r.ok) host.log(r.path); else host.log(r.code + ' ' + r.message);
```

可用：`generateImage / speak / httpRequest / readDialog / insertDialog / storageGet / storagePut / requestUiRefresh / now`。
所有失败都返回 `{ok:false, code, message}`（`permission_denied` / `host_error` / `host_timeout`），不向脚本抛异常。

同步外观用 `BlockingSuspend`（`startCoroutine` + `CountDownLatch`，不引 kotlinx-coroutines）实现。
限制：必须在非主线程调用；宿主实现若要求主线程继续调度会死锁；等待超时后放弃等待，被放弃的挂起任务仍会在后台跑完。

## 5. prompt 钩子

```kotlin
interface PromptHookRunner {
    fun runBeforeSend(plugin: PluginRuntime, currentPrompt: String, meta: PromptMeta): String?
}
data class PromptMeta(val timestampMillis: Long, val classIndex: Int, val userInput: String)
```

JS 侧 `onBeforePrompt(ctx)` 收到 `{prompt, meta}`，返回字符串即替换，返回 null/undefined 表示不改；
另有 `ctx.now()`（宿主可伪造时间）、`ctx.storage.get/set`、`ctx.log(msg)`。

`PromptHookPipeline.apply(plugins, prompt, meta)` 按传入顺序串行改写同一份 prompt；
任一插件抛异常 / 超时只跳过该插件并记入 `skipped`，绝不让发送流程失败。

## 6. 状态持久化

```kotlin
interface PluginStateStore { fun read(pluginId: String): String?; fun write(pluginId: String, json: String) }
```

主 app 用 prefs 实现。`ctx.storage` 建在它之上（一个插件一个 JSON 对象，key-value）。
