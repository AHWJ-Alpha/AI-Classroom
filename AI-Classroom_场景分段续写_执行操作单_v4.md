# 交给 A 执行：场景分段续写操作单 v4

项目目录：`D:\AI Classroom`

本单基于 `3128a49` 的实际源码编写。用途是修复并接通产品，不是继续讨论架构。按下面指定的文件、接口约束、顺序和验收执行。本单对本轮“场景分段续写”要求具有优先级，之前提示词的用户数据保护、人物边界及版本兼容要求继续有效。

## 0. 固定范围，不再重新选方案

本轮必须交付：

```text
来源对话的 SCENE 项目
→ 解析并冻结篇幅和有效输入
→ 生成第 1 段，校验并持久化
→ 生成第 2 段时中断
→ 重建仓库，新的执行者接管同一候选
→ 从缺失分段继续，不重写已保存正文
→ 组装完整单元
→ 审查全部正文，提取全部正文的状态
→ 原子提交正式 revision 和固定作品版本
→ 结果卡回写、阅读可见
```

固定技术决定：

1. **草稿加入现有 ClassroomStore / FileClassroomStore，使用同一 JSON、同一锁和同一 transaction。** 不新增独立 `FileDraftStore` 文件仓库，不使用 SharedPreferences 保存草稿，不引入数据库迁移工程。
2. `DraftStore.kt` 改为使用 `ClassroomStore` 的受保护草稿操作层。内存和文件测试都执行同一套业务守卫，不维护两套不同的守卫逻辑。
3. **本轮在 SCENE 真实入口启用分段执行。** 其余类型保留现有功能并回归，不为它们额外扩展新玩法。篇幅解析修正对所有类型一致有效，但不要因此改坏原有教学内容块。
4. 段落是内部生成批次。读者仍看到一节连续场景，最多一个可见标题。
5. 不改应用 ID、签名或用户连接配置；不删除真实存档；不向真实项目注入测试内容；不读取或打印凭据。
6. 不 push、不发布、不 reset、不改写历史。除非本会话已有明确提交授权，否则保留可审查 diff；提交数量不作为进度目标。
7. 分阶段编译测试，但**阶段完成后继续下一阶段，不再询问“继续还是推送”**。有外部阻塞只阻塞相关实测，其他代码和自动化验证继续完成。
8. 禁止把类型、接口、未调用的函数、内存测试或总测试数增加当作功能完成。本轮最终要有上面的真实入口全链路证据。

开始只做一次基线检查：`git status --short`、`git log -3 --oneline`、适用 AGENTS.md 和下面列出的源码。若 HEAD 已变化，先核对已做内容，不覆盖用户修改。不要重复写设计提案。

## 1. 必须修改或核对的文件

所有 Kotlin 路径以项目实际包路径为准。

```text
feature-classroom/.../domain/LengthPolicy.kt
feature-classroom/.../domain/UnitDraft.kt
feature-classroom/.../domain/RunLease.kt
feature-classroom/.../repository/DraftStore.kt
feature-classroom/.../repository/Ports.kt
feature-classroom/.../store/FileClassroomStore.kt
feature-classroom/.../store/StoreCodec.kt
feature-classroom/.../exec/UnitExecutor.kt
feature-classroom/.../exec/RunRecovery.kt
feature-classroom/.../exec/RunEngine.kt
feature-classroom/.../exec/RunFinalizer.kt
feature-classroom/.../commit/UnitCommitter.kt
feature-classroom/.../prompt/PromptTemplates.kt
feature-classroom/.../content/UnitContentParser.kt
feature-classroom/src/test/.../commit/InMemoryStore.kt
app/.../classroom/ClassroomService.kt
app/.../classroom/ClassroomRunWorker.kt
app/.../classroom/ClassroomRunManager.kt
app/.../classroom/ui/ClassroomScreen.kt
```

允许新增的职责文件：

- `exec/SceneContinuation.kt`：多段生成编排；不要把所有逻辑继续塞进 UnitExecutor。
- `content/SegmentNormalizer.kt`：类型、正文计数、ID 和事件引用规范化。
- `domain/DraftInputVersion.kt`：可重复计算的输入指纹及冻结数据。
- `prompt/SceneContinuationPrompt.kt`：首段、续写、收束、局部修订的提示词。
- 与上述职责直接对应的测试。

这些名称可以按现有包习惯调整，职责不能拆成一套新的平行平台。不要扩展插件、生图、Ren’Py、分支或考试功能。

## 2. 第一批操作：先修正篇幅并接到真实输入

### 2.1 修改 LengthPolicy

删除 `1_500..20_000` 对用户输入的静默夹取。

固定规则：

- 明确“每节 200 字”：目标就是 200，不得变成 1500。
- 明确“每节 5000 字”：目标 5000。
- “全篇 10 万字、20 节”：总目标 100000，单节基础分配 5000。
- 同时指定单节与全篇时保留两个原始值，单节按显式值；总量冲突要在规划预览说明，不偷偷覆盖。
- 单节未指定，但已给全篇总量：按**实际正式总纲节数**分配，不只靠正则猜节数；尾差分配到后面的节。
- “一个月”“22 天”只描述时间，不是字数。
- 支持阿拉伯整数和小数万字，以及“总共 100000 字”“每章 1.2 万字”。不必新增通用中文数字 NLP；解析不到的表达显示采用默认值。
- 未指定篇幅时使用现有默认值：SCENE 4000、NOVEL 6000、TEXT_GAL 2500、TEACHING 2000。
- 单节目前支持 1～20000 个正文计数单位。超过范围时在规划/开始入口返回明确的暂不支持原因，**不能修改成上限后继续假装接受**。整部总目标可以通过多个单元累计超过 20000。
- `perCallTarget` 仍以 2500 为推荐上限，但这只是文本目标，不能宣称可保证不截断。
- `maxSegments` 是安全上限，不是成功条件。基础估算外留出至少两个补写段，最多 12 个分段；达到上限仍不足必须待处理。
- 规划预览显示采用的单节目标及来源。恢复时读取草稿冻结的策略，不能随默认值变动而改变候选。

篇幅读取优先级固定：用户明确结构化设置（如已有）→ spec 中明确篇幅与原始需求 → 类型默认。仅保存 policy 对象而没有传给执行器不算完成。

正文计数固定为：Narration.text 和 Dialogue.text 中非空白、非 Unicode 标点的 Unicode code point 数；中文字符、字母和数字分别计数。不计标题、说话者标签、JSON、事件、摘要。提供唯一计数函数，累积和最终验收都调用它，不接受模型返回的字数或调用者传入的 `chars`。

默认接受范围为目标的 90%～120%，使用向上取整的最小值。200 字的最小接受值为 180。用户明确“至少 N 字”时最低值就是 N，不应用 90%。超过最大值时停止继续扩写，进入审查或明确超量处理，不丢段落凑数字。不要为了末尾几十字无限补写。

### 2.2 输出预算

阅读现有 `ModelPolicyResolver.resolve` 和 `ModelRequests.fromPolicy` 的实际签名，按真实参数传递。不要再猜参数名，编译失败后回滚成提示词硬编码。

单次文本目标要根据实际输出 token 上限、JSON/事件开销和模型能力缩小。没有 tokenizer 时采用保守估算并注明是估算；不能把 2500 字等同于 2500 token。供应商/模型已有较低上限不得被覆盖。主模型降级后重新适配。

首段和续写提示词不再出现冲突的“每次至少 3000 字”。它们必须分别写明“整节目标”“本次建议新增篇幅”“当前累计篇幅”。

## 3. 第二批操作：同库草稿及受保护写入

### 3.1 扩展现有仓库

在 `Ports.kt` 的 ClassroomStore 增加草稿底层读写端口，项目与单元联合定位：

```kotlin
fun draft(projectId: ProjectId, unitId: UnitId): UnitDraft?
fun saveDraft(draft: UnitDraft)
fun removeDraft(projectId: ProjectId, unitId: UnitId)
```

这些是仓库内部原语。产品执行器不能绕过 DraftStore 守卫直接写它们；测试建立 fixture 可用。InMemoryStore 和 FileClassroomStore 都实现，**不得给接口添加“默认空实现”掩盖未接线**。

FileClassroomStore 的 State、snapshot 回滚、load、persist 一起增加 drafts，key 使用 projectId + unitId 的无歧义组合。StoreCodec 增加完整双向编解码，复用已有 ContentBlock、UnitEvent 的编码；如现有 codec 为 private，只做必要的 internal 提取。

旧存档没有 drafts 时读为空。旧项目、正式 revisions、作品清单、事件、阅读进度不受影响。新 schema 如升级，保持旧字段兼容，不清库。

### 3.2 UnitDraft 需要增加的状态

保留现有类型，补齐以下信息，不只新增字段而不序列化：

```text
projectId / unitId / candidateId / revisionId
inputVersion（不可变指纹）
冻结的有效输入与篇幅策略（不含凭据）
segments（按序号连续排列）
candidateVersion（内容有修订时递增）
phase：GENERATING / ASSEMBLED / REVIEWING / PATCHING / READY_TO_COMMIT / COMMITTED / INVALIDATED
totalChars（程序重算）
summary / usedScenes / 当前时间与地点 / 未完成动作 / 剩余目标
active runId / owner / fencingToken
累计调用计数、连续失败计数、修订计数
审查结果及其对应 candidateVersion
状态提取结果及其对应 candidateVersion
committedRevisionId / 最后错误 / 更新时间
```

每个 DraftSegment 保存：index、已规范化 blocks/events、实际正文长度、内容 checksum、累计上下文摘要、对应 attempt、完成/收束标识和创建时间。

text 若保留，应从 blocks 生成，不允许 text 与 blocks 各写一份互相矛盾。摘要是派生内容，正文是事实来源。

### 3.3 输入指纹不能随执行者变化

`DraftInputVersion` 用 SHA-256 和确定性序列化产生，不能使用 Kotlin hashCode 作为跨进程内容身份。包含：

- 项目类型、spec 实际版本及有效内容；
- outline 版本及本节有效计划；
- 单元 directive、目标覆盖、用户编辑版本或等效内容指纹；
- 来源快照 ID/版本及内容身份；
- 当前单元的前置正式 revision、正确前置人物/事实状态；
- 长度策略、会影响输出的非敏感模型策略及模板版本。

排除 runId、owner、token、当前阶段状态、更新时间；这些变化不应导致正常接管后候选失效。不得包含 API key、Authorization 或敏感额外请求头。

开始时在仓库事务内读取并冻结。恢复时重新计算当前有效输入并比较；一致才接管，不一致标 INVALIDATED，旧正文保留为草稿但不得拼到新候选。生成各段、审查和修订只使用同一冻结输入，不能每次读 latestSnapshot 造成混用。

### 3.4 重写 DraftStore 守卫接口

替换当前只有 unitId 的 append/clear。写请求必须带以下完整身份：

```kotlin
data class DraftWriteGuard(
    val projectId: ProjectId,
    val unitId: UnitId,
    val candidateId: String,
    val expectedInputVersion: String,
    val expectedCandidateVersion: Int,
    val runId: RunId,
    val owner: String,
    val fencingToken: Long,
)
```

固定产品操作：开始候选、接管候选、追加分段、替换指定分段、保存阶段结果、作废候选、完成提交。都走同一守卫，不能只保护 append。

每次写操作在 **ClassroomStore.transaction 内**重新校验以下条件：

1. project 与 unit 实际归属一致。
2. candidateId 与当前候选一致；创建新候选使用 expectedOldCandidateId 做比较交换，首次创建预期为空；重复创建同一候选返回已有结果，不清空分段。
3. expectedInputVersion 与草稿一致，且当前有效输入仍一致。
4. candidateVersion 与预期一致。
5. run 确实属于此项目；owner 精确相等；run 状态为允许执行的状态。
6. token 精确等于该 run 的 token 和项目当前有效执行 token；租约到期时间严格大于 now。token=0 不自动放行，owner=null、租约=0 不自动视为有效。
7. 新分段序号必须等于 nextIndex。已有同序号同 checksum 的重复响应返回 AlreadyApplied，不能累加字数；同序号不同内容返回 Conflict。不能跳号。
8. 校验通过后才变更状态并持久化。写盘失败回滚，失败原因保留，不回报成功。

使用类型化结果区分 Applied、AlreadyApplied、StaleCandidate、StaleInput、StaleExecutor、Conflict，不靠任意字符串驱动恢复逻辑。

接管在同一事务中更新草稿执行身份。接管发生、新执行者尚未保存下一段时，旧执行者也必须已经失效；不能仅比较草稿最后写入 token。

旧 `InMemoryDraftStore` 的低 token 比较逻辑不得继续作为产品安全边界。改成对统一 DraftStore 操作层的测试适配，或删除重复实现并更新测试。

### 3.5 相关租约修复必须一起落实

RunLease 的续租、提交校验、终态与草稿校验使用同一项目级判定。续租不得先恢复旧 owner 的租约再检查 token，也不能允许过期 owner 自行复活。

运行期间每 30 秒心跳一次，在原来的执行协程下创建子协程；每次续租同时验证 run、项目、owner、token。心跳失败取消该执行者的网络与后续操作，不写新任务终态。

接管或释放后，旧执行者的 finally、延迟回调、状态更新和 RunFinalizer 都不能改变新任务。不要只修正文 commit 而遗漏其他写路径。

## 4. 第三批操作：接上真实的 SCENE 执行器

### 4.1 在 UnitExecutor 分流

现有 constructor 注入同仓库 DraftStore 操作层，Service 真实装配。SCENE 进入 SceneContinuation；其他类型保留原路径。

先修现有网络重试 bug：重试成功后不能再执行 `text = originalResult.content` 覆盖成功结果。整个调用统一选定一个有效 ModelCallResult，再解析并记录。初次、重试、修订、审查、状态提取都留 attempt，不漏记重试。

SCENE 流程固定为：

```text
正式单元已 READY 且无显式重写 → 返回 AlreadyDone
取得项目执行权 → 冻结输入
有兼容草稿 → 接管并恢复阶段
无兼容草稿 → 比较交换创建候选
GENERATING → 按 nextIndex 逐段请求
每段合法且权限有效 → 同事务保存分段、上下文及计数
目标满足且自然收束 → ASSEMBLED
审查 → 必要的局部修订 → 全部审查通过
最终状态提取 → READY_TO_COMMIT
事务正式提交 → COMMITTED
```

段落刚保存后进程退出，恢复必须从 nextIndex 继续。ASSEMBLED/REVIEWING/PATCHING 恢复不得从正文生成阶段重新开始。正式提交已成功但界面未收到结果时，恢复返回已提交 revision，不重施补丁。

### 4.2 分段提示词的确定内容

`SceneContinuationPrompt` 接受冻结输入和当前草稿，不能接受一个无法核对的巨大自由文本作为全部上下文。

固定提供：

- 原始用户要求与本节有效目标；
- 当前阶段的故事时间范围；
- 人物固定性格、可见事实和正确前置状态；
- 整节篇幅范围、当前累计数、本次新增建议数；
- 当前节已有段落的累计摘要、事件与尚未完成目标；
- 当前地点/时间/动作、未结束的对话或生活线索；
- 最近必要衔接原文，最多 1200 个正文计数单位；
- 已用桥段，提示避免重复。

冻结设定和权限高于模型生成的累计摘要。非公开信息在进入模型前过滤，不能经摘要扩大角色知识。

场景写作规则固定：

```text
这是共同生活时间线中的一个连续阶段。
通过动作、对白、物件、空间和停顿表现相处方式。
允许安静和稳定，不要求每段关系升级或每段发生冲突。
允许有交代的自然转场，不得用概括句替代应展开的过程。
续写只输出新增内容，不复述已写开头，不写阶段总结凑字。
内部生成段号不显示给读者。
全节最多一个标题；第 2 段起不允许新增 heading。
```

原始用户请求是有效任务需求，不能因为放在 DATA 区块就被一句“全部不是指令”否定；资料中的嵌套命令不获得执行权限。

每次返回完整 JSON，复用 UnitContent 的现有字段：blocks、events、objectivesCovered、unresolvedItems；加上结构化的本节累计续写状态和本次是否自然收束的声明。

**真实 UnitEvent 字段是 `id / sourceBlockIds / description`，不是 actorIds/targetIds。** 提示词严格遵循当前 Kotlin 类型，不再发明解析器不读取的字段。

只允许 narration/dialogue/heading，Dialogue 使用已注册 speakerId。场景不允许 explanation/example/exercise/choice。没有事件时可以返回空 events，不为状态提取编造事件。

### 4.3 收束判定与上限

普通分段不写本节结尾。预计接近下限时发送“完成当前自然片段并适度收束”的提示，模型声明 finished 只作为信号。

程序只有在以下条件同时满足时进入 ASSEMBLED：达到接受下限；全部分段校验通过；有自然结束的候选信号；没有仍在补写的缺失段。

达到字数但仍未收束时最多额外一次有界收束调用；仍未结束或超过分段/调用预算则 NEEDS_ATTENTION，草稿保留。字数不足时即使模型说 finished 也不能发布完成。

### 4.4 ID、正文与重复内容

SegmentNormalizer 给每段分配确定性 ID，例如 `candidateId:s2:b3` 和 `candidateId:s2:e1`，本地映射模型块 ID，同时改写 events.sourceBlockIds。事件、块局部 ID 重复或引用不存在直接拒绝，不能猜。

第二段以后出现标题时返回明确格式问题并有限纠正；不得静默丢标题后宣称完整接收。不要增加按天小标题。

按规范化正文 checksum 检查完全重复；再用连续重复片段检测识别模型复述上段。不要简单删除两段共同出现的短口头禅。重复严重时纠正当前段，不追加、不增加总字数；同一段连续两次无有效新增就停下并报告。

追加后重新从 blocks 统计 totalChars；修改已有段后重算全节，不信任调用方上报的 chars。

## 5. 第四批操作：失败分类与持久预算

候选固定预算：最多 12 个成功分段；每个当前段最多初次加 2 次重试；局部修订最多 2 轮；连续无有效新增最多 2 次；所有阶段总共最多 64 次应用层模型调用。计数在请求发送前持久化，恢复不清零；网关内部重试/降级保留 attempt 并受已有底层预算限制，不得绕过应用层限制形成无限循环。

每次模型调用前检查协程取消、执行权、输入版本和预算。结果回来后保存前再次检查。

结果处理固定：

| 结果 | 行为 |
|---|---|
| 正常结束、JSON 完整、结构合法 | 规范化并保存当前段 |
| 网络/超时 | 保留成功段，记录当前段失败；有界重试，耗尽后可恢复暂停 |
| finish_reason=length 或 incomplete transport | 不保存半段；缩小当前请求目标/调整允许的输出预算后有界重试 |
| 非法 JSON、错误块类型或引用 | 不保存；带具体错误纠正当前段，受同一预算约束 |
| 输入过期/候选被替换/执行权失效 | 立即停止，旧响应不得写入任何候选 |
| 无有效新增或重复正文 | 不累计，纠正；达到上限进入待处理 |
| 达到总预算/段数上限仍未完成 | 保留草稿，明确待处理，不能发布 READY |

不能用 finish_reason=length 之后拼接原始响应修 JSON。前一段成功的正文不可因当前段失败而删除。

连接取消需要实际验证。修正 AndroidHttpTransport 仅在 Job 完成后 disconnect 的时机问题，使用受取消控制的传输/独立阻塞任务和即时取消回调；不要仅换注释。用本地阻塞服务证明取消在读超时前终止，并且之后没有模型降级或草稿写入。

## 6. 第五批操作：完整审查、修订、状态与提交

### 6.1 审查覆盖所有正文

能在模型上下文预算内放下时审查整节完整 blocks/events，禁止 `take(4000)` 只取开头。

超预算时固定采用：每段完整正文审查 + 相邻段衔接检查 + 全局一致性审查。全局审查获得每段累计摘要、时间线、全部事件的有界索引、目标及知识边界；报告持久记录审查覆盖的全部 segment checksum，不得假称单次全篇逐字审查。

每段都必须实际被审查，只有总结没有原文检查不算通过。全局发现问题时回取涉及段原文，预算不足则待处理，不跳过。

审查问题绑定 candidateVersion 和 blockId。必要时局部修订一个段，完整保留前后衔接和全局目标。替换通过 DraftStore 受保护操作，递增 candidateVersion，重算 ID 映射、事件和累计长度，使该段及受影响的下游摘要、所有过时审查/状态结果失效。重审修改段、衔接与全局一致性，不直接重写整节或只保留开头 4000 字。

场景审查不得因为安静、没有冲突、没有关系升级、没有知识讲解就阻断。必须检查时间推进、固定人格、角色知情、重复桥段、最多一个标题和阶段目标。

### 6.2 状态提取依据最终正文

小候选一次提取完整正文；大候选按最终分段顺序提取，覆盖每段所有事件，并维护只在本候选内生效的临时状态。最后合并成一份待提交补丁，期间不修改正式人物状态。

每种操作字段必须齐全：SetRelationship.target、LearnFact.factId、AdjustStress.delta；使用真实已注册人物、可用事实、最终规范化事件 ID。没有变化输出合法空补丁。有部分非法操作则整个当前提取失败，有限纠正，不丢弃非法项后提交剩余部分。

多人物基础版本来自冻结的正确前置状态，不能从第一个人物复制。候选修改后旧补丁失效。历史重写使用历史前置状态，同时仍检查当前输入和依赖没有变更。

### 6.3 原子提交和跨 run 幂等

候选创建时固定 revisionId；正式提交幂等键使用 projectId/unitId/candidateId/candidateVersion，**不得包含会在恢复时变化的 runId**。

在现有 UnitCommitter.transaction 内重新读取并检查：候选身份、候选版本、输入指纹、当前单元编辑、依赖状态、有效执行者、审查覆盖、补丁基础和证据。现有校验若放在事务外，移到同一事务中，避免校验与写入间插入新编辑。

同一次事务写入：正式 revision、审查、事实/状态、摘要、checkpoint、单元 READY、作品版本清单，以及草稿 COMMITTED + committedRevisionId。不要先提交正式内容再无条件 clear(unitId)；那会误删新候选，也会在崩溃后产生恢复歧义。

恢复发现相同 candidateVersion 已 COMMITTED 时返回该 revision，不再请求模型、不重施状态、不重复创建作品版本。不同内容版本必须重新审查；不能沿用旧报告。

部分失败时旧正式版本保持可读，草稿不得冒充作品正文或被 ProjectRetrieval 当成正式事实。

## 7. 第六批操作：宿主、暂停恢复和阅读接线

真实产品路径必须依次证明：

```text
ClassroomScreen 的开始/继续/重写
→ ClassroomRunManager
→ ClassroomRunWorker
→ ClassroomService（共享仓库和 DraftStore）
→ UnitExecutor 的 SCENE 分流
→ SceneContinuation
→ UnitCommitter
→ outbox / 结构化卡片 / 指定版本阅读
```

不得只在测试里 new SceneContinuation，而产品入口仍旧走一次调用。

暂停可中断正在生成的当前段，保留之前已经确认持久化的分段；界面文案准确说明这个行为，不能写“当前单元写完才停”而实际立即取消。取消与系统中断分开：用户取消不自动重排；系统中断允许按持久任务规则恢复未提交阶段。

RunRecovery 只恢复失效执行者的单元阶段，不能删除候选、清掉调用预算或重置已经提交内容。有合法草稿时单元重新可调度，执行器根据草稿阶段接着跑。

UI 展示当前候选已保存段数、实际累计正文长度、目标范围、当前阶段/错误。不要根据历史任何 RUNNING 判定正在生成，也不要把 Worker 入队和真正开始混为一谈。

完成卡必须带结构化附件和实际 workVersion。只有完整单元正式提交才显示“完成”；暂停、取消、预算不足和待处理使用对应状态。检查新 run 的迟到清理不影响正在恢复的 run。

本轮不升级版本号、不增加新导航层级。保留既有最多一个标题的阅读效果，不把内部 JSON、段号和执行元数据显示为故事内容。

## 8. 固定测试清单：必须通过后才算完成

先更新旧测试中错误的断言，例如“每节 200 字应变 1500”，不能为了保留旧测试继续错误行为。新增测试必须失败于旧实现，不能只是断言字段存在。

### A. 篇幅与请求接入

1. 每节 200 字保留 200；每节 5000 字保留 5000；一个月不解析为字数。
2. 全篇 100000 字按正式 20 节分配；无显式节数也使用总纲节数；尾差正确。
3. 超过单节支持范围明确拒绝，不静默夹取。
4. 从宿主创建/规划到最终 ModelRequest，能看到整节目标与当前段目标；没有固定“每次不少于 3000 字”。
5. 较小输出 token 上限和模型降级时按真实能力适配，不把汉字数当 token。

### B. 文件与守卫

6. 新建 FileClassroomStore，写两段；丢弃所有对象，再建仓库后正文、事件、摘要、阶段、策略和预算完整回读。
7. 重复响应同序号同 checksum 为 AlreadyApplied，总字数不增加；内容不同和跳号被拒绝。
8. c1 请求在途，创建 c2 后 c1 响应不得写入 c2。
9. spec/outline/快照/directive/前置 revision 任一变化后旧输入不能追加或提交。
10. 新 owner 接管、尚未追加任何段时，旧 owner 已不能 start/append/replace/finish/clear。
11. token=0、伪造大 token、owner=null、错误项目、错误 run、到期 lease 都不能绕过实际授权检查。
12. 控制并发交错，两个执行者竞争同一下一段只有一个成功；不能靠随机 sleep。
13. 注入写盘故障，草稿和计数回滚；下一笔成功能保存；旧无 drafts 存档仍可读。

### C. 真实执行链

14. SCENE 至少三段才达到目标，最终 blocks/events 引用无冲突，正文计数正确。
15. 第一段保存、第二段网络失败；重建文件仓库、新 run 取得更高执行权后，继续同一 candidateId，只请求第二段及以后。
16. 当前段 JSON 截断/length 不写入；修复请求成功后只追加一次，前段 checksum 不变。
17. 重试成功内容不会被原失败结果覆盖。
18. 暂停/系统中断后阶段及预算保留；用户取消不自动重试。
19. 第二段新增 heading 或教学块被拒绝；最多一个标题；局部口头禅不误判为整段重复。
20. 完全重复/连续无新增、段数或调用预算耗尽时停下，不能 READY，草稿仍可恢复查看。
21. 分段审查覆盖所有段；尾段故意放入人格/知识边界问题能够被发现，不能只审开头或结尾。
22. 局部修订后旧报告和补丁不可提交，修改后长度与事件正确重算。
23. 合法无状态变化也能提交；多段非空补丁只对最终正文事件生效一次。
24. 正式提交后、响应返回前模拟崩溃；换 run 恢复无新模型请求、不重施状态、不重复 workVersion。
25. 阅读和项目检索只拿正式版本，旧作品可读，任务卡指向正确新版本。

关键集成测试优先用 FileClassroomStore 和确定性 FakeGateway/FakeTransport。只在 fake draft Map 上跑通不能算持久恢复验证。

必须补至少一条 App 宿主装配测试，证明 Worker/Service 实际注入同库草稿并进入 SCENE 分段路径。没有可测接缝时增加最小的 gateway/clock/store 注入点，不能硬塞调试逻辑到 release。

## 9. 验证命令、设备与交付

每完成一批跑针对性测试，发现失败立即修到通过，再继续下一批，不在中间宣布本轮完成。

最终运行：

```powershell
.\gradlew.bat :feature-classroom:test :app:testDebugUnitTest :app:assembleDebug --no-daemon --rerun-tasks
```

涉及 Android 生命周期、后台控制与真实宿主装配的内容增加必要的仪器测试，使用项目实际 runner。不要靠 JVM 的 Android 空实现冒充设备链路。

有设备时创建名称清楚的独立测试项目，不改用户已有正文。默认使用本地模拟 SSE 服务/虚构凭据和隔离测试数据，验证三段场景、第二段中断、应用进程重建、继续、审查、提交和卡片保存。不得为了测试无授权消耗真实模型额度。

记录实际设备型号、每段正文长度和 checksum、candidateId 是否保持、owner/token 变化、最终块类型和阅读版本。系统回收与 force-stop 分别记录；force-stop 后不承诺后台自行重启。

设备未连接时只报告“设备验证未进行”，继续完成所有自动化链路；不编造启动/生成日志。恢复设备连接后再补真机，不能拿旧 8 节 READY 当本轮证据。

最终交付报告固定包含：

1. 实际修改文件和生产入口调用图。
2. A/B/C 测试逐条的对应测试名、通过/失败/未实测状态。
3. 至少一条文件仓库重建后续写成功的完整证据。
4. 初始失败到恢复过程中，各段 checksum 未被意外改变的证据。
5. 最终正文长度、分段数、调用数、审查覆盖和正式提交版本。
6. 测试/构建真实输出，设备实测与限制，工作区/Git 状态。

**停止条件是目标链路全部实现并完成可用验证，不是写完某一个类。** 若确实有不可消除的外部阻塞，逐项说明已经完成到哪里，仍继续处理不依赖外部条件的任务。不得再次把“先做 FileDraftStore 还是推送”作为本轮结尾。
