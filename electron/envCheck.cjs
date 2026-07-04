const { execFile } = require('child_process')
const { promisify } = require('util')
const {
  INSTALLERS,
  MIN_JAVA_MAJOR,
  MIN_MYSQL_MAJOR,
  parseJavaMajor,
  parseMysqlMajor,
} = require('./installers.cjs')

const execFileAsync = promisify(execFile)

async function runShell(command) {
  try {
    const { stdout, stderr } = await execFileAsync('cmd.exe', ['/d', '/s', '/c', command], {
      windowsHide: true,
      timeout: 20000,
      maxBuffer: 1024 * 1024,
    })
    return {
      ok: true,
      stdout: String(stdout || '').trim(),
      stderr: String(stderr || '').trim(),
    }
  } catch (error) {
    return {
      ok: false,
      stdout: String(error.stdout || '').trim(),
      stderr: String(error.stderr || '').trim(),
      error: error.message,
    }
  }
}

function firstLine(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find(Boolean) || ''
}

function attachInstaller(item) {
  const installer = INSTALLERS[item.key]
  if (!installer) return item
  return {
    ...item,
    installer: {
      label: installer.label,
      fileName: installer.fileName,
      url: installer.url,
      installGuide: installer.installGuide || null,
    },
  }
}

function buildBundledItems(bundled = {}) {
  return [
    {
      key: 'frontend',
      label: '内置前端 (HTML)',
      installed: !!bundled.frontendReady,
      needsInstall: false,
      isBundled: true,
      statusLabel: bundled.frontendReady ? '已内置' : '缺失',
      version: '',
      path: bundled.distDisplayPath || '',
      hint: bundled.frontendReady
        ? '安装目录下 resources\\runtime\\dist，启动后会在「XXG-KAMI Frontend」终端中运行，可通过 http://127.0.0.1:5174 访问'
        : '未找到内置前端 dist，请重新打包桌面版',
    },
    {
      key: 'backend-jar',
      label: '内置后端 (JAR)',
      installed: !!bundled.jarExists,
      needsInstall: false,
      isBundled: true,
      statusLabel: bundled.jarExists ? '已内置' : '缺失',
      version: '',
      path: bundled.jarDisplayPath || '',
      hint: bundled.jarExists
        ? '安装目录下 resources\\runtime\\backend-0.0.1-SNAPSHOT.jar，点击「下一步」后执行 java -jar'
        : '未找到内置后端 JAR，请重新打包桌面版',
    },
  ]
}

async function checkJava() {
  const where = await runShell('where java')
  const pathLine = firstLine(where.stdout)
  if (!pathLine) {
    return attachInstaller({
      key: 'java',
      label: 'Java (JDK)',
      installed: false,
      needsInstall: true,
      versionTooLow: false,
      statusLabel: '未检测到',
      version: '',
      path: '',
      minVersion: MIN_JAVA_MAJOR,
      hint: `未检测到 Java，请下载并安装 JDK ${MIN_JAVA_MAJOR} 或以上（推荐 JDK 21），安装后重新检测。`,
    })
  }

  const ver = await runShell('java -version')
  const output = ver.stderr || ver.stdout
  const match = output.match(/version "([^"]+)"/)
  const version = match ? match[1] : firstLine(output)
  const major = parseJavaMajor(version)
  const versionOk = major >= MIN_JAVA_MAJOR

  if (!versionOk) {
    return attachInstaller({
      key: 'java',
      label: 'Java (JDK)',
      installed: false,
      needsInstall: true,
      versionTooLow: true,
      statusLabel: '版本过低',
      version,
      path: pathLine,
      minVersion: MIN_JAVA_MAJOR,
      hint: `当前 Java 版本为 ${version || '未知'}，需要 JDK ${MIN_JAVA_MAJOR} 或以上，请下载 JDK 21 安装包。`,
    })
  }

  return {
    key: 'java',
    label: 'Java (JDK)',
    installed: true,
    needsInstall: false,
    versionTooLow: false,
    statusLabel: '已就绪',
    version,
    path: pathLine,
    minVersion: MIN_JAVA_MAJOR,
    hint: `JDK ${MIN_JAVA_MAJOR}+ 检测通过，将用于运行内置后端 JAR`,
  }
}

async function checkMysqlService() {
  const ps = await runShell(
    'powershell -NoProfile -Command "Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match \'mysql\' -or $_.DisplayName -match \'mysql\' } | Select-Object -First 1 | ForEach-Object { $_.DisplayName + \' (\' + $_.Status + \')\' }"'
  )
  return firstLine(ps.stdout)
}

function validateMysqlVersion(versionText, pathLine) {
  const major = parseMysqlMajor(versionText)
  if (major > 0 && major < MIN_MYSQL_MAJOR) {
    return attachInstaller({
      key: 'mysql',
      label: 'MySQL',
      installed: false,
      needsInstall: true,
      versionTooLow: true,
      statusLabel: '版本过低',
      version: versionText,
      path: pathLine,
      minVersion: MIN_MYSQL_MAJOR,
      hint: `当前 MySQL 版本为 ${versionText || '未知'}，需要 MySQL ${MIN_MYSQL_MAJOR}.0 或以上，请安装 MySQL 8.0。`,
    })
  }
  return null
}

async function checkMysql() {
  const where = await runShell('where mysql')
  const pathLine = firstLine(where.stdout)

  if (pathLine) {
    const ver = await runShell('mysql --version')
    const version = firstLine(ver.stdout || ver.stderr)
    const tooLow = validateMysqlVersion(version, pathLine)
    if (tooLow) return tooLow
    return {
      key: 'mysql',
      label: 'MySQL',
      installed: true,
      needsInstall: false,
      statusLabel: '已就绪',
      version,
      path: pathLine,
      hint: `MySQL ${MIN_MYSQL_MAJOR}.0+ 检测通过，用于存储卡密与系统数据`,
    }
  }

  const serviceText = await checkMysqlService()
  if (serviceText) {
    return {
      key: 'mysql',
      label: 'MySQL',
      installed: true,
      needsInstall: false,
      statusLabel: '已就绪',
      version: serviceText,
      path: '',
      hint: '已检测到 MySQL 服务；若无法连接请确认服务已启动并完成初始化',
    }
  }

  return attachInstaller({
    key: 'mysql',
    label: 'MySQL',
    installed: false,
    needsInstall: true,
    statusLabel: '未检测到',
    version: '',
    path: '',
    hint: `未检测到 MySQL，请下载并安装 MySQL ${MIN_MYSQL_MAJOR}.0，安装后启动服务并重新检测。`,
  })
}

async function checkWindowsEnvironment(options = {}) {
  const bundled = options.bundled || {}
  const platform = process.platform

  if (platform !== 'win32') {
    return {
      platform,
      isWindows: false,
      checkedAt: new Date().toISOString(),
      envItems: [],
      bundledItems: buildBundledItems(bundled),
      items: [],
      allInstalled: true,
      mock: false,
      message: '当前非 Windows 环境，已跳过本地依赖检测',
    }
  }

  const [java, mysql] = await Promise.all([checkJava(), checkMysql()])
  const envItems = [java, mysql]
  const bundledItems = buildBundledItems(bundled)
  const items = [...envItems, ...bundledItems]
  const allInstalled = items.every((item) => item.installed)

  const missingLabels = items.filter((item) => !item.installed).map((item) => item.label)

  let message
  if (allInstalled) {
    message = '环境检测通过，点击「下一步」将启动内置前后端'
  } else {
    message = `请先安装缺失项（${missingLabels.join('、')}），完成后点击「重新检测」`
  }

  return {
    platform,
    isWindows: true,
    checkedAt: new Date().toISOString(),
    envItems,
    bundledItems,
    items,
    allInstalled,
    mock: false,
    message,
  }
}

module.exports = {
  checkWindowsEnvironment,
}
