// 宠物猫：ui.json 只负责长相，行为全在这里。
var lines = [
  "喵～ 点我一下",
  "这题不难，我盯着你写",
  "好感度 +1",
  "喵呜～ 继续加油",
  "打瞌睡了…zzZ"
];

var state = { showBubble: false, line: lines[0], hint: "点一下继续", affinity: 0 };

// 好感度存在插件私有存储里：重装 app 也在（storage 权限）。
var saved = host.storageGet("affinity");
state.affinity = saved ? parseInt(saved, 10) || 0 : 0;
state.hint = "好感度 " + state.affinity + " · 点一下继续";

function onPetTap(ctx) {
  state.showBubble = !state.showBubble;
  var index = (state.affinity + 1) % lines.length;
  state.line = lines[index];
  state.affinity = state.affinity + 1;
  state.hint = "好感度 " + state.affinity + " · 点一下继续";
  ctx.storage.set("affinity", String(state.affinity));
  if (state.showBubble) {
    // TTS 失败（没配密钥 / 没网）不能影响界面：失败只记一行日志。
    var spoken = host.speak(state.line);
    if (!spoken || !spoken.ok) {
      ctx.log("TTS 不可用，跳过：" + ((spoken && spoken.message) || "unknown"));
    }
  }
  host.requestUiRefresh();
}
