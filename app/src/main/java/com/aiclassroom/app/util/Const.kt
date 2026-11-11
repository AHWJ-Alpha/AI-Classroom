package com.aiclassroom.app.util

/**
 * 容量上限与提示词预算。
 *
 * 这些数字原来散在 MainActivity 各处（有的在文件头、有的夹在函数之间），
 * 改一个预算要在 8000 行里翻半天。集中到这里之后，调参只看一个文件。
 */

internal const val CORE_PREMISE_LIMIT = 16
internal const val MEMORY_CARD_RECALL_LIMIT = 8
internal const val MEMORY_CARD_BUILD_MESSAGE_LIMIT = 36
internal const val RECENT_MESSAGE_CONTEXT_LIMIT = 32

/** 媒体能力被用户在设置里关掉时的提示。 */
internal const val MEDIA_DISABLED_HINT =
    "音乐插件已在「设置 → 插件 → 音乐」关闭：本次没有读取也没有控制任何播放器"

/** 当前没有活跃的播放会话时的提示。 */
internal const val MEDIA_NO_SESSION_HINT =
    "当前没有正在播放的播放器。先打开酷狗 / B 站音乐等 App 播放一首，再回来试。"

internal const val TOOL_LOOP_MAX_ROUNDS = 5

internal const val TOOL_LOG_TAG = "ToolLoop"

internal const val NO_CONTENT_REPLY = "模型没有返回内容。"

internal const val RELEASE_NOTES_TEXT = """
# AI Classroom 4.0.0

## 开屏动画
- 新增像素开屏 CG：3.0 的紫蓝「3」消散，4.0 的红色「4」重组，收进方框地球后变形为 NEW。
- 播完停在 NEW 上，点击进入说明；同一版本之后不再播放。

## 代码结构
- 主文件从 8478 行压到 7332 行，数据模型、调色板、文本、记忆、提示词、网络、媒体等拆成 13 个独立文件。
- 依赖方向改为单向：model ← util ← ui ← MainActivity。

## 插件
- 插件的数字设置项改为滑条，拖动即时生效。
- 沉浸模式与音乐改为系统插件：可以停用，不能卸载。
- 顶栏插件按钮改为纯图标。

## 音乐
- 时间条彻底重写：不再跳变、不再归零，换歌时正确重置。
- 悬浮窗可在设置里调大小；小圆与大浮窗互斥切换，位置共享。

## 修复
- 修复长按对话消息导致应用崩溃（原生段错误）。
- 修复请求失败的红色提示永久停留：改为 8 秒自动收起，并带手动关闭。
- 修复进入课堂时的滚动动画。
- 长按菜单新增「复制」。

"""

internal const val USER_MANUAL_TEXT = """
# AI Classroom 使用手册

## 快速开始
第一次使用时，先进入设置页，展开“模型模块”，填写 Base URL、API Key 和至少一个可用模型名并保存。回到主课堂后，输入你想学习的内容，例如“从零开始学 C 语言指针”，AI 会围绕这个课堂持续教学。

## 主课堂
主课堂是一门课程的主线。你可以输入学习目标、追问问题、让 AI 出例题、讲解代码或总结章节。课堂内容、对话、章节索引、摘要和配置都会保存在本地。

刚进入应用或新建课堂时，主课堂顶部只显示浅色日期，中部保留输入框。输入学习要求并发送后，会自动进入正式主课堂对话界面。

主课堂输入框右侧的图片按钮可以上传照片。上传后，应用会把图片交给设置页里的识图或转述模型分析，并把结果保存进当前课堂。

## 分支课堂
长按或点击对话下方的“从这里开分支”，可以从任意一段主课堂对话开始一条平行支线。分支会带上创建时的主课堂上下文，之后的所有问答只保存在分支中，不会复制、打包或改写主课堂。

适合在不打断主线的情况下追问背景知识、补基础、展开例子或处理临时问题。

## 记忆
记忆页用于快速查找长期对话。应用会在后台按批次整理课堂内容，生成章节标题和一句话摘要，不会在每一句对话后阻塞前台回复。

全览模式会生成实时向量思维导图，章节按摘要相似度自动连接；章节模式可双击章节跳回主课堂对应位置。

长对话回答时，应用会把最近连续对话作为最高优先的上下文，同时插入自动提炼的常驻极简前提，并根据当前问题召回相关关键词记忆卡。世界书卡片会保存可见性、知道者、不知道者和证据；在故事或角色扮演中，作者私密、心理活动、暗中计划不会被未知角色直接利用。长期记忆、世界书卡片和章节索引都是压缩参考；若它们与最近对话冲突，模型会被要求优先遵守最近对话；如果上下文不足，应先说明不确定或追问，而不是编造内容。

回答边界会额外判断用户是否明确要求继续、推进、策划事件或往后发展。未检测到这类请求时，AI 会避免在末尾添加强引导性建议；如果用户明确要求推进，或当前剧情确实需要承接，AI 会结合世界书、角色可知信息和剧情因果生成完整合理事件。

## 知识库
知识库可直接读取 `.md` 和 `.txt` 文件；角色卡还支持 `.json` 和 `.png`，并可批量导入。普通资料会按你当前的问题、最近对话和课堂主题检索少量相关片段，而不是把所有文件粗暴塞入提示词；点击文件可查看全文，长按可删除。

“导入角色卡”支持 Markdown（`.md`）、常见角色卡 JSON（`.json`）和带文本元数据的 PNG（`.png`），也支持一次选择多个文件。Markdown 每个角色建议使用一个文件，并用一级标题写角色名，再用“身份”“性格”“语言风格”“动机”“关系”“不可违背”等二级标题描述。角色卡会作为作者级人物参考常驻，用于保持人物口吻、关系与行为边界；它不会让角色自动知道其他角色的秘密，最近连续对话已经发生的事实优先于角色卡。

这套“角色卡常驻 + 当前资料检索 + 世界书 + 长期记忆”会让单个小说项目获得接近微调的设定一致性和连续性，但不会改变模型本身参数。想要更稳定的效果，请把世界规则写进世界书、每位角色单独建卡，并保存 2—3 段你期待的叙事示例作为普通资料。

## API 与模型
模型模块用于保存普通课堂对话的服务商、Base URL、API Key 和模型名。它支持自动获取模型，也可以手动输入模型名。

模型优先级支持一行一个模型。应用会先调用第一行模型，如果连接失败或接口返回错误，会自动尝试下一行模型。

深度思考模式开启后，会优先使用单独填写的深度思考模型。关闭后，应用只按普通模型优先级调用。

## 多模态图片
多模态模块拥有独立的服务商、Base URL、API Key 和识图/转述模型名称。它可以与普通对话模型不同。主课堂上传照片时会优先调用多模态模块；如果该模块未填写 API Key，则会回退使用模型模块的 API Key。

## TTS
TTS 模块用于保存语音服务配置，包括服务商、API Key、Base URL、模型和音色。预设服务商可快速填入常见 Base URL，也可以选择自定义。获取模型按钮会尝试从兼容接口读取模型列表；如果失败，可以手动填写模型名。

双击 AI 讲师回复会调用 TTS 朗读。开启“长时开启 AI 回复朗读”后，新的 AI 回复会在生成完成后自动朗读。

## 讲师人格
讲师人格提示词可以自定义，例如大学教授、企业工程师、考研老师、幽默导师或二次元导师。讲师名字会实时显示在主课堂和分支课堂中；讲师对你的称呼会写入提示词，让模型在教学时按这个称呼与你互动。手写世界书可以保存长期固定设定、课程边界、学习习惯、角色知识边界或必须遵守的课堂规则。保存后当前课堂会持续使用这些设定。

讲师人格提示词拥有最高优先级。即使长期记忆、知识库、世界书卡片和分支上下文不断变长，应用也会在系统提示词首尾重复人格优先级提醒，要求模型优先遵守用户保存的人格提示词和教学风格。

## 高效模式
高效模式用于过滤 NSFW 等不适合学习场景的内容，默认开启。它本质上是健康模式，适合学习、自习和考试场景。

## 界面与皮肤
皮肤模块可切换对话方向、按钮颜色和界面明暗。默认强调色是橙色（按钮 `#FF7A1A`，插件强调元素 `#E85D04`），可通过调色盘或 Hex 输入换任意颜色；除按钮和可点击强调外，背景、卡片和正文不会被皮肤色染色。

默认界面采用低饱和灰蓝色和中性背景，减少高亮色与多层阴影；界面明暗可选择自动跟随系统、白色或黑色，应用会自动保持文字反色，保证可读性。

## 多课堂
在主界面从左向右滑出课堂菜单，可以切换课堂、新建课堂、删除任意课堂，也可以复制其他课堂配置。点课堂标题可切换，点删除只会删除该课堂，不会先跳进那个课堂。每个课堂都可以有独立 API、模型、人格、皮肤和知识库配置。

## 考试工具
考试不是普通入口。AI 明确要进行考试、测试或模拟测验时，应用会自动进入沉浸式考试界面。

题目由“前提”和“问题”组成。用户在每个问题后填写答案并提交批改。标记“不会”后，这道题会自动生成讲解分支，方便考试结束后继续学习。

## 插件
插件是 AI 可以自己决定要不要调用的工具。你不用点，模型觉得需要时才会用；具体调什么、什么时候调，由它自己判断。

五组插件各管一件事：
- 考试工具：把题目写进当前考试，也能改标题、加题、删题、标记“不会”。
- 文件工具：在应用私有目录里列目录、读、写、删文件，出不了这个目录。
- 网络工具：让 AI 去读一个 http/https 地址，比如接口文档或网页。
- 配置工具：让 AI 改模型名、BaseURL 和温度，省得你自己翻设置。
- 生图：按你的描述生成图片，自动存到本机“files/images”。

生图怎么配（“设置 → 插件 → 生图详细配置”）：
- OpenAI：选 OpenAI Images，填 Key，模型填 dall-e-3 之类，尺寸填 1024x1024。
- SD WebUI 或本地端口软件：选 SD WebUI，Base URL 填 http://127.0.0.1:端口。只要那个软件是 SD WebUI 兼容的就能直接用。
- 自定义 HTTP：选“自定义 HTTP”，填自己的接口地址，按 {"prompt":"{{prompt}}"} 的格式提交。

明文 HTTP：本应用已放行 http，方便连本机或局域网里的服务；但 http 是明文传输，链路上能看到内容，建议只填本地或可信局域网地址，走公网时优先用 https。

密钥与安全：AI 看不到你的 API Key，只能看到“已配置”这类状态，也没有工具能改它；配置工具能改的只有模型、BaseURL 和温度。

关掉某个插件：进“设置 → 插件”，把那一行的开关关掉即可，立即生效，AI 再调用会被直接拒绝，不会留下任何改动。

## 本地保存
所有课堂、分支、对话、知识库摘要、记忆章节、设置和考试记录都会保存在手机本地。手机重启或应用版本更新后，数据仍会保留。
"""

internal const val CORE_PREMISE_CONTEXT_LIMIT = 1800

internal const val WORLD_BOOK_CONTEXT_LIMIT = 3000

internal const val MEMORY_CARD_STORE_LIMIT = 120

internal const val CHAPTER_PROMPT_LIMIT = 32

internal const val CHAPTER_CONTEXT_LIMIT = 5000

internal const val CHARACTER_CARD_CONTEXT_LIMIT = 7000

internal const val KNOWLEDGE_CHUNK_SIZE = 1100

internal const val KNOWLEDGE_SNIPPET_RECALL_LIMIT = 4

internal const val KNOWLEDGE_SNIPPET_CONTEXT_LIMIT = 5200

internal const val BRANCH_CONTEXT_LIMIT = 24

internal const val BRANCH_CONTEXT_LIMIT_CHARS = 5000

internal const val CHAPTER_SIZE = 12

internal const val MEMORY_BATCH_MESSAGE_COUNT = 8

internal const val MEMORY_BATCH_DELAY_MS = 45000L

internal const val MAIN_MEMORY_PREFIX = "主课堂记忆："

internal const val DAY_MS = 86_400_000L

internal const val GITHUB_RELEASES_URL = "https://github.com/AHWJ-Alpha/AI-Classroom/releases"

internal const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/AHWJ-Alpha/AI-Classroom/releases/latest"

internal const val MESSAGE_IMAGE_MAX_SIDE = 1080

internal const val FULLSCREEN_IMAGE_MAX_SIDE = 2048
