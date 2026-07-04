const fs = require('fs')
const path = require('path')
const { app } = require('electron')

const STATE_FILE = 'electron-update-state.json'

function getStatePath() {
  return path.join(app.getPath('userData'), STATE_FILE)
}

function readState() {
  try {
    const filePath = getStatePath()
    if (!fs.existsSync(filePath)) return null
    return JSON.parse(fs.readFileSync(filePath, 'utf8'))
  } catch {
    return null
  }
}

function writeState(state) {
  const filePath = getStatePath()
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
  fs.writeFileSync(filePath, JSON.stringify(state || {}, null, 2), 'utf8')
}

function markPendingCleanup(payload = {}) {
  writeState({
    installerPath: payload.installerPath || '',
    targetVersion: payload.targetVersion || '',
    previousVersion: payload.previousVersion || '',
    installMode: payload.installMode || 'update',
    awaitingCleanup: true,
    markedAt: new Date().toISOString(),
  })
}

function getPendingCleanup() {
  const state = readState()
  if (!state?.awaitingCleanup) return null
  const installerPath = String(state.installerPath || '').trim()
  return {
    ...state,
    installerPath,
    fileExists: !!(installerPath && fs.existsSync(installerPath)),
  }
}

function cleanupDownloadedInstaller() {
  const state = readState()
  const installerPath = String(state?.installerPath || '').trim()
  let deleted = false
  if (installerPath && fs.existsSync(installerPath)) {
    fs.unlinkSync(installerPath)
    deleted = true
  }
  writeState({})
  return {
    success: true,
    deleted,
    installerPath,
  }
}

module.exports = {
  markPendingCleanup,
  getPendingCleanup,
  cleanupDownloadedInstaller,
}
