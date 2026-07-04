const { app, BrowserWindow, shell, ipcMain } = require('electron')
const path = require('path')
const fs = require('fs')
const { checkWindowsEnvironment } = require('./envCheck.cjs')
const { startInstallerDownload, cancelInstallerDownload } = require('./installerDownload.cjs')
const { stopBackendProcess, getBackendStatus, startBackendProcess, loadMysqlConfig } = require('./backendProcess.cjs')
const { launchApplication, saveMysqlConfig } = require('./startupLauncher.cjs')
const { testMysqlConnection } = require('./mysqlTest.cjs')
const { getPortOccupancy, clearPort, BACKEND_PORT } = require('./portUtils.cjs')
const { getFirstLaunchStatus, markFirstLaunchComplete } = require('./firstLaunch.cjs')
const {
  startFrontendServer,
  stopFrontendServer,
  getFrontendStatus,
  getFrontendUrl,
} = require('./frontendServer.cjs')
const { checkElectronUpdate, getDesktopVersionInfo } = require('./updateCheck.cjs')
const {
  startElectronUpdateDownload,
  cancelElectronUpdateDownload,
  applyInstallerAndQuit,
  getInstallState,
  getPendingCleanup,
  cleanupDownloadedInstaller,
} = require('./updateDownload.cjs')

const isDev = !app.isPackaged
const DEV_SERVER_URL = process.env.VITE_DEV_SERVER_URL || 'http://127.0.0.1:5173'
const APP_ICON = path.join(__dirname, '../build/icon.ico')

/** @type {import('electron').BrowserWindow | null} */
let mainWindow = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 960,
    minHeight: 640,
    show: isDev,
    autoHideMenuBar: true,
    title: 'XXG-KAMI-PRO 2.0',
    icon: fs.existsSync(APP_ICON) ? APP_ICON : undefined,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  if (!isDev) {
    mainWindow.once('ready-to-show', () => {
      mainWindow?.show()
    })
  }

  mainWindow.webContents.on('did-fail-load', (_event, code, description, url) => {
    console.error('[electron] did-fail-load:', code, description, url)
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  if (isDev) {
    mainWindow.loadURL(DEV_SERVER_URL)
  } else {
    const frontendUrl = getFrontendStatus().url || getFrontendUrl()
    mainWindow.loadURL(frontendUrl)
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(async () => {
  ipcMain.handle('env-check', () => checkWindowsEnvironment({ bundled: getBackendStatus() }))
  ipcMain.handle('start-application', async (event, options) => {
    try {
      const result = await launchApplication(event.sender, options || {})
      if (result?.success && !getFirstLaunchStatus().complete) {
        const marked = markFirstLaunchComplete({
          from: 'startup-wizard',
        })
        if (marked.success) {
          result.setupMarkerPath = marked.markerDisplayPath
        }
      }
      return result
    } catch (error) {
      return {
        success: false,
        message: error.message || '启动失败',
        logs: [],
        steps: [],
      }
    }
  })
  ipcMain.handle('first-launch-status', () => getFirstLaunchStatus())
  ipcMain.handle('ensure-backend-ready', async () => {
    try {
      return await startBackendProcess({
        mysqlConfig: loadMysqlConfig(),
        allowReuse: true,
      })
    } catch (error) {
      return {
        started: false,
        message: error.message || '后端启动失败',
      }
    }
  })
  ipcMain.handle('save-mysql-config', (_event, config) => {
    saveMysqlConfig(config || {})
    return { success: true }
  })
  ipcMain.handle('load-mysql-config', () => loadMysqlConfig())
  ipcMain.handle('test-mysql-connection', (_event, config) => testMysqlConnection(config || {}))
  ipcMain.handle('check-backend-port', () => getPortOccupancy(BACKEND_PORT))
  ipcMain.handle('clear-backend-port', async () => {
    stopBackendProcess()
    return clearPort(BACKEND_PORT)
  })
  ipcMain.handle('backend-status', () => getBackendStatus())
  ipcMain.handle('frontend-status', () => getFrontendStatus())
  ipcMain.handle('ensure-frontend-ready', async () => {
    try {
      return await startFrontendServer()
    } catch (error) {
      return {
        running: false,
        ready: false,
        message: error.message || '前端 HTTP 服务启动失败',
      }
    }
  })
  ipcMain.handle('env-download-installer', (event, { key }) =>
    startInstallerDownload(event.sender, key)
  )
  ipcMain.handle('env-cancel-download', (event, { key }) => {
    cancelInstallerDownload(event.sender, key)
    return { canceled: true }
  })
  ipcMain.handle('open-external', async (_event, url) => {
    const target = String(url || '').trim()
    if (!/^https?:\/\//i.test(target)) {
      throw new Error('仅允许打开 http/https 链接')
    }
    await shell.openExternal(target)
    return { success: true }
  })
  ipcMain.handle('get-desktop-version', () => ({
    ...getDesktopVersionInfo(),
    ...getInstallState(),
  }))
  ipcMain.handle('check-electron-update', async () => {
    try {
      return await checkElectronUpdate()
    } catch (error) {
      return {
        success: false,
        message: error.message || '检查桌面版更新失败',
      }
    }
  })
  ipcMain.handle('download-electron-update', (event, payload) =>
    startElectronUpdateDownload(event.sender, payload || {})
  )
  ipcMain.handle('apply-electron-update', (event, payload) => {
    try {
      return applyInstallerAndQuit(event.sender, payload || {})
    } catch (error) {
      return { success: false, message: error.message || '启动安装失败' }
    }
  })
  ipcMain.handle('get-electron-update-cleanup', () => getPendingCleanup())
  ipcMain.handle('confirm-electron-update-cleanup', () => cleanupDownloadedInstaller())
  ipcMain.handle('cancel-electron-update-download', (event) =>
    cancelElectronUpdateDownload(event.sender)
  )

  if (!isDev) {
    try {
      await startFrontendServer()
    } catch (error) {
      console.error('[electron] frontend server failed:', error.message || error)
    }
  }

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('before-quit', () => {
  stopBackendProcess()
  stopFrontendServer()
})
