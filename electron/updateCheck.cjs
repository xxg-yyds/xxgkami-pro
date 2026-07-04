const { net } = require('electron')
const { DESKTOP_VERSION } = require('./appVersion.cjs')
const { REPO_SOURCES } = require('./repoSources.cjs')

const VERSION_JSON_URLS = {
  gitee: 'https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/raw/master/public/version.json',
  github: 'https://raw.githubusercontent.com/xxg-yyds/xxgkami-pro/refs/heads/master/public/version.json',
}

function compareVersions(a, b) {
  const pa = String(a || '0').replace(/^v/i, '').split('.')
  const pb = String(b || '0').replace(/^v/i, '').split('.')
  const len = Math.max(pa.length, pb.length)
  for (let i = 0; i < len; i += 1) {
    const na = parseInt(pa[i] || '0', 10) || 0
    const nb = parseInt(pb[i] || '0', 10) || 0
    if (na !== nb) return na - nb
  }
  return 0
}

function probeSite(url, timeoutMs = 4500) {
  return new Promise((resolve) => {
    const started = Date.now()
    const request = net.request({ method: 'HEAD', url })
    const timer = setTimeout(() => {
      request.abort()
      resolve({ ok: false, ms: Date.now() - started })
    }, timeoutMs)

    request.on('response', (response) => {
      clearTimeout(timer)
      resolve({
        ok: (response.statusCode || 0) < 400,
        ms: Date.now() - started,
      })
    })
    request.on('error', () => {
      clearTimeout(timer)
      resolve({ ok: false, ms: Date.now() - started })
    })
    request.end()
  })
}

async function detectPreferredChannel() {
  const [gitee, github] = await Promise.all([
    probeSite('https://gitee.com/'),
    probeSite('https://github.com/'),
  ])
  if (gitee.ok && !github.ok) return 'gitee'
  if (github.ok && !gitee.ok) return 'github'
  if (gitee.ok && github.ok) return gitee.ms <= github.ms ? 'gitee' : 'github'
  if (gitee.ok) return 'gitee'
  if (github.ok) return 'github'
  return 'gitee'
}

function fetchJson(url, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const request = net.request({ method: 'GET', url })
    const timer = setTimeout(() => {
      request.abort()
      reject(new Error('请求超时'))
    }, timeoutMs)

    let raw = ''
    request.on('response', (response) => {
      const status = response.statusCode || 0
      if (status >= 400) {
        clearTimeout(timer)
        reject(new Error(`HTTP ${status}`))
        response.resume()
        return
      }
      response.on('data', (chunk) => {
        raw += chunk.toString('utf8')
      })
      response.on('end', () => {
        clearTimeout(timer)
        try {
          resolve(JSON.parse(raw))
        } catch (error) {
          reject(error)
        }
      })
    })
    request.on('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
    request.end()
  })
}

function fillTemplate(template, version) {
  return String(template || '').replace(/\{version\}/g, version)
}

function resolveSetupUrl(electronNode, channel, version) {
  const downloads = electronNode?.releaseDownloads?.[channel]
  if (downloads?.setupExe) {
    return fillTemplate(downloads.setupExe, version)
  }
  const productName = electronNode?.productName || 'XXG-KAMI-PRO'
  const fileName = `${productName}-Setup-${version}.exe`
  const releasesUrl = REPO_SOURCES[channel]?.releasesUrl
  if (releasesUrl) {
    return `${releasesUrl}/download/electron-v${version}/${fileName}`
  }
  return ''
}

async function fetchRemoteVersionPayload() {
  const preferred = await detectPreferredChannel()
  const alternate = preferred === 'gitee' ? 'github' : 'gitee'
  const channels = [preferred, alternate]

  for (const channel of channels) {
    try {
      const json = await fetchJson(VERSION_JSON_URLS[channel])
      if (json?.electron?.version) {
        return { json, channel }
      }
    } catch {
      // try next channel
    }
  }

  throw new Error('无法获取远程 version.json 或缺少 electron 更新信息')
}

async function checkElectronUpdate() {
  const localVersion = DESKTOP_VERSION
  const { json, channel } = await fetchRemoteVersionPayload()
  const electronInfo = json.electron || {}
  const remoteVersion = String(electronInfo.version || '').trim()
  if (!remoteVersion) {
    throw new Error('远程 version.json 未配置 electron.version')
  }

  const hasUpdate = compareVersions(localVersion, remoteVersion) < 0
  const downloadUrl = resolveSetupUrl(electronInfo, channel, remoteVersion)
  const altChannel = channel === 'gitee' ? 'github' : 'gitee'
  const alternateDownloadUrl = resolveSetupUrl(electronInfo, altChannel, remoteVersion)

  return {
    success: true,
    channel,
    channelLabel: channel === 'gitee' ? 'Gitee' : 'GitHub',
    localVersion,
    remoteVersion,
    hasUpdate,
    buildDate: electronInfo.buildDate || json.buildDate || '',
    changelog: Array.isArray(electronInfo.changelog) ? electronInfo.changelog : [],
    repoUrl: REPO_SOURCES[channel]?.repoUrl || json.repoUrl || '',
    releasesUrl: REPO_SOURCES[channel]?.releasesUrl || '',
    downloadUrl,
    alternateDownloadUrl,
    bundledAppVersion: json.version || '',
    versionJsonUrl: VERSION_JSON_URLS[channel],
  }
}

function getDesktopVersionInfo() {
  const { BUNDLED_APP_VERSION, COMPANY_NAME, HOMEPAGE_URL } = require('./appVersion.cjs')
  return {
    desktopVersion: DESKTOP_VERSION,
    bundledAppVersion: BUNDLED_APP_VERSION,
    companyName: COMPANY_NAME,
    homepage: HOMEPAGE_URL,
  }
}

module.exports = {
  checkElectronUpdate,
  getDesktopVersionInfo,
  compareVersions,
}
