// Polyfill for pdf-parse (DOMMatrix is browser API, not available in Node.js)
if (typeof globalThis.DOMMatrix === 'undefined') {
  globalThis.DOMMatrix = class DOMMatrix {
    constructor() { this.a = 1; this.b = 0; this.c = 0; this.d = 1; this.e = 0; this.f = 0; }
    translate() { return this; }
    scale() { return this; }
    rotate() { return this; }
    multiply() { return this; }
    inverse() { return this; }
    transformPoint() { return { x: 0, y: 0 }; }
  };
}

const { app, Tray, Menu, BrowserWindow, dialog, ipcMain, nativeImage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { pathToFileURL } = require('url');

let tray = null;
let mainWindow = null;
let logWindow = null;
let service = null;
let isRunning = false;
let cloudConnected = false;
let clawConnected = false;
let minimizeToTray = false;

const settingsPath = path.join(os.homedir(), '.openhsd', 'settings.json');

const iconActive = nativeImage.createFromPath(path.join(__dirname, '..', 'assets', 'tray-active.png'));
const iconInactive = nativeImage.createFromPath(path.join(__dirname, '..', 'assets', 'tray-inactive.png'));

const logs = [];
const maxLogs = 2000;

function addLog(level, message) {
  logs.push({ time: Date.now(), level, message });
  if (logs.length > maxLogs) logs.shift();
  
  if (logWindow && !logWindow.isDestroyed()) {
    logWindow.webContents.send('log', { time: Date.now(), level, message });
  }
}

function loadSettings() {
  try {
    if (fs.existsSync(settingsPath)) {
      const raw = fs.readFileSync(settingsPath, 'utf8');
      const settings = JSON.parse(raw);
      minimizeToTray = settings.minimizeToTray ?? false;
    } else {
      minimizeToTray = false;
    }
  } catch (e) {
    minimizeToTray = false;
  }
  return { minimizeToTray };
}

function saveSettings(settings) {
  try {
    const dir = path.dirname(settingsPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(settingsPath, JSON.stringify(settings, null, 2) + '\n', 'utf-8');
    minimizeToTray = settings.minimizeToTray ?? false;
    addLog('info', '设置已保存');
    return true;
  } catch (e) {
    addLog('error', '保存设置失败：' + e.message);
    return false;
  }
}

function getCurrentEnv() {
  return process.env.NODE_ENV || 'production';
}

function setCurrentEnv(env) {
  process.env.NODE_ENV = env;
}

async function loadConfig() {
  const env = getCurrentEnv();
  const envConfigPath = path.join(__dirname, '..', `cj.config.${env}.json`);
  const tokenConfigPath = path.join(__dirname, '..', 'cj.config.json');

  let envConfig = {};
  if (fs.existsSync(envConfigPath)) {
    envConfig = JSON.parse(fs.readFileSync(envConfigPath, 'utf-8'));
  }

  let tokenConfig = { cloud: { token: '' } };
  if (fs.existsSync(tokenConfigPath)) {
    tokenConfig = JSON.parse(fs.readFileSync(tokenConfigPath, 'utf-8'));
  }

  return {
    ...envConfig,
    _env: env,
    cloud: {
      ...envConfig.cloud,
      token: tokenConfig.cloud?.token || ''
    }
  };
}

async function saveToken(token) {
  const tokenConfigPath = path.join(__dirname, '..', 'cj.config.json');
  fs.writeFileSync(tokenConfigPath, JSON.stringify({ cloud: { token } }, null, 2) + '\n', 'utf-8');
}

async function createService() {
  const servicePath = path.join(__dirname, '..', 'src', 'service.mjs');
  const { OpenHSDService } = await import(pathToFileURL(servicePath).href);
  const config = await loadConfig();
  
  service = new OpenHSDService(config);
  
  service.onLog(({ time, level, message }) => {
    addLog(level, message);
  });
  
  service.onStatusChange((status) => {
    isRunning = status.running;
    cloudConnected = status.cloudConnected;
    clawConnected = status.clawConnected;
    updateTrayMenu();
    
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('status-change', status);
    }
  });
  
  return service;
}

async function startService() {
  if (isRunning) return;
  
  addLog('info', '正在启动服务...');
  
  try {
    service = await createService();
    await service.start();
  } catch (e) {
    addLog('error', '启动失败：' + e.message);
  }
}

function stopService() {
  if (!isRunning || !service) return;
  
  addLog('info', '正在停止服务...');
  service.stop();
}

function createMainWindow() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.show();
    mainWindow.focus();
    return;
  }

  mainWindow = new BrowserWindow({
    width: 800,
    height: 600,
    resizable: true,
    minimizable: true,
    frame: true,
    show: false,
    title: 'openHSD Plugin',
    icon: iconInactive,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.setMenuBarVisibility(false);

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    mainWindow.focus();
  });

  mainWindow.on('close', (e) => {
    if (minimizeToTray) {
      e.preventDefault();
      mainWindow.hide();
    } else {
      confirmExit();
    }
  });
}

function createLogWindow() {
  if (logWindow && !logWindow.isDestroyed()) {
    logWindow.show();
    logWindow.focus();
    return;
  }

  logWindow = new BrowserWindow({
    width: 700,
    height: 500,
    resizable: true,
    title: '日志 - openHSD Plugin',
    icon: iconInactive,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  logWindow.loadFile(path.join(__dirname, 'renderer', 'logs.html'));
  logWindow.setMenuBarVisibility(false);

  logWindow.on('ready-to-show', () => {
    logs.forEach(log => {
      logWindow.webContents.send('log', log);
    });
  });
}

function updateTrayMenu() {
  const statusLabel = isRunning 
    ? `● 服务运行中` 
    : '○ 服务已停止';
  
  const contextMenu = Menu.buildFromTemplate([
    { label: statusLabel, enabled: false },
    { type: 'separator' },
    {
      label: isRunning ? '⏹ 停止服务' : '▶ 启动服务',
      click: () => {
        if (isRunning) {
          stopService();
        } else {
          startService();
        }
      }
    },
    {
      label: '⚙ 配置...',
      click: () => {
        createMainWindow();
      }
    },
    {
      label: '📜 查看日志',
      click: () => {
        createLogWindow();
      }
    },
    { type: 'separator' },
    {
      label: '❌ 退出',
      click: () => {
        confirmExit();
      }
    }
  ]);

  tray.setContextMenu(contextMenu);
  tray.setImage(isRunning ? iconActive : iconInactive);
}

async function confirmExit() {
  if (isRunning) {
    const { response } = await dialog.showMessageBox({
      type: 'warning',
      title: '确认退出',
      message: '服务正在运行中，确定要退出吗？',
      buttons: ['退出', '取消'],
      defaultId: 1,
      cancelId: 1
    });
    if (response === 1) return;
    stopService();
  }
  app.exit(0);
}

app.whenReady().then(async () => {
  app.setAppUserModelId('com.openhsd.plugin');

  loadSettings();

  tray = new Tray(iconInactive);
  tray.setToolTip('openHSD Plugin');
  
  tray.on('click', () => {
    createMainWindow();
  });

  updateTrayMenu();
  
  addLog('info', 'openHSD Plugin 已启动');

  createMainWindow();
});

app.on('window-all-closed', (e) => {
  e.preventDefault();
});

ipcMain.handle('get-config', async () => {
  const config = await loadConfig();
  return {
    env: config._env || 'production',
    serverUrl: config.cloud?.wsUrl || '',
    openclawUrl: config.openclaw?.wsUrl || '',
    token: config.cloud?.token || ''
  };
});

ipcMain.handle('set-env', async (event, env) => {
  if (env !== 'development' && env !== 'production') {
    return false;
  }
  setCurrentEnv(env);
  
  if (service && isRunning) {
    addLog('warn', '环境已更改，需要重启服务以应用新配置');
  }
  
  return true;
});

ipcMain.handle('save-token', async (event, token) => {
  await saveToken(token);
  addLog('info', 'Token 已保存');
  return true;
});

ipcMain.handle('start-service', async () => {
  await startService();
  return true;
});

ipcMain.handle('stop-service', () => {
  stopService();
  return true;
});

ipcMain.handle('get-status', () => {
  return {
    running: isRunning,
    cloudConnected,
    clawConnected
  };
});

ipcMain.handle('get-logs', () => {
  return logs.slice(-500);
});

ipcMain.handle('clear-logs', () => {
  logs.length = 0;
  return true;
});

ipcMain.handle('open-log-window', () => {
  createLogWindow();
  return true;
});

ipcMain.handle('save-config', async (event, { env, wsUrl, openclawUrl }) => {
  const envConfigPath = path.join(__dirname, '..', `cj.config.${env}.json`);
  
  let envConfig = {};
  if (fs.existsSync(envConfigPath)) {
    envConfig = JSON.parse(fs.readFileSync(envConfigPath, 'utf-8'));
  }
  
  if (wsUrl) {
    envConfig.cloud = envConfig.cloud || {};
    envConfig.cloud.wsUrl = wsUrl;
  }
  
  if (openclawUrl) {
    envConfig.openclaw = envConfig.openclaw || {};
    envConfig.openclaw.wsUrl = openclawUrl;
  }
  
  fs.writeFileSync(envConfigPath, JSON.stringify(envConfig, null, 2) + '\n', 'utf-8');
  addLog('info', `配置已保存到 cj.config.${env}.json`);
  return true;
});

ipcMain.handle('get-settings', () => {
  return loadSettings();
});

ipcMain.handle('save-settings', (event, settings) => {
  return saveSettings(settings);
});
