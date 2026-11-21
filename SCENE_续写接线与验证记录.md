# SCENE 续写接线与验证记录

基线：`5c8ca78`。本记录对应本次工作区修改；没有提交或推送。

## 实际接通的产品路径

```text
ClassroomScreen → ClassroomRunManager → ClassroomRunWorker
→ ClassroomService（共享 ClassroomStore + DraftStore）
→ UnitExecutor.SCENE → SceneContinuation
→ 完整正文审查 / 可定位的局部修订 → 状态提取
→ UnitCommitter（正式 revision、版本清单和草稿 COMMITTED 同事务）
→ 原有 outbox / 作品阅读
```

- Service 新任务先写 QUEUED，取得项目租约后才恢复单元和进入 RUNNING。修复了新任务被清理逻辑当成中断任务的真实宿主故障。
- SCENE 按 LengthPlan 生成多个完整 JSON 分段，累计正文长度后自然收束。第二段之后不接受标题；人物 ID、固定设定、前置状态、资料与原有权限过滤一起进入请求。
- 草稿阶段、owner、候选版本、预算、审查、错误和分段元信息完整编解码；时间戳与 token 不再转 Int。
- 分段落盘前核对候选身份、当前输入指纹和项目执行权；恢复同一候选，保留之前分段 checksum。新输入不能接收旧响应。
- 调用前持久记录预算，最多 64 次应用层调用；截断/非法 JSON 不保存为成功分段，有限纠正当前段。
- 达到篇幅下限且自然收束后审查完整 blocks/events，不截取前 4000 字。可定位到块的审查问题进行有界局部修订并重审；无法定位的全局问题保留草稿并进入待处理。
- 审查结果存回草稿，状态提取失败后再继续时不重新生成已保存正文。状态提取仍可安全重跑，正式状态只在最终事务中应用。
- 稳定候选 revision ID 与提交标记保证恢复不重复发布，正式内容与草稿 COMMITTED 同一事务保存。
- 修复原单次执行路径网络重试成功结果被首次失败结果覆盖的问题，增加回归测试。
- 项目接管会结束旧运行身份，旧执行者不能续租、追加、正式提交或经 RunFinalizer 写完成终态；SCENE 长单元有 30 秒心跳。
- AndroidHttpTransport 改为可取消的独立阻塞任务，在取消开始时关闭连接，不再等 Job 完成回调。
- 项目列表显示保存段数、正文累计与阶段；入队/运行判断考虑 WorkManager 未结束任务。

## 自动化结果

最终命令：

```powershell
.\gradlew.bat :feature-classroom:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --rerun-tasks
```

结果：BUILD SUCCESSFUL，83 个任务实际执行；课堂 358 项、App 36 项，失败及错误均为 0。

新增的 SceneExecutorIntegrationTest 从产品 UnitExecutor 入口执行，使用 FileClassroomStore：

1. 两段生成后完整审查、状态提取、正式保存，文件重开后作品版本和 COMMITTED 一致。
2. 第一段保存、第二段网络中断；重建文件仓库和 run/owner 后，只补第二段。candidateId 与第一段 checksum 不变，预算继续累计。
3. 响应返回前修改 directive，旧响应不能追加或正式提交。
4. 4000 个正文计数单位、三个生成调用、整节审查和状态提取，末尾正文进入请求。
5. 即使 length 响应有完整 JSON，也不作为成功段保存；纠正当前段后只增加一个分段。
6. 审查定位到第二段时局部修订，第一段不重写，candidateVersion 更新后重新审查。
7. 新 owner 接管但尚未写新段时，旧 owner 已不能续租和追加。

另新增原单次路径“网络重试成功不被首次失败内容覆盖”回归。

## 设备结果

设备：小米 M2007J3SC，serial `1f5244c1`，设备报告 Android 17。覆盖安装 Debug APK 和独立测试 APK。

```powershell
adb -s 1f5244c1 shell am instrument -w com.aiclassroom.app.test/com.aiclassroom.app.classroom.SceneTestInstrumentation
```

实际输出：

```text
PASS host file-draft resume / full review / version commit
PASS 2 device tests: host resume and blocked transport cancellation
```

测试一通过真实 ClassroomService 的网关注入点，使用虚构连接和设备 cacheDir 下隔离项目：保存首段、模拟网络中断、重开文件仓库、取得新 token、补后段、审查全部原文、状态提取、正式版本读取。测试结束删除自己的临时目录，不改已有用户项目和连接。

测试二使用设备 localhost ServerSocket：发送 SSE 响应头后不发正文，客户端阻塞读取；取消在 2.5 秒内返回，服务端 3 秒内确认连接 EOF，远早于 30 秒读超时，没有迟到正文回调。

## 验证边界

- 实机测试走 Service/UnitExecutor 的真实装配，但使用模拟 ModelGateway；没有消耗真实 API 额度。
- 文件重开与新执行者恢复已测；没有在本轮对运行中的 Android Worker 做实际 force-stop/Doze/长时间锁屏验收。
- 4000 字是确定性模拟正文，证明执行与存储长度，不证明文风、自然关系变化等真实模型质量。
- 本轮 SCENE 已实际接入分段；小说、教学、文字 GAL 保留原执行路径，不能据此声称它们也获得多段续写。
- 本轮审查整节正文，超出供应商上下文时会失败并保留草稿；尚未实现任意上下文长度下的分块审查与全局联合审查。支持的单节上限仍由 LengthPlan 限定为 20000。
- 全局审查无法定位局部修改范围时进入待处理，不让模型擅自重写整节。状态补丁失败不作为无变化发布。
- 保持版本 4.1.0 / 47。工作区变更待审查，没有新建提交、推送或发布。
