# AI-Classroom UI/UX 重构 · 阶段性交付总结（阶段 0–4）

- 交付日期：2026-11-06
- 基线：`6c4c28e Release AI Classroom 3.3.0` → 当前 HEAD `a24cffe`
- 本次授权范围：阶段 0 审计 → 阶段 1 设计系统 → 阶段 2 主页面与导航 → 阶段 3 对话与课堂页（**阶段 4 设置页、阶段 5 全局回归尚未授权执行**）
- 设计方向：Nothing 风格，黑白灰 + 唯一强调色 Nothing 红 `#FF3B30`（用户已确认，视觉稿见 `design-preview/index.html`）

---

## 1. 交付物

| 类别 | 交付物 |
|---|---|
| 审计报告 | `UI_AUDIT.md`（316 行，结论均带文件+行号） |
| 视觉稿 | `design-preview/index.html`（71KB 单文件，深浅双主题三屏，零外链离线可开） |
| 设计系统 | `app/.../ui/theme/DesignTokens.kt`（221 行） |
| 基础组件 | `app/.../ui/components/AppButtons.kt`、`AppDialog.kt` |
| 页面改造 | `MainActivity.kt`（3749 → 3925 行） |
| Git | 8 个 commit（每阶段一个，可按阶段回退） |

**累积 diff**：4 files changed, 765 insertions(+), 255 deletions(-)；**0 文件删除**。

---

## 2. commit 清单

| hash | 内容 |
|---|---|
| `347656b` | 阶段1 设计系统：DesignTokens 收敛硬编码、7 处 Dialog 统一基座、点名组件 Token 化 |
| `8482fc3` | 阶段2 主页面与导航：顶栏/底部5Tab/课堂菜单/空状态首页视觉统一，修复回底按钮与格言死参数 |
| `5e7c431` | 阶段3-1 消息容器统一（MessageCard 与 SimpleMessageCard 视觉对齐，操作集差异保留） |
| `7f5a0f4` | 阶段3-2A 滚动策略：新消息仅在用户位于底部时自动回底 |
| `563a74e` | 阶段3-3A 修正 isAtBottom 判定（原判定恒 false 的既有缺陷） |
| `3fcb19f` | 阶段3-3B-1 MarkdownText 视觉 Token 化（代码块/标题/列表） |
| `8d129fd` | 阶段3-3B-2 主课堂 vs 分支课堂视觉区分 |
| `a24cffe` | 阶段3-3B-3 状态反馈：流式/发送中/失败/空状态展示层 |

---

## 3. 视觉系统

**DesignTokens 引用数：266 处**（MainActivity 内）

- **颜色**：深色 page `#181B20` / surface `#22262D` / card `#2B3038` / ink `#E8EBEF` / ink2 `#AAB2BD` / line `#48515D`；浅色 `#F5F6F8` / `#FFFFFF` / `#F0F2F5` / `#20242A` / `#697382` / `#D7DCE3`；强调色 `#FF3B30` 深浅恒定
- **排版**：Type{10/11/12/13/17/18/20/22sp} + 4 档行高 + Weight{4 档} + TextStyles{Kicker/SectionTitle/Body/BodyStrong/Caption/Technical}
- **间距**：4dp 基准网格（2/4/6/8/12/16/20/24/32 + Page16/CardH14/CardV13）
- **圆角**：12 档（0–999）；**边框**：Hairline1 / Focus1.5 / Selected2
- **尺寸**：Icon16/20/24、IconButton36、Button40/44、Field44、Row56、Avatar32、Tile38、Fab46
- **动效**：120/180/260ms

**硬编码收敛终态（全部实测为 0）**：
- `fontSize = N.sp` → **0**（原 46 处）
- `RoundedCornerShape(数字)` → **0**（原 20 处）
- `Color(0x…)` → **0**（原 36 处）

**强调色克制**：全文件 `Accent` 仅 **18 处**（3 处为白色前景 AccentOn），实际染红 14 处，全部落在主行动/选中态/关键状态/视觉锚点（菜单图标、导航选中圆点、新建主键、搜索聚焦、选中课堂行、FAB、生成中 spinner、分支 Badge）。

---

## 4. 功能保护说明（业务逻辑零改动的证据）

对 `6c4c28e..HEAD` 累积 diff 的增删行 grep 下列符号 → **全部 0 命中**：

`sendMessage` / `callChat` / `callChatStream` / `callChatStreamWithFallback` / `callVision` / `fetchModels` / `speakText` / `promptMessagesForRoom` / `promptMessagesForBranch` / `buildJson` / `buildVisionJson` / `parseStreamDelta` / `isApiFailure` / `ClassroomStore` / `toJson` / `toClassroom` / `recallMemoryCards` / `recallKnowledgeSnippets` / `splitKnowledgeChunks` / `detectExamSession` / `parseExamBlock` / `packExamAnswers` / `scheduleMemoryBuild` / `sanitizeMathText` / `buildInlineMarkdown` / `isMathLikeLine`

其他保护证据：
- **JSON 结构**：diff 中 `optString/put(/optJSONArray` 等序列化行 → **0 命中**，持久化 key 与字段未变
- **构建配置**：`build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` / wrapper → **diff 为空**（依赖、SDK、Kotlin 版本均未动）
- **资源与 Manifest**：`app/src/main/res` + `AndroidManifest.xml` → **diff 为空**
- **架构**：无 ViewModel / StateFlow / Navigation 库引入（grep 均为 0），沿用原有 remember 状态模式
- **功能入口**：17 项入口文案计数改前改后一致（`批量上传资料` 1→2 为**空状态文案引用**，唯一可点按钮 L2047 未变，属提示词要求的"空状态告知下一步"）
- **5 Tab 结构**：枚举逐字节一致（课堂/分支/记忆/知识库/设置）
- **消息操作集**：主课堂 6 项（长按菜单+双击朗读+2×开分支+菜单调用）、分支页 0 项，改前改后计数一致

---

## 5. 本次授权的行为变更（仅 3 处）

| # | 变更 | 依据 |
|---|---|---|
| 1 | 新消息仅在用户位于底部时自动回底，浏览历史不再被强制拉回 | 提示词阶段3第4条明文要求 |
| 2 | 修正 `isAtBottom` 判定（原 `bottomIndex=size+1` 永不命中 → 恒 false） | 既有缺陷，用户明确授权修复 |
| 3 | 展示层失败横幅 / 生成中 spinner / 发送中禁用视觉 | 提示词阶段3状态反馈要求 |

修复后行为变化：**输入栏在底部展开 142dp（原恒 76dp，展开态从未生效）**；**FAB 回到顶部功能恢复可用（原永远只显示"回底"）**。

---

## 6. 验证结果（全部为实际执行）

| 验证项 | 结果 |
|---|---|
| 阶段 0 基线编译 | `gradlew assembleDebug` → BUILD SUCCESSFUL in 4m41s，exit 0 |
| 每阶段独立复跑编译 | 8 次全部 **exit 0**（由调度方独立执行，非采信子代理自报） |
| 终验 clean 全量编译 | `gradlew clean assembleDebug` → **BUILD SUCCESSFUL in 16s，exit 0**，APK 16,542,253 bytes 产出 |
| 工作区 | 仅 7 个预期未跟踪项（提示词/UI_AUDIT.md/design-preview/5 个 apk），无 reset、无清理用户文件 |

---

## 7. 已知限制与遗留事项

**未授权、按规范保持原样（留给阶段 4/5 或单独授权）**：
1. 分支页死图片键 `onImage = {}`（L1690 附近）
2. 分支列表两个按钮执行同一动作
3. `BackHandler` 缺失（返回键为系统默认行为）
4. `styles.xml` 系统栏硬编码浅色 + `windowLightStatusBar`（**深色主题下状态栏仍为浅色**，改 res 需授权）
5. 阶段 4（知识/记忆/角色/设置页）与阶段 5（全局一致性回归）**尚未执行**
6. 首页格言死参数已在阶段 2 修复并真正展示

**需人工回归的高风险点**：
1. **横屏/小屏**：失败横幅为顶部浮层，长失败文本（HTTP 错误体）会变高并覆盖上方消息（为"原样展示不截断"所付出的代价）
2. **误报观察**：`looksLikeFailure` 匹配 `http 4`/`http 5` 等，正文含此类字样的**正常回复**有低概率误报红横幅
3. **深浅双主题真机观感**：顶栏 Hairline 分隔线、导航 ink 指示器、课堂行 Accent 淡染、横幅对比度
4. **自定义皮肤用户**：强调色固定 `#FF3B30`，不跟随用户自定义按钮色（按提示词 3.2"品牌一致性"取向处理；如需皮肤接管需另行决策）
5. **键盘与 Insets**：输入区弹出、IME 适配、发送键 loading 态无障碍播报
6. **滚动回归**：正序/反序（设置"对话从下到上"）两种布局 × 在底部/在中部 三场景
7. **Markdown 渲染**：长代码块、标题三级、引用行（`>` 无专属分支，与正文共享样式）

**未验证声明**：本次**未在真机/模拟器上运行**，全部验证为编译级 + 静态核对；纯视觉观感（字距、留白节奏）需人工打开 `design-preview/index.html` 或安装 APK 判断。

---

## 8. 运行方式

```bash
cd "D:\AI Classroom"
./gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

视觉稿直接双击 `design-preview/index.html` 离线打开，右上角可切深浅主题，顶部可切三屏。

---

## 9. 阶段 4：悬浮毛玻璃底栏与模糊浮层（已完成，全部真机实测）

> 范围 `a24cffe..HEAD` 共 **20 个提交**；业务逻辑冻结区（sendMessage / parseStreamDelta / ClassroomStore 等）未触碰。

### 9.1 commit 清单（正序）

| hash | 内容 |
|---|---|
| `94b7253` | 引入 `dev.chrisbanes.haze:haze:1.5.4` |
| `c8ff420` | 打通 haze 模糊浮层机制，课堂菜单为首个消费者 |
| `909b438` | 修复菜单打开即崩溃（hazeEffect 补 backgroundColor） |
| `a3ffe52` | 修复菜单面板内左滑关闭回归 |
| `2977674` | 底栏改为悬浮毛玻璃面板（左右 12dp / 距底 2dp / 圆角 16dp / blur 18dp） |
| `b2c226d` | 课堂菜单整块半透明玻璃化 |
| `124280b` | `AppDialogBase` 改用 `HazeDialog`（7 处对话框统一基座） |
| `452265e` | 内容透到悬浮底栏之下，固定底元素经 `LocalBottomBarLift` 抬升 |
| `7f75948` | 消息长按菜单改树内毛玻璃浮层（点空白关闭） |
| `64fb40d` | 聊天「模型」「推理力度」下拉 Popup → 树内毛玻璃浮层 |
| `74d45b2` | 底栏补玻璃遮盖层，观感与二级菜单统一 |
| `6a88f7b` `3827e75` `f4f46ff` `615feeb` | 合并 stage4-f2 / e / d1 / d2 |
| `7d00795` | 底栏横向拖动跟手选中 + 松手切页 + 图标缩放 |
| `89d6481` | 四屏列表 contentPadding 补 `LocalBottomBarLift` 抬升 |
| `e2e8905` | 底栏改用内容层独立 haze 采样源，修栏下内容不模糊 |
| `22ad4e4` | 全站思源宋体衬线 + 底栏下划线选中态 |
| `60dbe77` | 底栏加宽收高 + 阴影包边；二级菜单铺满上下 + 点空白收回 |

### 9.2 设计系统增量（DesignTokens 遵循「只增不改」）

- 新增 `Shadow.Bar = 8dp`（底栏浮层专用）
- 新增 `Serif` + `serifTypography(Typography())`，由最外层 `AIClassroomTheme` 注入，内层 `MaterialTheme` 不覆盖 → 全站生效
- 新增字体资源 `noto_serif_sc_400/700.ttf`（各 14,784,324 B，已入 git）

### 9.3 真机实测数据

设备：小米 M2007J3SC（`1f5244c1`），1080×2400，density 440 → **2.75 px/dp**，手势导航。
所有数字来自 `uiautomator dump` + `screencap` 的像素测量脚本，非目测、非推断。

**底栏（用户 5 点要求）**

| # | 要求 | 改动 | 实测证据 |
|---|---|---|---|
| 1 | 比卡片窄、没全包住 | 左右内缩 12dp → **4dp** | 面板边实测 4.7dp，与图标行宽推算一致 |
| 2 | 图标距应用底部有空白，缩短 | `NavigationBar` 传 `windowInsets = WindowInsets(0.dp)` | 面板高 **119dp → 80dp**；标签中心 y **2278 → 2317**（净缩短 14dp） |
| 3 | 两侧按钮贴边、长按放大后溢出 | item 行两侧内缩 8dp | 图标左缘到面板边 **61px → 81px**，1.15× 胶囊退出圆角区 |
| 4 | 需要阴影包边塑造层次 | 新增 `Shadow.Bar` + `.shadow()` | 空白区亮度 234.6 → 228.5（12px 渐变），确为阴影非内容 |
| 5 | 二级菜单没包住最上最下、缺点空白收回 | Row 去除系统栏避让（下移至 LazyColumn）+ 新增 `onDismiss` | 顶部 y2 内侧亮 +3.4、底部 y2397 亮 +19.1；点空白后 `我的课堂` 计数 **1 → 0** |

选中态下划线：**y2347–2352、宽 66px**，距屏底 17.5dp，与系统手势条（y≈2364 起）留 12px 空隙。

> 自纠记录：首版收紧过头，下划线落到 y2363 与手势条同带（选中中间「记忆」会压上去）→ 底部留白 2dp → 8dp，已复验。

### 9.4 弹窗 / 浮层覆盖（如实记录，不夸大）

- **真机点开核验过**：课堂菜单（含搜索 / 新建 / 简介 / 改名 / 删除链路）、消息长按浮层、模型与推理力度浮层、底栏 5 Tab、二级菜单点空白收回。
- **仅「共享同一基座」的静态推断，未逐个点开**：`AppDialogBase` 下 7 处对话框中的其余几处（删除确认、清空、导出类等）。
- 口径：基座改造对这 7 处**结构上等价**，但**不等同于逐个实测**；闭环需在阶段 5 全局回归中逐个点开。

### 9.5 版本与产物

- `versionCode = 38`、`versionName = 3.3.5`（按用户指令由 3.4.0 下调；code 不下调以免破坏设备 `install -r`）
- `app/build/outputs/apk/release/app-release.apk` 已 `install -r` 至真机，`firstInstallTime` 不变、崩溃 0

### 9.6 阶段 4 已知局限

1. **仅竖屏、浅色主题实测**：横屏与深色主题未纳入本轮像素测量（期间曾抓到横屏帧，已锁 `user_rotation 0` 复验）。
2. **截图易失真**：`screencap` 常抓到其他 App 前台帧（本机曾抓到 `com.jarvis.app`），每次取帧前必须用 `dumpsys window | grep mCurrentFocus` 校验。
3. **主题色保持 `#EF4444`**：用户明确「先不变了」，未改回默认 `#667085`，调色盘未动。
4. **底栏加宽取 4dp 属委托假设**：用户只说「加宽宽度」未给数值，未做通栏；贴边 0dp 或对齐其他基准需再确认。
5. **仍未做**：阶段 5 全局回归、`styles.xml` 系统栏浅色硬编码、`BackHandler` 缺失。

