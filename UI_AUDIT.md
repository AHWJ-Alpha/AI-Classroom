# AI Classroom UI/UX 重构 · 阶段 0 仓库审计报告

- 审计日期：2026-11-06（本机时间）
- 工作区：D:\AI Classroom，分支 main，HEAD `6c4c28e Release AI Classroom 3.3.0`
- 方法：完整通读 `AI-Classroom_设计提示词.md`（568 行）与 `app/src/main/java/com/aiclassroom/app/MainActivity.kt` 全文（3749 行，分 9 段读取）；构建/资源/Manifest 逐文件读取；关键结论以 grep 交叉验证；基线编译实测执行。
- 审计性质：只读。未修改任何 .kt / .xml / .gradle 文件；唯一新增文件为本报告。

---

## ① 项目地图

### 1.1 模块与构建配置

| 项 | 值 | 出处 |
|---|---|---|
| 模块结构 | 单模块 `AI Classroom` → `:app` | settings.gradle.kts（rootProject.name = "AI Classroom"，include(":app")） |
| Gradle | wrapper 9.0.0（distributionUrl gradle-9.0.0-bin.zip） | gradle/wrapper/gradle-wrapper.properties |
| AGP / Kotlin | 8.6.1 / 2.0.20（含 kotlin compose 插件） | build.gradle.kts L3-L5 |
| SDK | compileSdk 35，minSdk 26，targetSdk 35 | app/build.gradle.kts L8-L16 |
| 版本 | versionCode 37，versionName 3.3.0 | app/build.gradle.kts L14-L15 |
| Java | source/targetCompatibility 17，jvmTarget 17 | app/build.gradle.kts compileOptions/kotlinOptions |
| 依赖 | core-ktx 1.13.1、activity-compose 1.9.3、lifecycle-runtime-ktx 2.8.6、compose ui 1.7.4、material3 1.3.0、material-icons-extended 1.7.4、ui-tooling(debug) | app/build.gradle.kts dependencies 块 |
| 明确没有 | navigation-compose、lifecycle-viewmodel、retrofit/okhttp、room、datastore（依赖表中不存在） | app/build.gradle.kts dependencies 块（全量核对） |
| 签名 | release 无条件签名，支持环境变量/本地 keystore（signing/ 目录不在版本库） | app/build.gradle.kts buildTypes.release |

### 1.2 源码与资源清单（app/src/main 全量）

- **唯一 Kotlin 文件**：`app/src/main/java/com/aiclassroom/app/MainActivity.kt`，3749 行（wc -l 实测）。全部 UI 与业务逻辑在此文件。
- **Manifest**：`app/src/main/AndroidManifest.xml` —— 权限仅 `INTERNET`、`VIBRATE`；唯一组件 `.MainActivity`（LAUNCHER，exported，configChanges=orientation|screenSize|keyboardHidden，无深链接 intent-filter）。
- **资源**：`res/values/strings.xml`（仅 app_name）、`res/values/styles.xml`（Theme.AIClassroom，父主题 Material.Light.NoActionBar，硬编码 statusBarColor #F6F8FB、navigationBarColor #FFFFFF、windowLightStatusBar true）、`res/values/colors.xml`（仅 launcher_bg #FF5FA2）、`res/drawable/ic_launcher_foreground.xml`、两个 mipmap-anydpi-v26 启动图 xml。**没有任何 UI 颜色/字符串/排版资源被 Compose 使用**——所有文案、颜色硬编码在 MainActivity.kt。
- 构建产物目录 app/build、build、.gradle 均被 .gitignore 忽略（git status 未显示）。

### 1.3 实际 UI 技术栈与入口

- Kotlin + Jetpack Compose（Material3 1.3.0），单 Activity 架构。
- 入口：`class MainActivity : ComponentActivity()`，MainActivity.kt **L152-157**；`setContent { AIClassroomTheme { AIClassroomApp() } }` L155。
- 顶层枚举 `Tab`（课堂/分支/记忆/知识库/设置，5 项，L159-165）定义全部一级页面。
- 代码规模实测：**39 处 `@Composable` 文本 = 35 个 @Composable 函数（独立注解行）+ 4 处参数类型位**；顶层函数 **130 个**（正则 `^(private )?(suspend )?fun`，含扩展与 suspend 函数）；顶层私有声明（fun/val/const/class/data class/enum/object）合计 186 个。
  - *与任务简报差异*：简报称"约 45 个 @Composable、约 118 个顶层函数"，实测为 35 个 Composable 函数 / 130 个顶层函数，以实测为准（统计口径差异，见⑨）。

### 1.4 状态管理方式（关键结论）

- **无 ViewModel、无 StateFlow/SharedFlow、无 rememberSaveable、无 Navigation 库**：grep `ViewModel|StateFlow|SharedFlow|collectAsState|rememberSaveable|NavHost|navigation` 在 MainActivity.kt **零匹配**。
- 全部 UI 状态为 Composable 内 `remember + mutableStateOf`：`remember {` 45 处、`mutableStateOf` 64 处、`mutableStateListOf` 15 处、`LaunchedEffect` 8 处。
- 应用级状态集中在根 Composable `AIClassroomApp()`（**L281-781**）的局部变量：tab L285、classIndex L286、input L287、classMenuOpen L288、jumpToMessageIndex L289、examSession L290、showNewDialog L291、activeBranchIndex L292、branchInput L293、branchLoading L294、updateInfo L295、showManualDialog L296、saveNotice L297、modelStatus L298、isLoading L299、immersiveMode L300、homeQuote L301、classes（mutableStateListOf）L302、models L303。
- 副作用：`rememberCoroutineScope` L304 + `scope.launch`；记忆后台任务 `memoryJobs`（IdentityHashMap<Classroom, Job>）L305，watermark L306；网络 LaunchedEffect：更新检查 L315-324、首页格言 L326-330。
- 各 Screen 内还有自己的局部 remember（如 ModelScreen L1969-2002 共 30+ 个草稿态字段）。

### 1.5 数据层（持久化 / JSON / 网络边界）

- **持久化** `ClassroomStore`（**L2473-2523**）：
  - SharedPreferences 名 `ai_classroom_14`（L2474）；主数据文件 `filesDir/ai_classroom_14_classes.json` + 临时文件 `...tmp`（L2475-2476），原子写（Files.move ATOMIC_MOVE，失败降级普通 move，L2510-2515），失败时写入 prefs `classes_backup`（L2517）。
  - prefs key：`classes`、`classes_backup`、`class_index`（L2490/2520）、`seen_release_notes_<版本>`（L2492-2496）、`last_update_prompt_day`（L2498-2505）。
- **JSON 序列化**：`Classroom.toJson()` **L2573-2589**（一个超长 put 链，**含明文 apiKey**）；反序列化 `JSONObject.toClassroom` **L2525-2571** + `toMessages/toBranches/toChapters/toMemoryCards/toFiles` L2591-2618；旧数据兼容：legacy welcome 消息过滤 L2592、legacy interfaceMode L3469-3473、modelChain 缺省回退 L2544。**全部 JSON 字段名与 key 冻结。**
- **数据类**：ChatMessage L167、BranchClass L168、KnowledgeFile L169-176、ConversationChapter L177、MemoryCard L178-190、LearningMemoryPack L191、ExamQuestion/ExamSession L192-193、UpdateInfo L194、ClassroomConfig L210-263、Classroom L265-277。
- **网络边界**（全部 HttpURLConnection 手写，无三方 HTTP 库）：
  | 能力 | 函数 | 行号 | 端点 |
  |---|---|---|---|
  | 非流式对话 | callChat | L2714-2728 | POST {baseUrl}/chat/completions |
  | 流式对话 SSE | callChatStream | L2730-2768 | 同上，Accept: text/event-stream |
  | 模型链 fallback | callChatWithFallback / callChatStreamWithFallback | L2770 / L2834 | 按 chatModels() 顺序逐个尝试 |
  | 图片理解 | callVision / callVisionWithFallback | L2863 / L2851 | 同上，image_url data URL |
  | TTS | callTts | L2903-2916 | POST {ttsBaseUrl}/audio/speech |
  | 模型列表 | fetchModels | L2628-2638 | GET {baseUrl}/models |
  | 版本更新 | checkGitHubUpdate | L2652-2672 | api.github.com releases/latest |
- 请求体/解析：buildJson L3293（temperature 0.35、reasoning_effort 条件字段）、buildVisionJson L3305、parseStreamDelta L2929、正则提取 content L2726/2876、readBody L3288、isApiFailure L2924。**以上全部冻结。**

### 1.6 图片 / 文件 / 音频 / 权限 / 系统 Intent

- 图片选择：`ActivityResultContracts.GetContent()` `image/*`（**L546-548**）→ sendImageMessage L516-544（contentResolver 读流 → base64 data URL L525-529）。
- 文件导入：两个 `OpenMultipleDocuments` launcher（资料 **L1884**、角色卡 **L1887**）→ importFile **L1843-1883**（DISPLAY_NAME 查询 L1845-1849、扩展名/MIME 判定 L1850-1857、类型白名单 L1858-1863、JSON 规范化 L1868、PNG 元数据抽取 L1869、内容读入 KnowledgeFile L1873-1879）。
- 音频：TTS 落盘 `cacheDir/ai_classroom_tts.mp3` + `MediaPlayer`（**L2884-2900**），每次新建 MediaPlayer，completion/error release。
- 触觉：`performHapticFeedback` 4 处（L1879、1885、1888、1917）。
- 权限：Manifest 仅 INTERNET、VIBRATE；**无运行时权限请求**（文件走 SAF，不需要存储权限）。
- 系统 Intent：**grep `startActivity|Intent(` 零匹配——应用内没有任何 startActivity / 外部系统 Intent**（更新弹窗只展示 URL 文本 L1382，不跳转浏览器）。
- 系统栏：**grep `WindowInsetsController|systemUiVisibility|WindowCompat` 零匹配**——无系统栏隐藏/沉浸 API 调用；仅用了 `statusBarsPadding()`（L588）。

### 1.7 主题、颜色、字体、图标、组件复用现状

- **双层 MaterialTheme 结构**：
  - 外层 `AIClassroomTheme` **L3323-3328**：固定 lightColorScheme（primary=Blue…），包在 setContent 最外层（L155）。
  - 内层 `MaterialTheme(colorScheme = appColorScheme(palette, darkInterface))` **L576-578**：位于 AIClassroomApp 的 Scaffold 之前，覆盖主界面全部 colorScheme 槽位（appColorScheme **L3336-3399**，dark/light 双分支）。外层仅对内层未设置的槽位（error 系、light 分支 scrim）起回退作用——**双层结构冗余且易混淆**。
- **调色板**：`AppPalette` 数据类 **L197-209**（page/surface/card/ink/muted/button/onButton/outline/primary/secondary/accent 11 色）；`paletteFor` **L3401-3438**（dark L3411-3422：page #181B20、surface #22262D、card #2B3038、ink #E8EBEF、muted #AAB2BD、outline #48515D；light L3424-3436：page #F5F6F8、surface #FFF、card #F0F2F5、ink #20242A、muted #697382、outline #D7DCE3；button 非 custom 一律 #667085 L3404-3407）；`isDarkInterface` **L3330-3334**（interfaceMode=system/light/dark，归一化 L3463-3467）。
- **全局裸色**：Page/Ink/Muted/Blue/Green/Purple **L3719-3724**——命名与实际不符（Blue=#596579、Green=#65758A、Purple=#667085 均为灰系）；`Muted` 被 11+ 处直接用作文字色（L1380、1477、1490、1535、1588、1593、1634、1697、2261、2269、2282），绕过 palette，深色模式下不随 palette.muted(#AAB2BD) 变化。
- **形状**：`AppShapes` **L3610-3616**（panel 20dp / card 16dp / control 14dp / button 12dp / menu 28dp）；但另有 **20 处裸 `RoundedCornerShape`**（8/10/12/14/16/999dp，如 L594、1066、1169、1551、1709、2331），AppShapes 引用 22 处——两套并存。
- **排版**：**46 处硬编码 fontSize**（10sp–22sp），无 Typography 对象；lineHeight 散见 14/18/21sp；字体仅默认无衬线 + `FontFamily.Monospace` 用于代码/公式（L2374、2377、2441）。
- **图标**：material-icons-extended，代码中使用的图标见 import 区 L50-71（School、AccountTree、Memory、Bookmarks、Key、Tune、Send、Image、Quiz、RecordVoiceOver 等）；AI 消息头像为字符 "✦"（L1292、1670），非图标资源。
- **组件复用**：已有的可复用件——InfoCard L2447、SettingsCard L2310、AppFilterChip L2326、FloatingIconAction L1164、AiThinkingRow L1331、ButtonColorPreview L2349、ColorSwatch L2292、MarkdownText L2365；**但消息卡片存在两套实现**（MessageCard L1258 / SimpleMessageCard L1652），Dialog 存在两套配色路径（见⑤）。

---

## ② 页面与导航清单

**导航模型**：无 Navigation 库、无 NavHost、无路由字符串。导航 = 单 Activity 内 `tab` 状态（L285）+ 底部 NavigationBar 切换 + 少量 overlay/dialog 状态。**无深链接**（Manifest 仅 MAIN/LAUNCHER）。

| # | 页面/层级 | 组合函数 | 行号 | 进入方式 | 离开/返回 |
|---|---|---|---|---|---|
| 1 | 主课堂（对话） | ClassScreen | L948-1039 | 底部 Tab「课堂」L160；空课堂走 EmptyClassroomHome L1042-1087 | 切 tab |
| 2 | 分支列表 | BranchScreen | L1571-1604 | 底部 Tab「分支」L161 | 切 tab |
| 3 | 分支聊天 | BranchChatScreen | L1607-1649 | 分支卡片「进入分支/继续对话」L1598-1599 | 「分支列表」按钮 L1632（onBack L704 置 activeBranchIndex=-1） |
| 4 | 记忆（章节/导图） | MemoryScreen | L1685-1726 | 底部 Tab「记忆」L162；双击章节跳主课堂 L705-708 | 切 tab |
| 5 | 知识库 | KnowledgeScreen | L1837-1921 | 底部 Tab「知识库」L163 | 切 tab |
| 6 | 设置 | ModelScreen | L1958-2289 | 底部 Tab「设置」L164 | 切 tab |
| 7 | 课堂菜单（左侧滑入抽屉） | ClassroomMenu + AnimatedVisibility | L784-915 / 覆盖层 L733-749 | 顶栏 Tune 按钮 L622-627；主课堂页右滑手势 >80dp L662-671 | 再点按钮/左滑关闭 L667；选中课堂 L738-742 |
| 8 | 沉浸式考试（全屏 Overlay） | ExamOverlay / ExamQuestionList / ExamSidePanel | L1414-1442 / L1445-1500 / L1503-1543 | AI 回复含考试块时自动触发（detectExamSession L496-499，examSession L750-764） | 「退出」按钮 L1463 |
| 9 | 发布说明 Dialog | ReleaseNotesDialog | L1346-1367 | 首次打开/版本更新 showNewDialog L291、L765-770 | 「知道了」 |
| 10 | 更新 Dialog | UpdateDialog | L1370-1387 | 每日一次 GitHub 检查 L315-324、L771-773 | 「知道了」 |
| 11 | 使用手册 Dialog | UserManualDialog | L1390-1411 | 设置页「打开使用手册」L2284、L774-776 | 「关闭」 |
| 12 | 课堂改名 Dialog | RenameClassroomDialog | L918-930 | 菜单项/标题点击 L869、883、895-904 | 保存/取消 |
| 13 | 课堂简介 Dialog | EditClassroomSummaryDialog | L933-945 | 菜单「简介」L884、905-914 | 保存/取消 |
| 14 | 知识文件全文 Dialog | KnowledgeFileDialog | L1941-1955 | 点击文件行 L1905、1908 | 「关闭」 |
| 15 | 删除文件确认 Dialog | 内联 AlertDialog | L1909-1919 | 长按文件行 L1930 | 删除/取消 |
| 16 | 消息长按菜单 | MessageActionMenu | L1311-1328 | 长按消息 L1268-1272 | 选中/点外 |
| 17 | 模型/推理下拉 | ChatControls 内 DropdownMenu | L1112-1121、L1132-1148 | 输入区图标 L1109、L1124 | 点外 |
| 18 | 课堂列表内联操作 | — | L882-888 | 每行 5 个 TextButton（改名/简介/复制/复制配置/删除） | — |

**返回行为（实测）**：grep `BackHandler|onBackPressed` **零匹配**——系统返回键无任何拦截：任何层级按返回均为 Android 默认行为（直接退出应用），不会关闭课堂菜单、不会从分支聊天退回分支列表、不会关闭全屏考试 Overlay（除非各层自身是 Dialog 由系统自动处理）。*（"按返回退出应用"为无拦截代码的默认行为推论，未在真机验证——待确认。）*
**手势**：主课堂页水平拖拽开关课堂菜单 L662-672；记忆导图双指缩放/拖动 L1748-1753；涂鸦板拖画 L1553-1562。

---

## ③ 关键组件清单

### 3.1 主题与基础设施
| 组件/对象 | 行号 | 说明 |
|---|---|---|
| AIClassroomTheme | L3323-3328 | 外层固定 light 主题（冗余，见⑤-7） |
| appColorScheme(palette, dark) | L3336-3399 | 内层 dark/light 双 ColorScheme，全部槽位由 palette 派生 |
| paletteFor / isDarkInterface | L3401-3438 / L3330-3334 | 调色板工厂与明暗判定 |
| AppPalette | L197-209 | 11 色数据类 |
| AppShapes | L3610-3616 | 5 档圆角 |
| 全局裸色 Page/Ink/Muted/Blue/Green/Purple | L3719-3724 | 绕过 palette 的历史色 |
| 颜色工具 | luminanceValue L3475、compositeOn L3482、compositeOnWhite L3476（**无调用点，死代码**）、blendOnWhite L3440（**无调用点，死代码**）、parseHexColor L3449、argbToHex L3448 |

### 3.2 通用 UI 组件
| 组件 | 行号 | 复用情况 |
|---|---|---|
| InfoCard | L2447-2457 | 分支/记忆/知识/考试/手册高频复用（约 20+ 调用点） |
| SettingsCard（可折叠分组） | L2310-2323 | 设置页 5 个分组 L2005/2055/2100/2149/2251 |
| AppFilterChip | L2326-2345 | 设置页全部选项 chip |
| FloatingIconAction | L1164-1176 | 对话页回顶浮动按钮 L1031 |
| AiThinkingRow | L1331-1343 | 主课堂 L1012 与分支 L1645 的加载指示 |
| ButtonColorPreview / ColorSwatch | L2349-2362 / L2292-2307 | 皮肤设置 |
| MarkdownText（自实现） | L2365-2382 | 支持标题/列表 bullet/**粗体**/`代码`/简单公式行；不支持表格、引用、链接、嵌套列表；代码块仅渲染围栏行本身（L2374） |

### 3.3 对话组件
| 组件 | 行号 | 说明 |
|---|---|---|
| ChatInputBar | L1179-1254 | 图片按钮+输入框+发送按钮+可选 ChatControls |
| ChatControls | L1090-1159 | 模型切换、深度思考/推理强度、沉浸开关、沉浸内明暗切换 |
| MessageCard（主课堂） | L1258-1308 | 长按菜单、双击朗读 L1270、「↳ 从这里开分支」按钮 L1286/L1302 |
| SimpleMessageCard（分支） | L1652-1681 | 无长按菜单、无朗读、无分支操作（与主卡片样式不一致） |
| MessageActionMenu | L1311-1328 | 开分支 / 删除此处及之后 |
| 考试组件 | L1414-1568 | ExamOverlay（横竖屏两套布局 L1431-1440）、DrawPad 涂鸦 L1546 |

### 3.4 Dialog（7 处 AlertDialog）
两套配色：**palette 版** ReleaseNotesDialog L1346、UserManualDialog L1390（带 AppShapes.panel）；**MaterialTheme.colorScheme 版** RenameClassroomDialog L918、EditClassroomSummaryDialog L933、UpdateDialog L1370、删除确认 L1910、KnowledgeFileDialog L1941。

**不存在的组件类别**：无 BottomSheet / ModalBottomSheet（grep 零匹配）、无 Snackbar、无 Scaffold 顶栏 AppTopBar 抽象（顶栏为手写 Row L587-628）。

---

## ④ 功能入口与状态归属表（对照提示词第 1 节）

状态归属缩写：**根状态**=AIClassroomApp remember（L285-306）；**局部**=各 Screen 内 remember；**持久化**=ClassroomStore；**业务**=顶层私有函数。

| 提示词第 1 节能力 | 核实结果 | 入口（UI 触点） | 核心实现（行号） | 状态归属 |
|---|---|---|---|---|
| 主课堂与分支课堂 | **存在** | 底部 Tab「课堂」/「分支」L160-161；消息下「↳ 从这里开分支」L1286/L1302；长按菜单 L1318 | sendMessage L478-514；sendBranchMessage L550-574；分支创建 L694-703；branchSystemPrompt L434-450 | 根状态 tab L285、activeBranchIndex L292；branches 挂在 Classroom L265-277；持久化 L2578-2582 |
| AI 流式对话 | **存在** | 发送按钮 L1240-1249；AiThinkingRow L1331 | callChatStream L2730-2768（SSE 逐 delta 回写 room.messages[assistantIndex] L492-495）；fallback L2834 | 根状态 isLoading L299；消息列表 mutableStateListOf |
| 多模型配置 + 备用模型 + 推理设置 | **存在** | 设置页「模型模块」L2004-2053（服务商/URL/Key/模型名/模型优先级/深度思考/深度模型/获取模型）；输入区模型与推理图标 L1109-1148 | ClassroomConfig L210-263；orderedModels/chatModels L242-260；modelChain fallback L2841-2848；buildJson reasoning_effort L3296-3301 | 每课堂 config 持久化 L2588；模型下拉临时状态局部 L1100-1101 |
| 长期记忆 | **存在** | Tab「记忆」L162；无需手动入口（后台自动生成） | scheduleMemoryBuild L452-476（8 条消息批量+45s 延迟 L3744-3745）；buildLearningMemoryPack L2977；recallMemoryCards L3065；prompt 注入 L413-418、L3561-3568 | 根状态 memoryJobs/memoryWatermarks L305-306；持久化 memoryCards L2585 |
| 章节摘要 | **存在** | 记忆页「章节」chip L1693-1695；双击跳转 L1708 | buildConversationChapters L2960-2975；本地兜底 localChapterSummary L3266；prompt 注入 L384-386、L408-412 | Classroom.chapters L274；持久化 L2586 |
| 上下文管理 | **存在** | 无独立 UI（隐式） | systemPrompt L379-432；promptMessagesForRoom L3540-3580（状态包+召回）；截断常量 L3726-3747 | 纯业务函数（冻结区） |
| 本地知识库 | **存在** | Tab「知识库」L163；「批量上传资料」L1897；点开全文/长按删除 L1905、L1930 | importFile L1843-1883；recallKnowledgeSnippets L3154-3182；注入 L3569-3576 | Classroom.files L275；持久化 L2587 |
| Markdown / TXT 文件导入 | **存在** | 同上「批量上传资料」（MIME 白名单含 text/plain、text/markdown 等 L1897） | 类型校验 L1858-1863（reference 限 md/txt/text/*） | 同上 |
| 角色卡导入（MD/JSON/PNG） | **存在** | 「批量导入角色卡」L1898（.md/.json/.png/.txt MIME L1898） | normalizeCharacterCardJson L3103-3114；extractPngCardText L3116-3123；注入 prompt L380、L419-424、formatCharacterCards L3125 | KnowledgeFile.kind="character_card" L175；持久化含 kind L2587 |
| 世界书 | **存在**（手写文本型） | 设置页「讲师人格与模式模块」→「手写世界书」多行输入 L2260 | config.worldBook L234；注入 L398-402 | 每课堂 config，持久化 L2588 |
| 图片理解 / 图片分析 | **存在** | 输入栏图片按钮 L1210-1221 → imageLauncher L546 | sendImageMessage L516-544；callVision L2863；识图独立配置「多模态模块」L2054-2098 | 根状态无（随发随处理）；vision 配置持久化 L2549-2552 |
| TTS 语音播放 | **存在** | 双击 AI 回复 L1270；设置页「TTS 模块」L2100-2147（含长时自动朗读开关 L2123） | speakText L2880-2901；callTts L2903-2916；autoRead 触发 L502、L541、L572 | ttsAutoRead 在 config L230，持久化 L2558 |
| 沉浸式对话 | **存在**（仅应用内） | 输入区眼睛图标 L1155-1157；沉浸内明暗切换 L1151-1154 | immersiveMode 根状态 L300；topBar/bottomBar 条件渲染 L580、L632 | 根状态（不持久化，重启复位——事实，未见存储） |
| 考试工具（源码多于提示词清单） | **存在** | 无静态入口，AI 触发（isExamRequest L3197、EXAM_TOOL_PROMPT L2941-2958） | detectExamSession L3202；ExamOverlay L1414；提交 packExamAnswers L3249；「不会」生成讲解分支 L754-763 | 根状态 examSession L290（不持久化） |
| 多课堂管理 | **存在** | 课堂菜单 L784-915（搜索 L824、新建 L841、改名/简介/复制/复制配置/删除 L883-887、滑入手势 L662-671） | addClassroom L348-355、deleteClassroom L362-377、copyConfigFrom L357-360 | classes 根状态 L302；持久化 L2507-2522 |
| 课堂自动命名 | **存在**（隐式） | 无入口（首轮对话后自动） | generateClassroomIdentity L2781-2805、触发 L504-512 | 写回 Classroom.name/summary |
| 首页格言 | **存在但从未显示**（缺陷，见⑤-1） | —（EmptyClassroomHome 接收 quote 参数 L1048 但函数体 L1058-1086 未使用） | generateHomeQuote L2640-2650、LaunchedEffect L326-330 | 根状态 homeQuote L301 |
| 更新检查 / 发布说明 | **存在** | 自动弹窗 L765-773；手册入口 L2284 | checkGitHubUpdate L2652、ReleaseNotes L1346 | showNewDialog L291、updateInfo L295；prefs key L2492-2505 |

---

## ⑤ 视觉与交互问题清单（均有行号出处）

1. **首页格言是死参数 + 白跑网络请求**：EmptyClassroomHome 声明 `quote` 参数（L1048）但函数体（L1058-1086）从不使用；homeQuote 却由 LLM 生成（L326-330 → generateHomeQuote L2640）。空课堂每次首个空状态可能多发一次 API 调用且结果不展示。（修复是否算"新增展示"需调度方确认——待确认。）
2. **浮动回底按钮逻辑自相矛盾**：L1031-1037 `visible = isAtBottom`、`icon = if (isAtBottom) North else South`——按钮只在"已在底部"时出现（此时点它回顶），用户滚到中部时按钮消失，South 分支永远不可见，无法从中部一键回底。
3. **新消息强制拉回底部**：L1001-1003 `LaunchedEffect(room.messages.size)` 每次消息数变化即 `animateScrollToItem(bottomIndex)`，用户上翻查看历史时新消息到达会被强拉回底（流式文本回写不改 size，流式中不跳动；但用户+占位两条 add 会触发）。与提示词阶段 3 特别要求 4 冲突。
4. **分支页图片按钮是死键**：L1647 `ChatInputBar(... onImage = {})`——分支聊天页渲染图片按钮（L1210-1221）但点击无任何效果。
5. **分支列表双按钮同动作**：L1598-1599「进入分支」与「继续对话 N」onClick 都是 `onSelect(index)`，纯视觉重复。
6. **消息卡片双实现不一致**：用户气泡——主 MessageCard 无边框 alpha 0.08（L1278-1281）vs 分支 SimpleMessageCard 有边框 alpha 0.10（L1658-1659）；AI 卡片——主 alpha 0.52（L1299）vs 分支 alpha 0.72（L1675）；分支消息无长按菜单/无双击朗读/无开分支（SimpleMessageCard L1652-1681 无 actionModifier）。
7. **双层主题 + 双取色路径**：外层 AIClassroomTheme（L3323）被内层 MaterialTheme（L576）覆盖大半；组件取色一半 palette 直传（ClassScreen/MessageCard/ChatInputBar），一半读 MaterialTheme.colorScheme（ClassroomMenu L807/816、MemoryScreen L1711-1712/1736-1737、ExamOverlay L1430、ModelScreen 全页、5 个 Dialog），同页混用，改造时极易漏改。
8. **Dialog 两套样式**：palette 版（L1346、L1390）vs colorScheme 版（L918、933、1370、1910、1941），圆角/标题色/正文色不统一。
9. **圆角体系分裂**：AppShapes 5 档（L3610-3616）之外 20 处裸 RoundedCornerShape（8/10/12/14/16/999dp：L594、863、1066、1169、1551、1709、2331…），同级卡片圆角 8/12/16dp 并存。
10. **字号无规范**：46 处 fontSize 硬编码（10/11/12/13/17/18/20/22sp），无 Typography token；行高 14/18/21sp 散落。
11. **全局裸色命名误导且绕过主题**：Blue/Green/Purple 实为灰系（L3722-3724）；`Muted`(#697382) 被 11+ 处直接当文字色（L1380、1477、1490、1535、1588、1593、1634、1697、2261、2269、2282），深色模式下不使用 palette.muted(#AAB2BD)，暗底对比度低于主题方案（对比度数值待确认）。
12. **系统栏硬编码浅色**：styles.xml statusBarColor #F6F8FB + windowLightStatusBar true——深色界面下状态栏仍为浅色（styles.xml L4-5）；沉浸模式无系统栏 API（grep 零匹配），仅隐藏应用内 bar。
13. **返回键零处理**：无 BackHandler（grep 零匹配）——课堂菜单/分支聊天/考试 Overlay 打开时按系统返回为默认退出行为（推论，未真机验证——待确认）。
14. **交互可发现性**：记忆页跳转靠双击（L1708、L1783），仅一行 13sp 提示（L1697）；「删除此处及之后」只在长按菜单（L1323），可见按钮只有开分支（L1286/1302）；课堂菜单行内 5 个 TextButton 挤在一行（L882-888）。
15. **设置页保存时机不一致**：多数字段为本地草稿 + 显式「保存」按钮（L2042、2078、2127、2238、2271），但「界面明暗」chip 点击即 onConfig 即时保存（L2177-2181）；ModelScreen 30+ 个 `remember(config.X)` 草稿（L1969-2002），切换课堂靠 key 重置（remember(config.provider) 等），机制能工作但认知负担高。
16. **空状态单薄**：空课堂首页只有日期胶囊 + 输入框（L1058-1086），无引导文案/无格言；空分支、空知识库、空记忆有提示卡（L1588、1640、1903、1700），层级不一。
17. **Markdown 渲染能力有限**：MarkdownText（L2365-2382）为自实现行级解析——代码块只把围栏行本身渲染为等宽（L2374），代码内容行按普通文本处理；无表格/引用/链接/图片渲染。改造时不得"顺手增强"（属能力变更，需授权）。
18. **API Key 明文持久化**：toJson L2588 将 apiKey/visionApiKey/ttsApiKey 明文写入 filesDir JSON 与 SharedPreferences。安全风险，但属持久化冻结区——只能记录，不得在 UI 重构中改动。
19. **无停止/重新生成/编辑消息操作**：grep `停止|重新生成|regenerate` 零匹配；isLoading 仅禁用发送（L1242）；流式请求无取消机制（grep cancel() 仅 L455 记忆任务）。提示词 §4.1 列举的这些"已有操作"在源码中不存在（见⑨）。
20. **死代码**：ThemePreset（L196，仅声明）、blendOnWhite（L3440）、compositeOnWhite（L3476）均无调用点（grep 全文件仅定义行）。

---

## ⑥ 高风险文件/区域清单

| 级别 | 区域（文件:行号） | 风险说明 |
|---|---|---|
| ★★★ | MainActivity.kt **L281-781**（AIClassroomApp 根函数，约 500 行） | 20+ 根状态、业务调用（sendMessage L478、sendImageMessage L516、sendBranchMessage L550）、全部 overlay 挂载、手势混在一处；动布局极易误伤业务回调 |
| ★★★ | **L1958-2289**（ModelScreen，约 330 行） | 30+ 草稿态 + 5 个 config.copy 保存按钮（L2042/2078/2127/2238/2271）——改动字段绑定即可能改变保存行为（功能冻结） |
| ★★★ | **业务冻结区**：网络 L2628-2916、Prompt L379-450 与 L3489-3606、记忆 L452-476 与 L2960-3094、持久化/序列化 L2473-2619 与 L3293-3308、考试解析 L3197-3259、NSFW L3313-3317 | 一字不动；UI 改造的调用点（如 L677-704 的回调）只能保持签名 |
| ★★ | **主题层 L3322-3488 + AppShapes L3610 + 全局裸色 L3719-3724** | 阶段 1 主战场；paletteFor/appColorScheme 改错会导致全局色偏（所有页面） |
| ★★ | **副作用敏感点**：滚动 L1001-1008、手势 L662-672、网络 LaunchedEffect L315-330、记忆 Job L452-476 | 重组/生命周期敏感，易引入重复请求或滚动跳动 |
| ★★ | styles.xml（状态栏硬编码）+ Manifest | 主题与系统栏联动需同时改 res（当前阶段是否允许改 xml 需授权——待确认） |
| ★ | 单文件 3749 行本身 | 全项目唯一源文件，任何阶段的 diff 都集中于此，冲突面最大；按提示词"尽量局部修改"，阶段 1-3 不应做文件拆分搬迁（拆分属高风险架构改动，未授权不执行） |
| — | 工作区未跟踪文件：AI-Classroom_设计提示词.md、releases/AI-Classroom-{3.0.0,3.1.0,3.2.0,3.3.0}.apk | 属用户文件，Git 纪律禁止清理 |

---

## ⑦ 建议重构顺序（对应提示词阶段 1/2/3）

> 总原则：全部改动限定在 MainActivity.kt 展示层（+必要时 styles.xml，需授权）；不改任何函数的业务语义与回调数据；每阶段结束跑 assembleDebug 并列变更文件。

### 阶段 1：设计系统建立（先做，影响面最小、复用收益最大）
**先动（纯组件/Token，签名不变）**：
1. 扩展 token 区：AppShapes（L3610，保持现有 5 档值不改语义）、新增 Typography/Spacing 对象（新代码先用，存量不强换）；L3719-3724 全局裸色保留但标注 deprecated，新代码禁用。
2. 收敛基础组件：InfoCard L2447、SettingsCard L2310、AppFilterChip L2326、FloatingIconAction L1164、AiThinkingRow L1331、ButtonColorPreview L2349、ColorSwatch L2292——只调内部样式，参数列表不动。
3. 提取统一对话框基座（复刻现有两套视觉为一套 palette 方案），替换 7 处 AlertDialog 的容器/圆角/标题色（L918、933、1346、1370、1390、1910、1941）——文案与按钮行为逐字保留。
**后动（全局敏感，最后改并全页回归）**：appColorScheme L3336 / paletteFor L3401 / AIClassroomTheme L3323（明确双层主题去留需授权——待确认）。
**不动**：L576 内层 MaterialTheme 的挂载位置、interfaceMode/themeMode 语义、L2148-2249 皮肤保存逻辑。

### 阶段 2：主页面与导航
**先动**：顶栏 L579-631（层级与溢出）、底部导航 L632-655（保持 5 Tab 与全部入口）、ClassroomMenu L784-915（行内 5 按钮 L882-888 改为更清晰的分层，保留全部动作）、空状态 EmptyClassroomHome L1042-1087（补引导信息；"是否显示格言"需授权）、Dialog 组（接阶段 1 基座）。
**保留**：滑入手势 L662-672、tab 切换语义、返回栈现状（不引入 BackHandler 除非授权，属行为变更）。
**候选修复（需逐项授权）**：浮动按钮 visible 逻辑 L1031-1037（明显自相矛盾，建议直接修）；分支页死图片键 L1647（移除还是接通需决策）。

### 阶段 3：对话与课堂页面（最高频、最高风险）
**先动**：ClassScreen L948-1039 滚动策略（L1001-1003 按提示词阶段 3 要求 4 改为"仅用户在底部时跟随"——行为变更，按提示词授权执行）、contentPadding 硬编码 L1010（76/142dp）。
**再动**：MessageCard L1258-1308 与 SimpleMessageCard L1652-1681 样式统一（**保留两者的操作集差异**：分支无开分支/删除，不得反向添加或删除）、ChatInputBar L1179-1254、ChatControls L1090-1159、AiThinkingRow L1331、MessageActionMenu L1311。
**最后动**：MarkdownText L2365（仅视觉层排版：字号/行高/代码块底色；不改解析规则）。
**明确不碰**：sendMessage/sendBranchMessage/sendImageMessage、callChat* 全家、promptMessages*、MarkdownText 的解析语义、考试触发链 L496-499。
**验证**：每步 assembleDebug；关键交互（长按菜单、开分支、双击朗读、沉浸切换、模型切换）逐一回归。

### 后续（阶段 4/5 提示，不在本次授权范围）
设置页分组美化、记忆/知识库页、全局一致性与深浅色/横屏/字体放大回归（提示词 §6 阶段 4/5）。

---

## ⑧ 基线编译结果（真实执行）

| 次序 | 命令 | 结果 | 说明 |
|---|---|---|---|
| 1 | bash 下 `.\gradlew.bat assembleDebug`（后台） | **exit 127**，耗时 <1s | 原样错误：`/usr/bin/bash: line 1: .gradlew.bat: command not found`——MSYS bash 无法直接执行 .bat，属调用方式错误，非编译失败 |
| 2 | `cmd //c gradlew.bat assembleDebug`（后台，完整构建） | **BUILD SUCCESSFUL in 4m 41s**；35 actionable tasks: 9 executed, 26 up-to-date | 警告原样记录：① `app/build.gradle.kts:96:5 kotlinOptions 已弃用`；② `MainActivity.kt:1248:39 Icons.Filled.Send 已弃用，建议 AutoMirrored`；③ Gradle 9 → 10 弃用特性提示；④ "Starting a Gradle Daemon, 8 busy Daemons could not be reused"。产物：app/build/outputs/apk/debug/app-debug.apk（16,525,753 字节）。注：后台作业包装器状态未及时回收为 completed，但完整输出与 APK 产物均已核验 |
| 3 | `cmd //c gradlew.bat assembleDebug`（前台复跑取退出码） | **exit code 0**，BUILD SUCCESSFUL in 4s（35 tasks 全部 up-to-date） | 复跑确认退出码与产物稳定 |

- **基线结论：项目按原有方式可成功编译，exit code = 0；无编译错误，仅有上述 4 条警告。**
- 本阶段唯一非只读动作即上述编译（仅写 build/ 目录）。

---

## ⑨ 与提示词第 1 节描述的差异（源码为准）

### 9.1 第 1 节能力逐项核对
| 提示词第 1 节条目 | 结论 | 依据 |
|---|---|---|
| 主课堂与分支课堂 | 存在 | L159-165、L948、L1571、L550-574 |
| AI 流式对话 | 存在 | L2730-2768、L492-495 |
| 多模型配置、备用模型及推理设置 | 存在 | L2004-2053、L2834-2849、L1132-1148 |
| 长期记忆、章节摘要、上下文管理 | 存在 | L452-476、L2960-2992、L3540-3580 |
| 本地知识库 | 存在 | L1837-1921、L3154-3182 |
| Markdown / TXT 文件导入 | 存在 | L1897、L1843-1883 |
| 角色卡导入（MD/JSON/PNG） | 存在 | L1898、L1858-1872、L3103-3123 |
| 世界书或类似知识条目 | 存在（手写文本字段，非条目管理器） | L2260、L234、L398-402 |
| 图片理解/图片分析 | 存在 | L516-544、L2863、L2054-2098 |
| TTS 语音播放 | 存在 | L2880-2916、L2100-2147 |
| 沉浸式对话体验 | 存在（仅隐藏应用内顶/底栏；**无系统栏级沉浸 API**，grep 零匹配） | L300、L580、L632 |

### 9.2 提示词其他章节提及、但源码中不存在的能力（标「不适用」）
- **停止生成 / 重新生成 / 编辑消息**（§4.1 列为"已有消息操作"）：**不适用**——grep 零匹配，源码无这些入口；流式无取消（仅记忆任务 cancel L455）。
- **消息附件**（§4.1"附件和键盘交互"）：**不适用**——输入区仅图片按钮（L1210），无通用附件。
- **深链接**（阶段 2"深链接（若存在）"）：**不适用**——Manifest 无 intent-filter 深链。
- **最近会话/历史记录独立页**（阶段 2）：**不适用**——历史即当前课堂消息列表（L1010），课堂菜单是课堂列表而非消息历史（L784）。
- **BottomSheet**：**不适用**——全文件零匹配。
- **ViewModel / StateFlow / SharedFlow / collectAsState**（§1、§5.3）：**不适用**——零匹配，状态全为 remember（见①1.4）。
- **Navigation 路由**：**不适用**——tab 状态切换，无 NavHost/路由。
- **Repository / UseCase / 数据库**（§5.2 架构边界）：**不适用**——不存在该分层；数据层 = ClassroomStore + 顶层网络函数。
- **动态配色/Material You**（§3.2"动态配色支持"审计线索）：**不适用**——paletteFor 仅 system/light/dark + 自定义单色（L3401-3438）。

### 9.3 数量口径差异（以实测为准）
| 简报/背景描述 | 实测 | 统计口径 |
|---|---|---|
| 45 个 @Composable | **39 处 @Composable 文本 = 35 个 Composable 函数 + 4 处参数类型位** | grep -o / grep -c '^@Composable$' |
| 约 118 个顶层函数 | **130 个顶层 fun**（含 suspend 与扩展函数） | grep -E '^(private )?(suspend )?fun ' |
| 入口 Activity 第 152 行 | **一致**：L152 | 通读 |
| 主题体系约 3323-3488 行 | **一致**：AIClassroomTheme L3323 → compositeOn L3487（AppShapes L3610、裸色 L3719-3724 为主题关联区，超出该窗口） | 通读 |

### 9.4 源码中存在、但提示词第 1 节未列明的能力
考试工具（L1414、L3197-3259）、多课堂管理（L784-915）、自动课堂命名（L2781-2805）、GitHub 更新检查（L2652）、首页格言生成（L2640，未展示）、记忆思维导图（L1730-1834）、高效模式 NSFW 过滤（L3313-3317）——重构时同样受功能冻结保护。

---

## 附：验收自查
- 本报告所有结论均可追溯到文件+行号；grep 零匹配类结论已注明"grep 零匹配"；无法静态确认的运行时行为（返回键退出、深色对比度数值）标注「待确认」。
- 未修改任何 .kt/.xml/.gradle 文件（以 git status 自证，见随报告提交的回报）。
- 编译结果为真实执行输出（⑧）。
