import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const updateVisible = ref(false)
const cleanupVisible = ref(false)
const updateInfo = ref(null)
const downloadProgress = ref(null)
const downloading = ref(false)
const downloadedPath = ref('')
const installMode = ref('update')
const installDir = ref('')
const cleanupInfo = ref(null)
const autoChecked = ref(false)

let progressOff = null

function isDesktop() {
  return typeof window !== 'undefined' && !!window.electronAPI?.isDesktop
}

function dismissKey(version) {
  return `electron_update_dismissed_${version || 'unknown'}`
}

function isDismissed(version) {
  try {
    return sessionStorage.getItem(dismissKey(version)) === '1'
  } catch {
    return false
  }
}

function markDismissed(version) {
  try {
    sessionStorage.setItem(dismissKey(version), '1')
  } catch {
    // ignore
  }
}

function stopProgressListener() {
  if (progressOff) {
    progressOff()
    progressOff = null
  }
}

function bindProgressListener() {
  stopProgressListener()
  if (!window.electronAPI?.onElectronUpdateProgress) return
  progressOff = window.electronAPI.onElectronUpdateProgress((payload) => {
    if (payload.status === 'downloading') {
      downloadProgress.value = {
        status: 'downloading',
        percent: payload.percent ?? 0,
        message: payload.total
          ? `正在下载 ${payload.percent ?? 0}%`
          : `正在下载 ${Math.round((payload.received || 0) / 1024 / 1024)} MB`,
      }
    } else if (payload.status === 'starting') {
      downloadProgress.value = {
        status: 'starting',
        percent: 0,
        message: '准备下载…',
      }
    } else if (payload.status === 'done') {
      downloadProgress.value = {
        status: 'done',
        percent: 100,
        message: '下载完成',
        savePath: payload.savePath,
      }
    } else if (payload.status === 'error') {
      downloadProgress.value = {
        status: 'error',
        percent: 0,
        message: payload.message || '下载失败',
      }
      downloading.value = false
    }
  })
}

async function loadDesktopMeta() {
  if (!window.electronAPI?.getDesktopVersion) return
  try {
    const info = await window.electronAPI.getDesktopVersion()
    if (info?.recommendedInstallMode) installMode.value = info.recommendedInstallMode
    if (info?.installDir) installDir.value = info.installDir
  } catch {
    // ignore
  }
}

async function checkPendingCleanup() {
  if (!window.electronAPI?.getElectronUpdateCleanup) return false
  try {
    const pending = await window.electronAPI.getElectronUpdateCleanup()
    if (pending?.awaitingCleanup) {
      cleanupInfo.value = pending
      cleanupVisible.value = true
      return true
    }
  } catch {
    // ignore
  }
  return false
}

async function confirmCleanup() {
  if (!window.electronAPI?.confirmElectronUpdateCleanup) return
  try {
    const result = await window.electronAPI.confirmElectronUpdateCleanup()
    cleanupVisible.value = false
    cleanupInfo.value = null
    if (result?.deleted) {
      ElMessage.success('已删除下载的安装包')
    } else {
      ElMessage.success('更新完成')
    }
  } catch (error) {
    ElMessage.error(error.message || '清理失败')
  }
}

async function checkForUpdate({ silent = false, force = false } = {}) {
  if (!isDesktop() || !window.electronAPI?.checkElectronUpdate) {
    return null
  }

  await loadDesktopMeta()

  try {
    const result = await window.electronAPI.checkElectronUpdate()
    if (!result?.success) {
      if (!silent) ElMessage.error(result?.message || '检查更新失败')
      return null
    }

    if (!result.hasUpdate) {
      if (!silent) {
        const suffix = result.channelLabel ? `（${result.channelLabel} 通道）` : ''
        ElMessage.success(`桌面版已是最新版本 v${result.localVersion}${suffix}`)
      }
      return result
    }

    if (!force && isDismissed(result.remoteVersion)) {
      return result
    }

    updateInfo.value = {
      version: result.remoteVersion,
      buildDate: result.buildDate,
      changelog: result.changelog || [],
      repoUrl: result.repoUrl,
      releasesUrl: result.releasesUrl,
      downloadUrl: result.downloadUrl,
      alternateDownloadUrl: result.alternateDownloadUrl,
      channelLabel: result.channelLabel,
      localVersion: result.localVersion,
    }
    downloadProgress.value = null
    downloadedPath.value = ''
    updateVisible.value = true
    return result
  } catch (error) {
    if (!silent) ElMessage.error(error.message || '检查更新失败')
    return null
  }
}

async function autoCheckOnStartup() {
  if (!isDesktop() || autoChecked.value) return
  autoChecked.value = true

  const hasCleanup = await checkPendingCleanup()
  if (hasCleanup) return

  try {
    const meta = await window.electronAPI?.getDesktopVersion?.()
    if (meta && meta.isPackaged === false) return
  } catch {
    // ignore
  }

  await checkForUpdate({ silent: true })
}

async function startDownload() {
  const info = updateInfo.value
  const url = info?.downloadUrl || info?.alternateDownloadUrl
  if (!url) {
    ElMessage.error('未找到安装包下载地址')
    return
  }

  downloading.value = true
  downloadedPath.value = ''
  downloadProgress.value = { status: 'starting', percent: 0, message: '准备下载…' }
  bindProgressListener()

  try {
    const result = await window.electronAPI.downloadElectronUpdate({
      url,
      fileName: `XXG-KAMI-PRO-Setup-${info.version}.exe`,
      installMode: installMode.value,
      targetVersion: info.version,
    })

    if (!result?.success) {
      throw new Error(result?.message || '下载失败')
    }

    downloadedPath.value = result.savePath
    downloading.value = false

    try {
      await ElMessageBox.confirm(
        `安装包 v${info.version} 已下载完成，是否立即更新？\n\n程序将自动退出并启动安装向导。`,
        '下载完成',
        {
          type: 'info',
          confirmButtonText: '立即更新',
          cancelButtonText: '稍后',
          closeOnClickModal: false,
        }
      )
      await applyDownloadedUpdate()
    } catch {
      ElMessage.info('可稍后在系统信息页继续安装')
    }
  } catch (error) {
    downloading.value = false
    ElMessage.error(error.message || '下载失败')
  } finally {
    stopProgressListener()
  }
}

async function applyDownloadedUpdate() {
  const savePath = downloadedPath.value
  const info = updateInfo.value
  if (!savePath) {
    ElMessage.error('未找到已下载的安装包')
    return
  }

  bindProgressListener()
  try {
    const result = await window.electronAPI.applyElectronUpdate({
      savePath,
      installMode: installMode.value,
      targetVersion: info?.version || '',
    })
    if (!result?.success && !result?.quitting) {
      throw new Error(result?.message || '启动安装失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '启动安装失败')
    stopProgressListener()
  }
}

function dismissUpdate() {
  if (updateInfo.value?.version) {
    markDismissed(updateInfo.value.version)
  }
  updateVisible.value = false
  downloading.value = false
  downloadProgress.value = null
}

export function useElectronDesktopUpdate() {
  return {
    updateVisible,
    cleanupVisible,
    updateInfo,
    downloadProgress,
    downloading,
    downloadedPath,
    installMode,
    installDir,
    cleanupInfo,
    autoCheckOnStartup,
    checkForUpdate,
    startDownload,
    applyDownloadedUpdate,
    dismissUpdate,
    confirmCleanup,
    checkPendingCleanup,
  }
}
