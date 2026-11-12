# AI-Classroom 4.0.3（正式版）

修掉一个**只在部分机型出现**的严重问题：震动完全没反应。版本号 `44 / 4.0.3`。

> 取代 4.0.2。4.0.2 在小米等机型上震动是失效的。

## 修复：小米上完全没有震动

**现象**：vivo 正常，小米一点反应都没有。

**根因**（dumpsys 抓到的证据）：
```
effect | ignored_unsupported | duration: 3ms | usage: TOUCH | com.aiclassroom.app
```
小米的振动 HAL 把应用**直接调用** `vibrator.vibrate()` 的请求判定为不支持并丢弃 ——
`VibrationEffect.Composition` 基元和 `createPredefined` 预置效果都一样被丢。

**改法**：改走系统触感通道 `View.performHapticFeedback()`，由 ViewRootImpl 转给厂商自己的
触感实现，各 ROM 都认；只有它失败时才退回直接震动。

**验证**（小米 M2007J3SC）：
```
改前: effect | ignored_unsupported | 3ms
改后: effect | finished            | 77ms   ✓
```

## 默认插件（同 4.0.2）

只保留四个功能件：**沉浸模式 / 音乐 / 夜梦画室（生图配置）/ 时间流动**。
回复徽章、宠物猫已下线，老设备升级时自动清理残留。

## 其他（同 4.0.2）

- 触感反馈滑条调档：关闭 / 轻 / 中 / 强 / 极大
- 输入框平时单行，点进去两行，点别处自动缩回
- 模型与推理合并成一个键，和发送键同一条中心线
- 音乐首次点开无权限时弹窗引导
- 右上角悬浮键改为圆形

## 安装

- Android 8.0（API 26）及以上
- 签名与 4.0.0 / 4.0.2 / 3.3.5 一致，可直接覆盖安装，数据保留
