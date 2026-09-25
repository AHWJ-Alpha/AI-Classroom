# AI Classroom 3.2.0

## 角色卡导入与小说写作

- 角色卡支持 `.md`、`.json` 和带文本元数据的 `.png` 格式。
- 支持一次选择多个文件，批量导入资料或角色卡。
- JSON 兼容常见角色卡字段，包括 `name`、`description`、`personality`、`scenario`、`first_mes`、`mes_example`、`system_prompt` 和嵌套 `data`。
- PNG 会读取常见的 `chara`、`character`、`description`、`personality`、`scenario` 和 `first_mes` 文本元数据。
- 角色卡自动作为作者级人物参照，保持身份、性格、口吻、动机、关系和行为边界连续。
- 角色卡不会让人物自动获得其他角色的秘密，最近连续对话中的事实优先于旧设定。

## 知识库体验

- 普通资料继续按当前问题、最近对话和课堂主题进行轻量检索。
- 主课堂和分支课堂均支持本轮资料召回。
- 导入失败、格式错误、空文件和 PNG 无元数据时提供明确反馈。

## 触觉反馈

- 批量选择、导入成功和删除文件时提供轻量系统震动反馈。
- 清单声明 `VIBRATE` 权限，兼容支持触觉反馈的 Android 设备。

## 兼容性

- 保留世界书、长期记忆、人格视角、私密记忆边界、分支课堂、多模型 fallback、Vision、TTS 和考试工具。
- 旧课堂 JSON 和旧知识库文件继续兼容。

## 版本信息

- 版本号：`3.2.0`
- versionCode：`36`
- APK 使用 AI Classroom Developer 开发者证书签名。
