// 沉浸模式：风景 / 息屏像素钟两种形态；钟形态点一下屏幕切回风景。
function pad(n) { return (n < 10 ? '0' : '') + n; }

function onEnter(ctx) { ctx.host.enterFullscreen(); return 'ok'; }

function onSwitch(ctx) {
  var m = ctx.storage.get('mode') || 'scene';
  var next = (m === 'scene') ? 'clock' : 'scene';
  ctx.storage.set('mode', next);
  return 'ok';
}

function onTick(ctx) {
  var d = new Date(ctx.now());
  var h = d.getHours(), m = d.getMinutes();
  ctx.storage.set('clock', pad(h) + ':' + pad(m));
  // 像素钟只认 0-9 与冒号，所以时间固定两位，冒号也由字体里的字模画。
  ctx.storage.set('time', pad(h) + ':' + pad(m));
  ctx.storage.set('date', d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日');
  // 5x7 点阵只有 0-9 与冒号，所以日期再给一份纯数字版本（YYYY.MM.DD）。
  ctx.storage.set('dateDigits', d.getFullYear() + '.' + pad(d.getMonth() + 1) + '.' + pad(d.getDate()));
}

function provideState(ctx) {
  var mode = ctx.storage.get('mode') || 'scene';
  // 旧版本存过 'flip'，它和 'clock' 是同一种形态：只要不是 'scene' 就当时钟。
  var clockMode = (mode !== 'scene');
  return {
    mode: mode,
    scene: clockMode ? 'false' : 'true',
    flip: clockMode ? 'true' : 'false',
    clock: ctx.storage.get('clock') || '--:--',
    time: ctx.storage.get('time') || '--:--',
    date: ctx.storage.get('date') || '',
    dateDigits: ctx.storage.get('dateDigits') || ''
  };
}
