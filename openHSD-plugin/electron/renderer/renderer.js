let status = {
  running: false,
  cloudConnected: false,
  clawConnected: false
};

let currentEnv = 'production';
let tokenVisible = false;
let settings = { 
  minimizeToTray: false, 
  autostartPlugin: false, 
  autostartOpenClaw: false,
  fetchTokenOnStartup: true
};

const cloudDot = document.getElementById('cloudDot');
const clawDot = document.getElementById('clawDot');
const cloudStatus = document.getElementById('cloudStatus');
const clawStatus = document.getElementById('clawStatus');
const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const logsBtn = document.getElementById('logsBtn');
const settingsBtn = document.getElementById('settingsBtn');
const serverUrlInput = document.getElementById('serverUrl');
const openclawUrlInput = document.getElementById('openclawUrl');
const currentTokenInput = document.getElementById('currentToken');
const toggleTokenBtn = document.getElementById('toggleToken');
const editTokenBtn = document.getElementById('editTokenBtn');
const tokenModal = document.getElementById('tokenModal');
const closeModalBtn = document.getElementById('closeModal');
const cancelModalBtn = document.getElementById('cancelModal');
const saveTokenBtn = document.getElementById('saveToken');
const newTokenInput = document.getElementById('newToken');
const devEnvBtn = document.getElementById('devEnvBtn');
const prodEnvBtn = document.getElementById('prodEnvBtn');
const envHint = document.getElementById('envHint');
const saveConfigBtn = document.getElementById('saveConfigBtn');
const settingsModal = document.getElementById('settingsModal');
const closeSettingsModalBtn = document.getElementById('closeSettingsModal');
const cancelSettingsModalBtn = document.getElementById('cancelSettingsModal');
const saveSettingsBtn = document.getElementById('saveSettingsBtn');
const minimizeToTrayCheckbox = document.getElementById('minimizeToTray');
const autostartPluginCheckbox = document.getElementById('autostartPlugin');
const autostartOpenClawCheckbox = document.getElementById('autostartOpenClaw');
const fetchTokenOnStartupCheckbox = document.getElementById('fetchTokenOnStartup');

function updateUI() {
  if (status.running) {
    startBtn.disabled = true;
    stopBtn.disabled = false;
    startBtn.innerHTML = `
      <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
        <polygon points="5,3 19,12 5,21"/>
      </svg>
      运行中...
    `;
    devEnvBtn.disabled = true;
    prodEnvBtn.disabled = true;
    saveConfigBtn.disabled = true;
  } else {
    startBtn.disabled = false;
    stopBtn.disabled = true;
    startBtn.innerHTML = `
      <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
        <polygon points="5,3 19,12 5,21"/>
      </svg>
      启动服务
    `;
    devEnvBtn.disabled = false;
    prodEnvBtn.disabled = false;
    saveConfigBtn.disabled = false;
  }

  if (status.cloudConnected) {
    cloudDot.classList.add('connected');
    cloudStatus.textContent = '已连接';
  } else {
    cloudDot.classList.remove('connected');
    cloudStatus.textContent = '未连接';
  }
  if (status.clawConnected) {
    clawDot.classList.add('connected');
    clawStatus.textContent = '已连接';
  } else {
    clawDot.classList.remove('connected');
    clawStatus.textContent = '未连接';
  }
}

function updateEnvUI(env) {
  currentEnv = env;
  if (env === 'development') {
    devEnvBtn.classList.add('active');
    prodEnvBtn.classList.remove('active');
  } else {
    devEnvBtn.classList.remove('active');
    prodEnvBtn.classList.add('active');
  }
}

async function loadConfig() {
  const config = await window.electronAPI.getConfig();
  serverUrlInput.value = config.serverUrl || '';
  openclawUrlInput.value = config.openclawUrl || '';
  currentTokenInput.value = config.token || '';
  updateEnvUI(config.env || 'production');
}
async function refreshStatus() {
  status = await window.electronAPI.getStatus();
  updateUI();
}
async function switchEnv(env) {
  if (status.running) {
    envHint.textContent = '服务运行中无法切换环境';
    return;
  }
  await window.electronAPI.setEnv(env);
  updateEnvUI(env);
  envHint.textContent = '环境已切换';
  setTimeout(() => {
    envHint.textContent = '';
  }, 2000);
  await loadConfig();
}
devEnvBtn.addEventListener('click', () => switchEnv('development'));
prodEnvBtn.addEventListener('click', () => switchEnv('production'));
startBtn.addEventListener('click', async () => {
  startBtn.disabled = true;
  startBtn.innerHTML = `
    <svg class="btn-icon spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10" stroke-opacity="0.3"/>
      <path d="M12 2a10 10 0 0 1 10 10"/>
    </svg>
    启动中...
  `;
  await window.electronAPI.startService();
});
stopBtn.addEventListener('click', async () => {
  stopBtn.disabled = true;
  await window.electronAPI.stopService();
});
logsBtn.addEventListener('click', async () => {
  await window.electronAPI.openLogWindow();
});
toggleTokenBtn.addEventListener('click', () => {
  tokenVisible = !tokenVisible;
  currentTokenInput.type = tokenVisible ? 'text' : 'password';
});
editTokenBtn.addEventListener('click', () => {
  tokenModal.classList.add('show');
  newTokenInput.value = '';
  newTokenInput.focus();
});
function closeModal() {
  tokenModal.classList.remove('show');
}
closeModalBtn.addEventListener('click', closeModal);
cancelModalBtn.addEventListener('click', closeModal);
saveTokenBtn.addEventListener('click', async () => {
  const newToken = newTokenInput.value.trim();
  if (newToken) {
    await window.electronAPI.saveToken(newToken);
    currentTokenInput.value = newToken;
  }
  closeModal();
});
saveConfigBtn.addEventListener('click', async () => {
  const serverUrl = serverUrlInput.value.trim();
  const openclawUrl = openclawUrlInput.value.trim();
  await window.electronAPI.saveConfig({
    env: currentEnv,
    wsUrl: serverUrl,
    openclawUrl: openclawUrl
  });
  envHint.textContent = '配置已保存';
  setTimeout(() => {
    envHint.textContent = '';
  }, 2000);
});
tokenModal.addEventListener('click', (e) => {
  if (e.target === tokenModal) {
    closeModal();
  }
});

async function loadSettings() {
  settings = await window.electronAPI.getSettings();
  minimizeToTrayCheckbox.checked = settings.minimizeToTray ?? false;
  autostartPluginCheckbox.checked = settings.autostartPlugin ?? false;
  autostartOpenClawCheckbox.checked = settings.autostartOpenClaw ?? false;
  fetchTokenOnStartupCheckbox.checked = settings.fetchTokenOnStartup ?? true;
}

function closeSettingsModal() {
  settingsModal.classList.remove('show');
}

settingsBtn.addEventListener('click', async () => {
  await loadSettings();
  settingsModal.classList.add('show');
});

closeSettingsModalBtn.addEventListener('click', closeSettingsModal);
cancelSettingsModalBtn.addEventListener('click', closeSettingsModal);

saveSettingsBtn.addEventListener('click', async () => {
  const newSettings = {
    minimizeToTray: minimizeToTrayCheckbox.checked,
    autostartPlugin: autostartPluginCheckbox.checked,
    autostartOpenClaw: autostartOpenClawCheckbox.checked,
    fetchTokenOnStartup: fetchTokenOnStartupCheckbox.checked
  };
  await window.electronAPI.saveSettings(newSettings);
  settings = newSettings;
  closeSettingsModal();
});

settingsModal.addEventListener('click', (e) => {
  if (e.target === settingsModal) {
    closeSettingsModal();
  }
});

window.electronAPI.onStatusChange((newStatus) => {
  status = newStatus;
  updateUI();
});
const style = document.createElement('style');
style.textContent = `
  .spin {
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
`;
document.head.appendChild(style);
loadConfig();
refreshStatus();
