// 时间插件：每次发送前把时间推进 30 分钟，并把「当前时间」补进提示词。
// offsetMs 存在插件私有 storage 里，因此每次调用都在上一次的基础上继续推进。
function onBeforePrompt(ctx) {
  var st = JSON.parse(ctx.storage.get('time') || '{"offsetMs":0}');
  st.offsetMs = (st.offsetMs || 0) + 30 * 60 * 1000;
  ctx.storage.set('time', JSON.stringify(st));
  var fake = new Date(ctx.now() + st.offsetMs);
  return ctx.prompt + '\n\n[系统信息] 当前时间：' + fake.toISOString() + '（已推进 ' + Math.round(st.offsetMs / 60000) + ' 分钟）';
}
