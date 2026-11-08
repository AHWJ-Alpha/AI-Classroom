
// 翻页时钟：点击顶栏进入全屏；每秒更新；深夜自动压暗
function pad(n) { return (n < 10 ? '0' : '') + n; }

function onEnter(ctx) {
  ctx.host.enterFullscreen();
  return 'ok';
}

function onTick(ctx) {
  var d = new Date(ctx.now());
  var h = d.getHours(), m = d.getMinutes(), s = d.getSeconds();
  ctx.storage.set('hh', pad(h));
  ctx.storage.set('mm', pad(m));
  ctx.storage.set('ss', pad(s));
  ctx.storage.set('date', d.getFullYear() + '年' + (d.getMonth()+1) + '月' + d.getDate() + '日');
  var night = (h >= 22 || h < 6);
  ctx.storage.set('dim', night ? '0.55' : '1.0');
  ctx.storage.set('period', night ? '夜间' : '日间');
}

function provideState(ctx) {
  return {
    hh: ctx.storage.get('hh') || '--',
    mm: ctx.storage.get('mm') || '--',
    ss: ctx.storage.get('ss') || '--',
    date: ctx.storage.get('date') || '',
    dim: ctx.storage.get('dim') || '1.0',
    period: ctx.storage.get('period') || ''
  };
}
