const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  isDesktop: true,
  platform: process.platform,
  checkEnvironment: () => ipcRenderer.invoke('env-check'),
  startApplication: (options) => ipcRenderer.invoke('start-application', options || {}),
  saveMysqlConfig: (config) => ipcRenderer.invoke('save-mysql-config', config),
  loadMysqlConfig: () => ipcRenderer.invoke('load-mysql-config'),
  testMysqlConnection: (config) => ipcRenderer.invoke('test-mysql-connection', config),
  checkBackendPort: () => ipcRenderer.invoke('check-backend-port'),
  clearBackendPort: () => ipcRenderer.invoke('clear-backend-port'),
  getBackendStatus: () => ipcRenderer.invoke('backend-status'),
  getFrontendStatus: () => ipcRenderer.invoke('frontend-status'),
  ensureFrontendReady: () => ipcRenderer.invoke('ensure-frontend-ready'),
  getFirstLaunchStatus: () => ipcRenderer.invoke('first-launch-status'),
  ensureBackendReady: () => ipcRenderer.invoke('ensure-backend-ready'),
  downloadInstaller: (key) => ipcRenderer.invoke('env-download-installer', { key }),
  cancelInstallerDownload: (key) => ipcRenderer.invoke('env-cancel-download', { key }),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),
  getDesktopVersion: () => ipcRenderer.invoke('get-desktop-version'),
  checkElectronUpdate: () => ipcRenderer.invoke('check-electron-update'),
  downloadElectronUpdate: (payload) => ipcRenderer.invoke('download-electron-update', payload || {}),
  applyElectronUpdate: (payload) => ipcRenderer.invoke('apply-electron-update', payload || {}),
  getElectronUpdateCleanup: () => ipcRenderer.invoke('get-electron-update-cleanup'),
  confirmElectronUpdateCleanup: () => ipcRenderer.invoke('confirm-electron-update-cleanup'),
  cancelElectronUpdateDownload: () => ipcRenderer.invoke('cancel-electron-update-download'),
  onElectronUpdateProgress: (callback) => {
    const listener = (_event, data) => callback(data)
    ipcRenderer.on('electron-update-progress', listener)
    return () => ipcRenderer.removeListener('electron-update-progress', listener)
  },
  onDownloadProgress: (callback) => {
    const listener = (_event, data) => callback(data)
    ipcRenderer.on('env-download-progress', listener)
    return () => ipcRenderer.removeListener('env-download-progress', listener)
  },
  onStartupProgress: (callback) => {
    const listener = (_event, data) => callback(data)
    ipcRenderer.on('startup-progress', listener)
    return () => ipcRenderer.removeListener('startup-progress', listener)
  },
})
