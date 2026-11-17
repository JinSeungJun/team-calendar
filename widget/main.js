// 팀 캘린더 PC 위젯 — 바탕화면 구석에 반투명으로 띄우는 창
// 웹 캘린더(GitHub Pages)를 위젯 모드로 불러오므로, 웹을 고치면 위젯에도 자동 반영됨
const { app, BrowserWindow, Tray, Menu, nativeImage, screen, shell } = require('electron');
const path = require('path');
const fs = require('fs');

const PAGE_URL = 'https://jinseungjun.github.io/team-calendar/?widget=1';
const SIZES = { '작게': [360, 400], '보통': [440, 500], '크게': [560, 620] };
const ALPHAS = [1, 0.9, 0.75, 0.6, 0.45];

let win = null, tray = null, cfg = {}, retryTimer = null;

const cfgPath = () => path.join(app.getPath('userData'), 'widget.json');
function loadCfg() { try { return JSON.parse(fs.readFileSync(cfgPath(), 'utf8')); } catch (e) { return {}; } }
function saveCfg() { try { fs.writeFileSync(cfgPath(), JSON.stringify(cfg)); } catch (e) {} }

// 두 번 실행하면 기존 창을 보여줌
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => showWin());
  app.whenReady().then(() => {
    cfg = Object.assign({ alpha: 0.75, onTop: false, size: '보통', autoStart: false }, loadCfg());
    if (cfg.autoStart) setAutoStart(true); // exe 위치가 바뀌었을 수 있어서 다시 등록
    createWindow();
    createTray();
  });
  app.on('window-all-closed', () => app.quit());
}

function defaultPos(w) {
  const wa = screen.getPrimaryDisplay().workArea;
  return [wa.x + wa.width - w - 16, wa.y + 16];
}

function createWindow() {
  const [w, h] = SIZES[cfg.size] || SIZES['보통'];
  let [x, y] = (typeof cfg.x === 'number' && typeof cfg.y === 'number') ? [cfg.x, cfg.y] : defaultPos(w);
  // 저장된 위치가 화면 밖이면 (모니터 분리 등) 기본 위치로
  const onScreen = screen.getAllDisplays().some((d) => {
    const a = d.workArea;
    return x + 60 > a.x && y + 30 > a.y && x < a.x + a.width - 60 && y < a.y + a.height - 30;
  });
  if (!onScreen) [x, y] = defaultPos(w);

  win = new BrowserWindow({
    x, y, width: w, height: h,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    resizable: false,
    maximizable: false,
    minimizable: true,
    skipTaskbar: true,
    hasShadow: false,
    alwaysOnTop: !!cfg.onTop,
    show: false,
    title: '팀 캘린더',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { contextIsolation: true, sandbox: true, backgroundThrottling: false }
  });

  win.loadURL(PAGE_URL);
  win.once('ready-to-show', () => win.show());
  win.webContents.on('did-finish-load', applyAlpha);
  win.webContents.on('did-fail-load', (e, code, desc, url, isMainFrame) => {
    if (!isMainFrame || code === -3) return; // -3: 정상적인 이동 취소
    win.loadFile(path.join(__dirname, 'offline.html'));
    clearTimeout(retryTimer);
    retryTimer = setTimeout(() => win && win.loadURL(PAGE_URL), 30000);
  });
  win.on('moved', () => { const [nx, ny] = win.getPosition(); cfg.x = nx; cfg.y = ny; saveCfg(); });
  // 링크는 기본 브라우저로
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
}

function applyAlpha() {
  if (!win) return;
  win.webContents.executeJavaScript(
    "document.documentElement.style.setProperty('--walpha','" + Number(cfg.alpha) + "')"
  ).catch(() => {});
}

function showWin() { if (!win) return; if (win.isMinimized()) win.restore(); win.show(); win.focus(); }
function toggleWin() { if (!win) return; (win.isVisible() && !win.isMinimized()) ? win.hide() : showWin(); }

function setSize(name) {
  const [w, h] = SIZES[name];
  win.setResizable(true);
  win.setSize(w, h);
  win.setResizable(false);
}

function setAutoStart(on) {
  // 포터블 exe는 실행할 때마다 임시 폴더에 풀리므로, 원래 exe 경로를 등록해야 함
  const exe = process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;
  app.setLoginItemSettings({ openAtLogin: on, path: exe });
}

function createTray() {
  const img = nativeImage.createFromPath(path.join(__dirname, 'icon.png')).resize({ width: 16, height: 16 });
  tray = new Tray(img);
  tray.setToolTip('팀 캘린더');
  tray.on('click', toggleWin);
  buildMenu();
}

function buildMenu() {
  const menu = Menu.buildFromTemplate([
    { label: '보이기 / 숨기기', click: toggleWin },
    { label: '새로고침', click: () => win && win.loadURL(PAGE_URL) },
    { type: 'separator' },
    {
      label: '항상 위에 표시', type: 'checkbox', checked: !!cfg.onTop,
      click: (m) => { cfg.onTop = m.checked; win.setAlwaysOnTop(m.checked); saveCfg(); }
    },
    {
      label: '배경 투명도',
      submenu: ALPHAS.map((a) => ({
        label: a === 1 ? '불투명' : Math.round((1 - a) * 100) + '% 투명',
        type: 'radio', checked: Math.abs(cfg.alpha - a) < 0.01,
        click: () => { cfg.alpha = a; saveCfg(); applyAlpha(); }
      }))
    },
    {
      label: '크기',
      submenu: Object.keys(SIZES).map((k) => ({
        label: k, type: 'radio', checked: cfg.size === k,
        click: () => { cfg.size = k; saveCfg(); setSize(k); }
      }))
    },
    {
      label: '위치 초기화 (오른쪽 위)',
      click: () => { const [w] = win.getSize(); const [x, y] = defaultPos(w); win.setPosition(x, y); cfg.x = x; cfg.y = y; saveCfg(); }
    },
    {
      label: 'Windows 시작 시 자동 실행', type: 'checkbox', checked: !!cfg.autoStart,
      click: (m) => { cfg.autoStart = m.checked; saveCfg(); setAutoStart(m.checked); }
    },
    { type: 'separator' },
    { label: '웹 캘린더 열기 (브라우저)', click: () => shell.openExternal(PAGE_URL.replace('?widget=1', '')) },
    { label: '종료', click: () => app.quit() }
  ]);
  tray.setContextMenu(menu);
}
