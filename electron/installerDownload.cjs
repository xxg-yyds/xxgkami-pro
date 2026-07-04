const fs = require('fs')
const { net, dialog, BrowserWindow } = require('electron')
const { INSTALLERS } = require('./installers.cjs')

/** @type {Map<string, import('electron').ClientRequest>} */
const activeRequests = new Map()

function sendProgress(webContents, payload) {
  if (!webContents.isDestroyed()) {
    webContents.send('env-download-progress', payload)
  }
}

function downloadToFile(webContents, { key, url, savePath, redirectCount = 0 }) {
  if (redirectCount > 8) {
    return Promise.reject(new Error('下载重定向次数过多'))
  }

  return new Promise((resolve, reject) => {
    const request = net.request({ method: 'GET', url })
    activeRequests.set(key, request)

    request.on('response', (response) => {
      const status = response.statusCode || 0

      if (status >= 300 && status < 400) {
        activeRequests.delete(key)
        const location = response.headers.location || response.headers.Location
        const nextUrl = Array.isArray(location) ? location[0] : location
        if (!nextUrl) {
          reject(new Error(`HTTP ${status}，缺少重定向地址`))
          return
        }
        downloadToFile(webContents, { key, url: nextUrl, savePath, redirectCount: redirectCount + 1 })
          .then(resolve)
          .catch(reject)
        return
      }

      if (status >= 400) {
        activeRequests.delete(key)
        reject(new Error(`下载失败：HTTP ${status}`))
        return
      }

      const totalBytes = parseInt(response.headers['content-length'] || '0', 10)
      let downloadedBytes = 0
      const writer = fs.createWriteStream(savePath)

      const report = (status, extra = {}) => {
        sendProgress(webContents, {
          key,
          status,
          percent: totalBytes > 0
            ? Math.min(100, Math.round((downloadedBytes / totalBytes) * 100))
            : null,
          received: downloadedBytes,
          total: totalBytes,
          savePath: status === 'done' ? savePath : undefined,
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
          activeRequests.delete(key)
          report('done')
          resolve(savePath)
        })
      })

      response.on('error', (err) => {
        activeRequests.delete(key)
        writer.destroy()
        fs.unlink(savePath, () => {})
        reject(err)
      })
    })

    request.on('error', (err) => {
      activeRequests.delete(key)
      reject(err)
    })

    request.end()
  })
}

async function startInstallerDownload(webContents, key) {
  const installer = INSTALLERS[key]
  if (!installer) {
    throw new Error('未知的安装包类型')
  }

  if (activeRequests.has(key)) {
    return { success: false, message: '该安装包正在下载中' }
  }

  const parentWindow = BrowserWindow.fromWebContents(webContents)
  const ext = installer.fileName.split('.').pop()?.toLowerCase() || 'msi'
  const { canceled, filePath } = await dialog.showSaveDialog(parentWindow ?? undefined, {
    title: `保存 ${installer.label} 安装包`,
    defaultPath: installer.fileName,
    filters: [{ name: 'Windows 安装包', extensions: [ext] }],
  })

  if (canceled || !filePath) {
    return { canceled: true }
  }

  sendProgress(webContents, { key, status: 'starting', percent: 0, received: 0, total: 0 })

  try {
    const savePath = await downloadToFile(webContents, {
      key,
      url: installer.url,
      savePath: filePath,
    })
    return { success: true, savePath }
  } catch (error) {
    sendProgress(webContents, {
      key,
      status: 'error',
      message: error.message || '下载失败',
    })
    return { success: false, message: error.message || '下载失败' }
  }
}

function cancelInstallerDownload(webContents, key) {
  const request = activeRequests.get(key)
  if (request) {
    request.abort()
    activeRequests.delete(key)
    if (webContents && !webContents.isDestroyed()) {
      sendProgress(webContents, { key, status: 'idle' })
    }
    return true
  }
  return false
}

module.exports = {
  INSTALLERS,
  startInstallerDownload,
  cancelInstallerDownload,
}
