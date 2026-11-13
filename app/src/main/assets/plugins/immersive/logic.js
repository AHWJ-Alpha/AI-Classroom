// 沉浸模式：风景 / 息屏像素钟 / 音乐三种形态，一个键循环切换。
// 有音乐在播时，第一次进入直接落在音乐形态（用户要的是「点沉浸就进音乐沉浸界面」）。
function pad(n) { return (n < 10 ? '0' : '') + n; }

// 进沉浸：先进入全屏，再决定落在哪个形态。
//
// **顺序很重要**：enterFullscreen 必须是第一件事。
// 之前把它放在 refreshMedia 后面，而 refreshMedia 会调宿主能力（mediaCurrent），
// 一旦那次调用抛异常，整个 onEnter 就中断 —— 表现就是「点了沉浸按钮但什么都没发生」。
// 全屏是用户点这个按钮的唯一目的，不能让它被任何后续逻辑拖累。
function onEnter(ctx) {
  ctx.host.enterFullscreen();
  try {
    refreshMedia(ctx);
    if (media.playing) {
      ctx.storage.set('mode', 'music');
    } else if (!ctx.storage.get('mode')) {
      ctx.storage.set('mode', 'scene');
    }
  } catch (e) {
    // 读不到媒体就按风景形态进，不影响进全屏这件事。
    ctx.storage.set('mode', ctx.storage.get('mode') || 'scene');
  }
  return 'ok';
}

// ---- 媒体读取 ----------------------------------------------------------
// 注意：媒体能力挂在**裸 host** 上（和 music 插件一致），不是 ctx.host。
// ctx.host 只有 enterFullscreen / exitFullscreen 那几个；写错就静默拿到 undefined。
var media = {
  playing: false,
  title: '未在播放',
  artist: '打开一个音乐 App 播一首',
  artworkPath: '',
  hasArt: false,
  positionText: '0:00',
  durationText: '0:00',
  progress: 0,
  dbg: ''
};

function fmt(ms) {
  var total = Math.floor((Number(ms) || 0) / 1000);
  if (total < 0) total = 0;
  return Math.floor(total / 60) + ':' + pad(total % 60);
}

function refreshMedia(ctx) {
  var r;
  try {
    r = host.mediaCurrent();
  } catch (e) {
    r = null;
  }
  media.dbg = 'raw:' + String(JSON.stringify(r)).slice(0, 90);
  if (!r || !r.ok) {
    media.playing = false;
    media.title = (r && (r.code === 'media_permission_required' || r.code === 'media_disabled'))
      ? '需要通知使用权' : '未在播放';
    media.artist = (r && r.message) ? r.message : '';
    media.artworkPath = '';
    media.hasArt = false;
    media.progress = 0;
    media.dbg = 'err:' + ((r && r.code) || 'none');
    return;
  }
  var t = {};
  try { t = JSON.parse(r.value) || {}; } catch (e) { t = {}; }
  media.playing = t.playing === true;
  media.title = t.title ? String(t.title) : '未在播放';
  media.artist = t.artist ? String(t.artist) : '';
  var pos = t.positionMs || 0;
  var dur = t.durationMs || 0;
  media.positionText = fmt(pos);
  media.durationText = fmt(dur);
  media.progress = dur > 0 ? Math.min(1, pos / dur) : 0;
  // 封面：同一首歌只落一次盘（provider 每秒都会被问，重复写盘既慢又费空间）。
  var key = (t.packageName || '') + '|' + (t.title || '') + '|' + (t.album || '');
  if (key !== artKey) {
    artKey = key;
    artPath = '';
    artTries = 0;
  }
  if (artPath === '' && artTries < 2) {
    artTries = artTries + 1;
    var a = host.mediaArtwork();
    artPath = (a && a.ok) ? String(a.path) : '';
  }
  media.artworkPath = artPath;
  media.hasArt = !!artPath;
  media.dbg = 'ok:' + media.title + '|' + media.playing;
}

var artKey = '';
var artPath = '';
var artTries = 0;

// ---- 形态切换 ----------------------------------------------------------
// scene → clock → music → scene 循环。
// 没存过模式时按「有音乐就进音乐形态」决定，这是用户明确要的默认行为。
function onSwitch(ctx) {
  var m = ctx.storage.get('mode');
  if (!m) {
    refreshMedia(ctx);
    m = media.playing ? 'music' : 'scene';
  }
  var next = (m === 'scene') ? 'clock' : (m === 'clock' ? 'music' : 'scene');
  ctx.storage.set('mode', next);
  return 'ok';
}

// ---- 媒体控制 ----------------------------------------------------------
function onPlayPause(ctx) {
  refreshMedia(ctx);
  host.mediaControl(media.playing ? 'pause' : 'play');
  ctx.host.requestUiRefresh();
  return 'ok';
}
function onPrev(ctx) { host.mediaControl('prev'); ctx.host.requestUiRefresh(); return 'ok'; }
function onNext(ctx) { host.mediaControl('next'); ctx.host.requestUiRefresh(); return 'ok'; }

// 从音乐形态回到风景：清掉 mode，下次自动判断。
function onExitMusic(ctx) {
  ctx.storage.set('mode', 'scene');
  return 'ok';
}

// ---- 时钟 --------------------------------------------------------------
function onTick(ctx) {
  var d = new Date(ctx.now());
  var h = d.getHours(), m = d.getMinutes();
  ctx.storage.set('clock', pad(h) + ':' + pad(m));
  ctx.storage.set('time', pad(h) + ':' + pad(m));
  ctx.storage.set('date', d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日');
  ctx.storage.set('dateDigits', d.getFullYear() + '.' + pad(d.getMonth() + 1) + '.' + pad(d.getDate()));
  // 音乐形态下进度要每秒走，所以 tick 里也刷一次媒体。
  refreshMedia(ctx);
  ctx.storage.set('dbg', media.dbg);
  // 同上：ui_ticker 不自动刷新界面，必须显式请求，否则音乐形态的进度条不会动。
  host.requestUiRefresh();
}

function provideState(ctx) {
  var wrapper;
  try {
    refreshMedia(ctx);
  } catch (e) {
    // 出现异常时把错误写进标题，直接显示在界面上（比翻 logcat 快）。
    wrapper = 'ERR:' + e;
  }
  var mode = ctx.storage.get('mode');
  if (!mode) mode = media.playing ? 'music' : 'scene';
  // 旧版本存过 'flip'，它和 'clock' 是同一种形态。
  if (mode === 'flip') mode = 'clock';
  // 手动切到 music 时就留在 music（即使没在播），否则用户想看看音乐界面都进不去。

  // 背景图：设置里选了照片就用它，没选就退回包里自带的那张。
  // 以前 root.json 写死了 assets/bg.png，manifest 里的 bgImage 设置项根本没人读，
  // 所以用户选完照片界面上什么都不会变。
  var userBg = (ctx.settings && ctx.settings.bgImage) ? String(ctx.settings.bgImage) : '';
  return {
    bgPath: userBg,
    useUserBg: userBg !== '' ? 'true' : 'false',
    useDefaultBg: userBg === '' ? 'true' : 'false',
    mode: mode,
    scene: mode !== 'clock' && mode !== 'music' ? 'true' : 'false',
    flip: mode === 'clock' ? 'true' : 'false',
    music: mode === 'music' ? 'true' : 'false',
    clock: ctx.storage.get('clock') || '--:--',
    time: ctx.storage.get('time') || '--:--',
    date: ctx.storage.get('date') || '',
    dateDigits: ctx.storage.get('dateDigits') || '',
    playing: media.playing ? 'true' : 'false',
    paused: media.playing ? 'false' : 'true',
    title: wrapper ? wrapper : media.title,
    artist: media.artist,
    artworkPath: media.artworkPath,
    hasArt: media.hasArt ? 'true' : 'false',
    noArt: media.hasArt ? 'false' : 'true',
    positionText: media.positionText,
    durationText: media.durationText,
    // 时间拼成一整串：沉浸插件不允许出现任何固定文案（有单测守这条），
    // 所以分隔符「/」不能写死在 ui.json 里，只能在逻辑层拼。
    timeText: media.positionText + ' / ' + media.durationText,
    progress: media.progress,
    dbg: ''
  };
}