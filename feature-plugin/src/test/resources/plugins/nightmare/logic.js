// 夜梦画室：演示「插件自己声明配置，宿主自动生成表单」。
// 配置全部通过只读的 ctx.settings 读取；未配置且无默认值的键读到 undefined。
// 注意：token 只用于发请求，绝不能拼进 prompt 或日志 —— 那等于把密钥交给了模型。
function onBeforePrompt(ctx) {
  var s = ctx.settings || {};
  if (!s.token) {
    // 必填项没填：插件安静地什么都不做，设置页那边会把这一项标红。
    return null;
  }
  var lines = [];
  lines.push('画质：' + (s.quality || 'medium'));
  lines.push('步数：' + (s.steps || 20));
  lines.push('音频：' + (s.audio_enabled ? '开' : '关'));
  lines.push('服务：' + (s.base_url || '未配置'));
  return ctx.prompt + '\n\n[夜梦画室] 当前创作参数 → ' + lines.join(' / ') + '（令牌已配置）';
}
