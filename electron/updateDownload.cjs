const fs = require('fs')
const path = require('path')
const { spawn } = require('child_process')
const { app, net } = require('electron')
const { DESKTOP_VERSION } = require('./appVersion.cjs')
const { stopBackendProcess } = require('./backendProcess.cjs')
const { stopFrontendServer } = require('./frontendServer.cjs')
const { markPendingCleanup, getPendingCleanup, cleanupDownloadedInstaller } = require('./updateState.cjs')

/** @type {import('electron').ClientRequest | null} */
let activeRequest = null

function sendProgress(webContents, payload) {
  if (webContents && !webContents.isDestroyed()) {
    webContents.send('electron-update-progress', payload)
  }
}

function downloadToFile(webContents, { url, savePath, redirectCount = 0 }) {
  if (redirectCount > 8) {
    return Promise.reject(new Error('下载重定向次数过多'))
  }

  return new Promise((resolve, reject) => {
    const request = net.request({ method: 'GET', url })
    activeRequest = request

    request.on('response', (response) => {
      const status = response.statusCode || 0

      if (status >= 300 && status < 400) {
        activeRequest = null
        const location = response.headers.location || response.headers.Location
        const nextUrl = Array.isArray(location) ? location[0] : location
        if (!nextUrl) {
          reject(new Error(`HTTP ${status}，缺少重定向地址`))
          return
        }
        downloadToFile(webContents, { url: nextUrl, savePath, redirectCount: redirectCount + 1 })
          .then(resolve)
          .catch(reject)
        return
      }

      if (status >= 400) {
        activeRequest = null
        reject(new Error(`下载失败：HTTP ${status}`))
        return
      }

      const totalBytes = parseInt(response.headers['content-length'] || '0', 10)
      let downloadedBytes = 0
      const writer = fs.createWriteStream(savePath)

      const report = (statusValue, extra = {}) => {
        sendProgress(webContents, {
          status: statusValue,
          percent: totalBytes > 0
            ? Math.min(100, Math.round((downloadedBytes / totalBytes) * 100))
            : null,
          received: downloadedBytes,
          total: totalBytes,
          savePath: statusValue === 'done' || statusValue === 'applying' ? savePath : undefined,
          ...extra,
        })
      }

      response.on('data', (chunk) => {
        downloadedBytes += chunk.length
        writer.write(chunk)
        report('downloading')
      })

      response.on('end', () => {
        writer.end(() => {
          activeRequest = null
          report('done')
          resolve(savePath)
        })
      })

      response.on('error', (err) => {
        activeRequest = null
        writer.destroy()
        fs.unlink(savePath, () => {})
        reject(err)
      })
    })

    request.on('error', (err) => {
      activeRequest = null
      reject(err)
    })

    request.end()
  })
}

function buildInstallerArgs(installMode) {
  const mode = String(installMode || 'update').toLowerCase()
  if (mode === 'fresh') {
    return []
  }
  return ['--updated']
}

function getInstallState() {
  const installDir = app.isPackaged ? path.dirname(process.execPath) : ''
  return {
    isPackaged: app.isPackaged,
    installDir,
    recommendedInstallMode: app.isPackaged ? 'update' : 'fresh',
  }
}

function applyInstallerAndQuit(webContents, payload = {}) {
  const savePath = String(payload.savePath || '').trim()
  const installMode = payload.installMode === 'fresh' ? 'fresh' : 'update'
  const targetVersion = String(payload.targetVersion || '').trim()

  if (!savePath || !fs.existsSync(savePath)) {
    throw new Error('安装包不存在')
  }

  markPendingCleanup({
    installerPath: savePath,
    targetVersion,
    previousVersion: DESKTOP_VERSION,
    installMode,
  })

  const args = buildInstallerArgs(installMode)
  sendProgress(webContents, {
    status: 'applying',
    percent: 100,
    message: installMode === 'fresh'
      ? '即将退出并启动全新安装向导…'
      : '即将退出并启动覆盖更新…',
    savePath,
    installMode,
  })

  const child = spawn(savePath, args, {
    detached: true,
    stdio: 'ignore',
    windowsHide: false,
  })
  child.unref()

  setTimeout(() => {
    sendProgress(webContents, {
      status: 'quitting',
      percent: 100,
      message: '正在退出当前程序…',
      savePath,
      installMode,
    })
    try {
      stopBackendProcess()
    } catch {
      // ignore
    }
    try {
      stopFrontendServer()
    } catch {
      // ignore
    }
    app.exit(0)
  }, 600)

  return { success: true, quitting: true }
}

async function startElectronUpdateDownload(webContents, payload = {}) {
  const targetUrl = String(payload.url || '').trim()
  const installMode = payload.installMode === 'fresh' ? 'fresh' : 'update'

  if (!/^https?:\/\//i.test(targetUrl)) {
    throw new Error('无效的下载地址')
  }
  if (activeRequest) {
    return { success: false, message: '已有更新包正在下载中' }
  }

  const defaultName = payload.fileName
    || path.basename(new URL(targetUrl).pathname)
    || 'XXG-KAMI-PRO-Setup.exe'
  const savePath = path.join(app.getPath('temp'), defaultName)

  if (fs.existsSync(savePath)) {
    fs.unlinkSync(savePath)
  }

  sendProgress(webContents, {
    status: 'starting',
    percent: 0,
    received: 0,
    total: 0,
    installMode,
  })

  try {
    const downloadedPath = await downloadToFile(webContents, {
      url: targetUrl,
      savePath,
    })
    return {
      success: true,
      savePath: downloadedPath,
      installMode,
      targetVersion: payload.targetVersion || '',
    }
  } catch (error) {
    sendProgress(webContents, {
      status: 'error',
      message: error.message || '下载失败',
    })
    return { success: false, message: error.message || '下载失败' }
  }
}

function cancelElectronUpdateDownload(webContents) {
  if (activeRequest) {
    activeRequest.abort()
    activeRequest = null
    sendProgress(webContents, { status: 'idle' })
    return { canceled: true }
  }
  return { canceled: false }
}

module.exports = {
  startElectronUpdateDownload,
  cancelElectronUpdateDownload,
  applyInstallerAndQuit,
  getInstallState,
  getPendingCleanup,
  cleanupDownloadedInstaller,
  buildInstallerArgs,
}
