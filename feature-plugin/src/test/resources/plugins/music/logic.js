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
state.compact = host.storageGet('compact') === 'true';
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
  if (artPath !== '' || artTries >= 2) return artPath;
  artTries = artTries + 1;
  var art = host.mediaArtwork();
  artPath = (art && art.ok) ? String(art.path) : '';
  return artPath;
}

/** 宿主每次刷新界面都会调它：读一次媒体状态，把 ui.json 要的扁平字段一次给全。 */
function provideState(ctx) {
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
  return 'ok';
}

/** 展开成胶囊：点形态 A 时调用。 */
function onExpand(ctx) {
  state.compact = false;
  state.expanded = true;
  ctx.storage.set('compact', 'false');
  ctx.storage.set('expanded', 'true');
  host.requestUiRefresh();
}

/** 收回成小悬浮窗：点形态 B 的封面或空白处时调用。 */
function onCollapse(ctx) {
  state.compact = false;
  state.expanded = false;
  ctx.storage.set('compact', 'false');
  ctx.storage.set('expanded', 'false');
  host.requestUiRefresh();
}

/**
 * 收成最小圆：任意形态上长按。
 *
 * 用长按而不是单击：单击已经被「逐级展开 / 收回」占满了，再加一层单击没人猜得到。
 */
function onCompact(ctx) {
  state.compact = true;
  state.expanded = false;
  ctx.storage.set('compact', 'true');
  ctx.storage.set('expanded', 'false');
  host.requestUiRefresh();
}

/** 点最小圆：先回到形态 A，而不是一步跳到胶囊（手滑一下就全展开太跳）。 */
function onCircleTap(ctx) {
  state.compact = false;
  state.expanded = false;
  ctx.storage.set('compact', 'false');
  ctx.storage.set('expanded', 'false');
  host.requestUiRefresh();
}

/**
 * 顶栏「音乐」：一键播放 + 显示悬浮窗；已经在显示时收起。
 *
 * 播放的事全交给宿主的 playOrShow：
 * 有会话在播只显示浮窗，暂停就继续，没会话就拉起上次用过的播放器（宿主负责等会话出现再发 play）。
 */
function onToggleShow(ctx) {
  if (state.shown) {
    state.shown = false;
    state.compact = false;
    state.expanded = false;
    ctx.storage.set('shown', 'false');
    ctx.storage.set('compact', 'false');
    ctx.storage.set('expanded', 'false');
    host.requestUiRefresh();
    return;
  }
  // 重新打开时回到形态 A：收起过的小圆不该在下次打开时当成默认。
  state.shown = true;
  state.compact = false;
  ctx.storage.set('shown', 'true');
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

/** 播放 / 暂停：按钮显不显眼由 ui.json 决定，这里只发指令 + 立刻刷新。 */
function onToggle(ctx) {
  var action = state.playing ? 'pause' : 'play';
  var result = host.mediaControl(action);
  if (result && result.ok) {
    state.playing = (action === 'play');
    optimisticTicks = 2;
  } else {
    ctx.log('播放控制失败：' + ((result && result.code) || 'unknown') + ' ' + ((result && result.message) || ''));
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
  // 三态互斥在这里算一次：ui.json 的 visibleWhen 只做单层取值，看不懂 && / !。
  var circle = state.compact;
  var expanded = !circle && state.expanded;
  var collapsed = !circle && !state.expanded;
  return {
    shown: state.shown,
    circle: circle,
    collapsed: collapsed,
    expanded: expanded,
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
