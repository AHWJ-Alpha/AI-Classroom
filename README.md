# AI Classroom 1.4

AI Classroom 是一个面向长期学习场景的 Android Demo，主线为 AI 课堂、分支课堂、长期记忆、知识库和可自定义讲师配置。

## 1.4 更新

- 设置界面按 API 模块、模型模块、讲师人格与模式模块分块保存。
- API Key、Base URL、自定义模型、讲师人格、高效模式等配置按课堂独立保存。
- 所有课堂、对话、分支、记忆、知识库摘要和配置保存在本机私有 JSON 文件，并保留备份兜底。
- 手机重启或应用更新后，已保存内容不会丢失。
- 主界面左滑或右滑打开课堂二级菜单。
- 课堂菜单支持切换课堂、新建课堂、复制其他课堂配置新建课堂。
- 支持将任意其他课堂配置快捷复制到当前课堂并立即保存。
- 保留 Markdown 与公式文本显示、`.md` / `.txt` 知识库读取、OpenAI 兼容 API 调用。

## 编译

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

历史 APK 归档在：

```text
releases/
```
