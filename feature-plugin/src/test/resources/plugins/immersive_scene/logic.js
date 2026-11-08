
// 沉浸风景：点击顶栏按钮进入全屏；每秒更新时钟
function pad(n) { return (n < 10 ? '0' : '') + n; }

function onEnter(ctx) {
  ctx.host.enterFullscreen();
  return 'ok';
}

function onTick(ctx) {
  var d = new Date(ctx.now());
  ctx.storage.set('clock', pad(d.getHours()) + ':' + pad(d.getMinutes()));
  ctx.storage.set('date', d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日');
}

function provideState(ctx) {
  return {
    clock: ctx.storage.get('clock') || '--:--',
    date: ctx.storage.get('date') || ''
  };
}
