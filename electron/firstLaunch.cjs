const fs = require('fs')
const path = require('path')
const { app } = require('electron')

const SETUP_MARKER_NAME = '.xxgkami-setup-complete.json'

function getInstallRoot() {
  if (app.isPackaged) {
    return path.dirname(process.execPath)
  }
  return path.join(__dirname, '..')
}

function getMarkerCandidates() {
  const installRoot = getInstallRoot()
  const userDataRoot = app.getPath('userData')
  const unique = new Set([
    path.join(installRoot, SETUP_MARKER_NAME),
    path.join(userDataRoot, SETUP_MARKER_NAME),
  ])
  return [...unique]
}

function readMarkerFile(filePath) {
  try {
    const raw = fs.readFileSync(filePath, 'utf8')
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function getFirstLaunchStatus() {
  for (const markerPath of getMarkerCandidates()) {
    if (!fs.existsSync(markerPath)) continue
    const data = readMarkerFile(markerPath)
    return {
      complete: true,
      markerPath,
      markerDisplayPath: toDisplayPath(markerPath),
      installRoot: getInstallRoot(),
      data,
    }
  }

  return {
    complete: false,
    markerPath: path.join(getInstallRoot(), SETUP_MARKER_NAME),
    markerDisplayPath: toDisplayPath(path.join(getInstallRoot(), SETUP_MARKER_NAME)),
    installRoot: getInstallRoot(),
    data: null,
  }
}

function toDisplayPath(absolutePath) {
  const installRoot = getInstallRoot()
  const rel = path.relative(installRoot, absolutePath)
  if (rel && !rel.startsWith('..') && !path.isAbsolute(rel)) {
    return process.platform === 'win32' ? rel.replace(/\//g, '\\') : rel
  }
  return absolutePath
}

function markFirstLaunchComplete(extra = {}) {
  const payload = {
    completedAt: new Date().toISOString(),
    installRoot: getInstallRoot(),
    appVersion: app.getVersion(),
    ...extra,
  }

  const candidates = app.isPackaged
    ? getMarkerCandidates()
    : [path.join(app.getPath('userData'), SETUP_MARKER_NAME)]

  let lastError = null
  for (const markerPath of candidates) {
    try {
      fs.mkdirSync(path.dirname(markerPath), { recursive: true })
      fs.writeFileSync(markerPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8')
      return {
        success: true,
        markerPath,
        markerDisplayPath: toDisplayPath(markerPath),
        data: payload,
      }
    } catch (error) {
      lastError = error
    }
  }

  return {
    success: false,
    message: lastError?.message || '无法写入首次启动标记文件',
  }
}

module.exports = {
  SETUP_MARKER_NAME,
  getInstallRoot,
  getFirstLaunchStatus,
  markFirstLaunchComplete,
}
