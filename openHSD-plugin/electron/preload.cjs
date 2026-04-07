const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  getConfig: () => ipcRenderer.invoke('get-config'),
  saveConfig: (config) => ipcRenderer.invoke('save-config', config),
  saveToken: (token) => ipcRenderer.invoke('save-token', token),
  setEnv: (env) => ipcRenderer.invoke('set-env', env),
  startService: () => ipcRenderer.invoke('start-service'),
  stopService: () => ipcRenderer.invoke('stop-service'),
  getStatus: () => ipcRenderer.invoke('get-status'),
  getLogs: () => ipcRenderer.invoke('get-logs'),
  clearLogs: () => ipcRenderer.invoke('clear-logs'),
  openLogWindow: () => ipcRenderer.invoke('open-log-window'),
  getSettings: () => ipcRenderer.invoke('get-settings'),
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
  
  onStatusChange: (callback) => {
    ipcRenderer.on('status-change', (event, status) => callback(status));
  },
  
  onLog: (callback) => {
    ipcRenderer.on('log', (event, log) => callback(log));
  },
  
  removeListeners: () => {
    ipcRenderer.removeAllListeners('status-change');
    ipcRenderer.removeAllListeners('log');
  }
});
