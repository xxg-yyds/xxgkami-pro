const https = require('https')
const { net } = require('electron')
const { getRepoSourceByRegion } = require('./repoSources.cjs')

const CN_CODES = new Set(['CN', 'HK', 'MO', 'TW'])

function fetchJson(url, timeoutMs = 6000) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { timeout: timeoutMs }, (res) => {
      if (res.statusCode && res.statusCode >= 400) {
        reject(new Error(`HTTP ${res.statusCode}`))
        res.resume()
        return
      }
      let raw = ''
      res.on('data', (chunk) => { raw += chunk })
      res.on('end', () => {
        try {
          resolve(JSON.parse(raw))
        } catch (error) {
          reject(error)
        }
      })
    })
    req.on('error', reject)
    req.on('timeout', () => {
      req.destroy(new Error('timeout'))
    })
  })
}

function probeHead(url, timeoutMs = 4500) {
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

async function detectRegionByIp() {
  const data = await fetchJson('https://ipwho.is/?fields=country_code,country,region')
  const code = String(data?.country_code || '').toUpperCase()
  if (!code) return null
  const region = CN_CODES.has(code) ? 'domestic' : 'international'
  return {
    region,
    countryCode: code,
    country: data?.country || '',
    source: 'ip',
  }
}

async function detectRegionByConnectivity() {
  const [gitee, github] = await Promise.all([
    probeHead('https://gitee.com'),
    probeHead('https://github.com'),
  ])

  if (gitee.ok && !github.ok) {
    return { region: 'domestic', source: 'connectivity' }
  }
  if (github.ok && !gitee.ok) {
    return { region: 'international', source: 'connectivity' }
  }
  if (gitee.ok && github.ok) {
    return {
      region: gitee.ms <= github.ms ? 'domestic' : 'international',
      source: 'connectivity',
    }
  }
  return null
}

function detectRegionByLocale() {
  try {
    const locale = Intl.DateTimeFormat().resolvedOptions().locale || ''
    if (/^zh(-cn|-hans)?$/i.test(locale) || locale.toLowerCase().includes('zh-cn')) {
      return { region: 'domestic', source: 'locale' }
    }
  } catch {
    // ignore
  }
  return { region: 'international', source: 'locale' }
}

async function detectNetworkRegion() {
  try {
    const byIp = await detectRegionByIp()
    if (byIp) return byIp
  } catch {
    // fallback
  }

  try {
    const byConn = await detectRegionByConnectivity()
    if (byConn) return byConn
  } catch {
    // fallback
  }

  return detectRegionByLocale()
}

async function buildRegionInfo() {
  const detected = await detectNetworkRegion()
  const repo = getRepoSourceByRegion(detected.region)
  return {
    ...detected,
    ...repo,
    detecting: false,
    hint:
      detected.region === 'domestic'
        ? '检测到国内网络环境，推荐使用 Gitee 获取源码与发行包'
        : '检测到国外网络环境，推荐使用 GitHub 获取源码与发行包',
  }
}

module.exports = {
  detectNetworkRegion,
  buildRegionInfo,
}
