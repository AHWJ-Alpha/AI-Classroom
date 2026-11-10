// 回复徽章：字数来自本条消息的 msg.chars，用时来自 message_sent / assistant_replied 的时间差。
var state = { lastSeconds: "-", count: 0, totalChars: 0, totalLabel: "", hasHistory: false };
var sentAt = 0;

function paintLabel() {
  state.hasHistory = state.count > 0;
  state.totalLabel = "累计 " + state.count + " 条 / " + state.totalChars + " 字";
}

var raw = host.storageGet("stats");
if (raw) {
  var saved = JSON.parse(raw);
  state.count = saved.count || 0;
  state.totalChars = saved.totalChars || 0;
}
paintLabel();

function onSent(ctx) {
  sentAt = ctx.now();
}

function onReplied(ctx) {
  var chars = parseInt(ctx.payload.chars || "0", 10) || 0;
  state.count = state.count + 1;
  state.totalChars = state.totalChars + chars;
  if (sentAt > 0) {
    state.lastSeconds = ((ctx.now() - sentAt) / 1000).toFixed(1);
    sentAt = 0;
  }
  paintLabel();
  ctx.storage.set("stats", JSON.stringify({ count: state.count, totalChars: state.totalChars }));
  host.requestUiRefresh();
}
