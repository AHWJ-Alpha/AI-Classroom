// 音乐插件：三态悬浮窗 —— 最小圆（40dp）/ 小悬浮窗（形态 A）/ 胶囊（形态 B）。
// 单击逐级展开、再收；任意形态长按直接收成最小圆。ui.json 只负责长相；
// 「现在在放什么」全部由 provideState 现问宿主，所以切歌 / 换主题都不用插件自己算。
var state = {
  // ---- 形态与开关 ----
  shown: false,      // 顶栏「音乐」按下的洗牌：关掉时整个悬浮层不画
  compact: false,    // true = 仅封面/音符的最小圆
  expanded: false,   // false = 小悬浮窗，true = 胶囊（compact 为 true 时不生效）
  // ---- 播放信息 ----
  playing: false,
  title: '未在播放',
  artist: '',
  artworkPath: '',
  progress: 0,
  positionText: '0:00',
  durationText: '0:00',
  timeText: '0:00 / 0:00'
};

// 封面按「哪一首歌」缓存：provider 每秒都会被问一次，同一首歌重复落盘既慢又把磁盘写满。
// 用户在设置里调的两个尺寸（manifest 里是 number + slider）。
// snapshot() 拿不到 ctx，所以在 provideState(ctx) 里读好存这里。
var sizes = { circleSize: 56, panelWidth: 300 };

var artKey = '';
var artPath = '';
var artTries = 0;

// 点过播放/暂停之后，系统媒体会话可能还没把新状态回传，先信自己两帧，避免图标闪一下又跳回去。
var optimisticTicks = 0;

// 一键播放失败时的提示：悬浮窗照常打开，但先把「缺什么」说清楚，几帧之后回到常规文案。
var hintTicks = 0;
var hintArtist = '';

// 开关存插件私有存储：重装 app 也在，重启后悬浮窗保持上次的样子。
state.shown = host.storageGet('shown') === 'true';
state.expanded = host.storageGet('expanded') === 'true';

function pad(n) { return (n < 10 ? '0' : '') + n; }

/** 毫秒 -> m:ss；非法 / 负数一律 0:00（进度条不会因为脏数据跳到奇怪的位置）。 */
function formatTime(ms) {
  var total = Math.floor((Number(ms) || 0) / 1000);
  if (total < 0) total = 0;
  return Math.floor(total / 60) + ':' + pad(total % 60);
}

/** 读一次宿主「正在播放」；失败时把 code / message 原样带出来，兜底文案由 applyError 决定。 */
function readTrack() {
  var result = host.mediaCurrent();
  if (!result || !result.ok) {
    return { ok: false, code: (result && result.code) || 'media_unavailable', message: (result && result.message) || '' };
  }
  var track = {};
  try { track = JSON.parse(result.value) || {}; } catch (e) { track = {}; }
  return { ok: true, track: track };
}

/** 没有通知使用权 / 工具开关关掉时的界面：说清楚去哪儿开，而不是显示一个空曲目。 */
function applyError(code, message) {
  state.playing = false;
  state.progress = 0;
  state.positionText = '0:00';
  state.durationText = '0:00';
  state.timeText = '0:00 / 0:00';
  state.artworkPath = '';
  if (code === 'media_permission_required' || code === 'media_disabled') {
    state.title = '需要通知使用权';
    state.artist = '设置 -> 工具 里打开「音乐」，并在系统里授权通知使用权';
  } else {
    state.title = '音乐暂不可用';
    state.artist = (message || code || '请稍后再试');
  }
}

/**
 * 当前这首的封面路径。
 *
 * host.mediaArtwork() 会把封面落盘并返回绝对路径；同一首歌只落一次，
 * 失败最多试 2 次（歌手头像加载慢时还有机会补上），之后一直是占位图，不再每秒重试。
 */
function coverPathOf(track) {
  var key = (track.packageName || '') + '|' + (track.title || '') + '|' + (track.album || '');
  if (key !== artKey) {
    artKey = key;
    artPath = '';
    artTries = 0;
  }
  if (artPath !== '') return artPath;
  // 以前这里是 `artTries >= 2` 就永久放弃 —— 播放器常常晚一两秒才给封面，
  // 前两次没拿到，这首歌就再也没有封面了（用户反馈「一丢就回不来」）。
  // 改成每 3 次 tick 重试一次，直到拿到为止；换歌时计数清零。
  if (artTries % 3 !== 0) {
    artTries = artTries + 1;
    return artPath;
  }
  artTries = artTries + 1;
  var art = host.mediaArtwork();
  artPath = (art && art.ok) ? String(art.path) : '';
  return artPath;
}

/** 宿主每次刷新界面都会调它：读一次媒体状态，把 ui.json 要的扁平字段一次给全。 */
function provideState(ctx) {
  // 每次刷新都重读设置：宿主保存设置后会重建 runtime 重新装载，所以这里拿到的一定是最新值。
  // 于是「拖滑条 → 浮窗立刻变大小」不需要任何额外的实时通道。
  var cfg = ctx.settings || {};
  var cs = Number(cfg.circleSize);
  sizes.circleSize = (cs > 0) ? cs : 56;
  var pw = Number(cfg.panelWidth);
  sizes.panelWidth = (pw > 0) ? pw : 300;

  var now = readTrack();
  if (!now.ok) {
    applyError(now.code, now.message);
    return snapshot();
  }
  var track = now.track;
  var reported = track.playing === true;
  if (optimisticTicks > 0) {
    optimisticTicks = optimisticTicks - 1;
  } else {
    state.playing = reported;
  }
  state.title = track.title ? String(track.title) : '未在播放';
  state.artist = track.artist ? String(track.artist) : (track.title ? '' : '打开酷狗放一首歌试试');
  if (hintTicks > 0) {
    // 只在真的没有曲目时顶掉文案：有歌在放时歌手名比提示更有用。
    hintTicks = hintTicks - 1;
    if (!track.title && hintArtist !== '') state.artist = hintArtist;
  }
  var duration = Number(track.durationMs) || 0;
  var position = Number(track.positionMs) || 0;
  var ratio = duration > 0 ? position / duration : 0;
  state.progress = ratio > 1 ? 1 : (ratio > 0 ? ratio : 0);
  state.positionText = formatTime(position);
  state.durationText = formatTime(duration);
  state.timeText = state.positionText + ' / ' + state.durationText;
  state.artworkPath = track.title ? coverPathOf(track) : '';
  return snapshot();
}

/**
 * ui_ticker 每秒跑一次。
 *
 * 它自己不改界面：PluginUiDriver 每次触发之后都会让宿主重读 this.state（provider），
 * 所以进度条是「每秒被问一次」，而不是在脚本里堆 timer。
 */
function onTick(ctx) {
  // ui_ticker 只负责「按时叫醒插件」，**不会自动刷新界面**。
  // 不在这里显式请求重组的话：进度条永远停在打开那一刻的值（实测酷狗 position 会变，
  // 但界面读不到新值，看上去就是「进度不动」）。
  host.requestUiRefresh();
  return 'ok';
}

/** 展开成胶囊：点形态 A 时调用。 */
/** 双击小圆：展开成大浮窗（完整播放控制都在那里）。 */
function onExpand(ctx) {
  state.expanded = true;
  ctx.storage.set('expanded', 'true');
  host.requestUiRefresh();
}

/** 收回成小悬浮窗：点形态 B 的封面或空白处时调用。 */
function onCollapse(ctx) {
  // 点胶囊 → 收回小圆。
  state.expanded = false;
  ctx.storage.set('expanded', 'false');
  host.requestUiRefresh();
}

/**
 * 顶栏「音乐」：一键播放 + 显示悬浮窗；已经在显示时收起。
 *
 * 播放的事全交给宿主的 playOrShow：有会话在播只显示浮窗，暂停就继续，
 * 没会话就拉起上次用过的播放器（宿主负责等会话出现再发 play）。
 */
function onToggleShow(ctx) {
  if (state.shown) {
    state.shown = false;
    state.expanded = false;
    ctx.storage.set('shown', 'false');
    ctx.storage.set('expanded', 'false');
    host.requestUiRefresh();
    return;
  }
  // 打开时默认就是小圆（只有一个收起态了）。
  state.shown = true;
  state.expanded = false;
  ctx.storage.set('shown', 'true');
  ctx.storage.set('expanded', 'false');
  ctx.storage.set('compact', 'false');
  var result = host.mediaControl('playOrShow');
  var info = {};
  if (result && result.ok) {
    try { info = JSON.parse(result.value) || {}; } catch (e) { info = {}; }
    if (info.state === 'playing' || info.state === 'resumed' || info.state === 'launched') {
      state.playing = true;
      optimisticTicks = 2;
    }
  }
  var hint = '';
  if (!result || !result.ok) {
    hint = (result && result.message) ? String(result.message) : '播放器没有响应';
  } else if (info.state === 'no_player' || info.state === 'launched_pending' || info.state === 'failed') {
    hint = info.message ? String(info.message) : '先手动打开一个音乐 App 播放一次';
  }
  if (hint !== '') {
    hintArtist = hint;
    hintTicks = 4;
    ctx.log('一键播放未完成：' + hint);
  }
  host.requestUiRefresh();
}

/**
 * 播放 / 暂停。
 *
 * 关键点：没有会话时（播放器被系统杀掉、或这台机器从没播放过）直接发 play
 * 会得到 media_no_session，界面上就是「点了没反应」。这时要退回到 playOrShow：
 * 它用 intent 把上次用过的播放器拉起来，等会话出现再发 play，用户才真的听到声音。
 */
function onToggle(ctx) {
  var action = state.playing ? 'pause' : 'play';
  var result = host.mediaControl(action);
  var ok = !!(result && result.ok);
  var code = (result && result.code) || '';
  // 只有「想播但播不了」才退回拉起播放器；暂停失败不该顺手把歌打开。
  if (!ok && action === 'play' && (code === 'media_no_session' || code === 'media_control_failed')) {
    var fallback = host.mediaControl('playOrShow');
    if (fallback && fallback.ok) {
      var info = {};
      try { info = JSON.parse(fallback.value) || {}; } catch (e) { info = {}; }
      if (info.state === 'playing' || info.state === 'resumed' || info.state === 'launched') {
        result = fallback;
        ok = true;
      } else if (info.message) {
        hintArtist = String(info.message);
        hintTicks = 4;
      }
    }
  }
  if (ok) {
    state.playing = (action === 'play');
    optimisticTicks = 2;
  } else {
    // 失败要看得见：把原因写到歌手位几秒，否则用户只觉得按钮点下去没动。
    hintArtist = (result && result.message) ? String(result.message) : '播放器没有响应';
    hintTicks = 4;
    ctx.log('播放控制失败：' + (code || 'unknown') + ' ' + ((result && result.message) || ''));
  }
  host.requestUiRefresh();
}

function onPrev(ctx) {
  var result = host.mediaControl('prev');
  if (!result || !result.ok) {
    ctx.log('上一首失败：' + ((result && result.code) || 'unknown'));
  }
  // 换歌了：封面要跟着换，下一帧 provider 会重新落盘。
  artKey = '';
  artPath = '';
  artTries = 0;
  host.requestUiRefresh();
}

function onNext(ctx) {
  var result = host.mediaControl('next');
  if (!result || !result.ok) {
    ctx.log('下一首失败：' + ((result && result.code) || 'unknown'));
  }
  artKey = '';
  artPath = '';
  artTries = 0;
  host.requestUiRefresh();
}

/** provider 的返回：只给标量，ui.json 用 state.xxx 单层取值。 */
function snapshot() {
  // 两态：展开 → 胶囊；否则 → 小圆。
  // 原来的第三个「方卡」形态已按用户要求删掉 —— 它要靠长按才能变回小圆，
  // 而长按小圆会崩（两个手势识别器同时抢事件），三态切换本身也是多余复杂度。
  // 两态互斥必须在 snapshot 里算成两个布尔：
  // ui.json 的 visibleWhen 只做单层取值，**返回 undefined 时会被当成「可见」** ——
  // 之前漏了 expanded，结果大浮窗和小圆同时画出来，大浮窗盖住了小圆（用户看到的就是「没有图标」）。
  return {
    shown: state.shown,
    circleSize: sizes.circleSize,
    panelWidth: sizes.panelWidth,
    circle: !state.expanded,
    expanded: state.expanded,
    playing: state.playing,
    paused: !state.playing,
    title: state.title,
    artist: state.artist,
    artworkPath: state.artworkPath,
    hasArt: state.artworkPath !== '',
    noArt: state.artworkPath === '',
    progress: state.progress,
    positionText: state.positionText,
    durationText: state.durationText,
    timeText: state.timeText
  };
}