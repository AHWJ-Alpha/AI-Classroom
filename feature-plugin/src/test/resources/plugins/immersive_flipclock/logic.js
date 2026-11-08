
// 翻页时钟：每秒更新；深夜自动压暗
function pad(n) { return (n < 10 ? '0' : '') + n; }

function onTick(ctx) {
  var d = new Date(ctx.now());
  var h = d.getHours(), m = d.getMinutes(), s = d.getSeconds();
  ctx.storage.set('hh', pad(h));
  ctx.storage.set('mm', pad(m));
  ctx.storage.set('ss', pad(s));
  ctx.storage.set('date', d.getFullYear() + '年' + (d.getMonth()+1) + '月' + d.getDate() + '日');

  // 自动压暗：22:00-06:00 视为夜间
  var night = (h >= 22 || h < 6);
  ctx.storage.set('night', night ? '1' : '0');
  // 亮度：白天 1.0，深夜 0.55
  ctx.storage.set('dim', night ? '0.55' : '1.0');
}

function provideState(ctx) {
  return {
    hh: ctx.storage.get('hh') || '--',
    mm: ctx.storage.get('mm') || '--',
    ss: ctx.storage.get('ss') || '--',
    date: ctx.storage.get('date') || '',
    dim: ctx.storage.get('dim') || '1.0',
    showSec: 'true'
  };
}
