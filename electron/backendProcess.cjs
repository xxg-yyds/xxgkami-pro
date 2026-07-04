const { spawn, execFileSync } = require('child_process')
const http = require('http')
const fs = require('fs')
const path = require('path')
const { app } = require('electron')
const { getPortOccupancy, isPort8080Conflict, waitForPortFree, clearPort, BACKEND_PORT: DEFAULT_BACKEND_PORT, isProcessRunning, getJavaListenerOnPort, killProcessesOnPort, getProcessInfoByList } = require('./portUtils.cjs')

const BACKEND_JAR_NAME = 'backend-0.0.1-SNAPSHOT.jar'
const HEALTH_CHECK_PATHS = ['/api/v1/health', '/api/maintenance/status']
const BACKEND_HOST = '127.0.0.1'
const BACKEND_PORT = 8080
const STARTUP_TIMEOUT_MS = 120000
const POLL_INTERVAL_MS = 800
const LOG_BUFFER_MAX = 120
const MYSQL_CONFIG_FILE = 'mysql-config.json'
const BACKEND_TERMINAL_TITLE = 'XXG-KAMI Backend'
const BACKEND_START_SCRIPT = 'start-backend.bat'
const BACKEND_LAUNCHER_VBS = 'open-backend-terminal.vbs'

/** @type {import('child_process').ChildProcess | null} */
let backendProcess = null
/** @type {boolean} */
let backendLaunchedInTerminal = false
/** @type {number | null} */
let backendJavaPid = null
/** @type {string[]} */
let logBuffer = []
/** @type {Promise<any> | null} */
let activeStartPromise = null

function getBackendWorkDir() {
  const dir = path.join(app.getPath('userData'), 'backend-runtime')
  fs.mkdirSync(path.join(dir, 'data'), { recursive: true })
  fs.mkdirSync(path.join(dir, 'logs'), { recursive: true })
  return dir
}

function getMysqlConfigPath() {
  return path.join(getBackendWorkDir(), MYSQL_CONFIG_FILE)
}

function loadMysqlConfig() {
  try {
    const raw = fs.readFileSync(getMysqlConfigPath(), 'utf8')
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function saveMysqlConfig(config) {
  const cwd = getBackendWorkDir()
  fs.writeFileSync(getMysqlConfigPath(), JSON.stringify(config, null, 2), 'utf8')
  return path.join(cwd, MYSQL_CONFIG_FILE)
}

const BUNDLED_RUNTIME_DIR = path.join('resources', 'runtime')
const BUNDLED_FRONTEND_REL = path.join(BUNDLED_RUNTIME_DIR, 'dist')
const BUNDLED_JAR_REL = path.join(BUNDLED_RUNTIME_DIR, BACKEND_JAR_NAME)
const DEV_JAR_REL = path.join('backend', 'target', BACKEND_JAR_NAME)
const DEV_DIST_REL = 'dist'

function getInstallRoot() {
  if (app.isPackaged) {
    return path.dirname(process.execPath)
  }
  return path.join(__dirname, '..')
}

function formatPathForDisplay(relativePath) {
  const normalized = String(relativePath || '').replace(/\\/g, '/')
  if (!normalized) return ''
  return process.platform === 'win32' ? normalized.replace(/\//g, '\\') : normalized
}

function toBundledDisplayPath(absolutePath, fallbackRelative) {
  if (absolutePath) {
    const rel = path.relative(getInstallRoot(), absolutePath)
    if (rel && !rel.startsWith('..') && !path.isAbsolute(rel)) {
      return formatPathForDisplay(rel)
    }
  }
  return formatPathForDisplay(fallbackRelative || '')
}

function getBundledFrontendDisplayPath(bundledDistPath) {
  if (app.isPackaged) {
    return formatPathForDisplay(BUNDLED_FRONTEND_REL)
  }
  return toBundledDisplayPath(bundledDistPath, DEV_DIST_REL)
}

function getBundledJarDisplayPath(jarPath) {
  if (app.isPackaged) {
    return formatPathForDisplay(BUNDLED_JAR_REL)
  }
  return toBundledDisplayPath(jarPath, DEV_JAR_REL)
}

function resolveJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'runtime', BACKEND_JAR_NAME)
  }
  const devJar = path.join(__dirname, '..', 'backend', 'target', BACKEND_JAR_NAME)
  return fs.existsSync(devJar) ? devJar : null
}

function resolveBundledDistPath() {
  if (app.isPackaged) {
    const extraDist = path.join(process.resourcesPath, 'runtime', 'dist')
    return fs.existsSync(path.join(extraDist, 'index.html')) ? extraDist : null
  }
  const devDist = path.join(__dirname, '..', 'dist')
  return fs.existsSync(path.join(devDist, 'index.html')) ? devDist : null
}

function appendLog(line, onLog) {
  const text = String(line || '').trimEnd()
  if (!text) return
  logBuffer.push(text)
  if (logBuffer.length > LOG_BUFFER_MAX) {
    logBuffer = logBuffer.slice(-LOG_BUFFER_MAX)
  }
  onLog?.(text)
}

function buildJavaCommand(jarPath, mysqlConfig) {
  const parts = ['java', '-jar', `"${jarPath}"`]
  if (mysqlConfig?.host) {
    const host = mysqlConfig.host
    const port = mysqlConfig.port || 3306
    const db = mysqlConfig.database || 'kami'
    parts.push(
      `--spring.datasource.url=jdbc:mysql://${host}:${port}/${db}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`
    )
  }
  if (mysqlConfig?.username) {
    parts.push(`--spring.datasource.username=${mysqlConfig.username}`)
  }
  if (mysqlConfig?.password != null) {
    parts.push(`--spring.datasource.password=${mysqlConfig.password}`)
  }
  return parts.join(' ')
}

function buildJavaArgs(jarPath, mysqlConfig) {
  const args = ['-jar', jarPath]
  if (mysqlConfig?.host) {
    const host = mysqlConfig.host
    const port = mysqlConfig.port || 3306
    const db = mysqlConfig.database || 'kami'
    args.push(
      `--spring.datasource.url=jdbc:mysql://${host}:${port}/${db}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`
    )
  }
  if (mysqlConfig?.username) {
    args.push(`--spring.datasource.username=${mysqlConfig.username}`)
  }
  if (mysqlConfig?.password != null) {
    args.push(`--spring.datasource.password=${mysqlConfig.password}`)
  }
  return args
}

function parseStartupError(logs, exitCode) {
  const text = logs.join('\n')
  if (/Access denied for user/i.test(text)) {
    return 'MySQL 连接被拒绝：用户名或密码不正确。请在下方填写本机 MySQL 账号密码后重试。'
  }
  if (/Communications link failure|Connection refused/i.test(text)) {
    return '无法连接 MySQL：请确认 MySQL 服务已启动，端口与账号配置正确。'
  }
  if (/Unknown database/i.test(text)) {
    return 'MySQL 数据库不存在：请先创建 kami 库，或修改数据库名称后重试。'
  }
  if (/Address already in use|BindException.*8080|Port 8080 was already in use/i.test(text)) {
    return `端口 ${DEFAULT_BACKEND_PORT} 已被占用，启动程序已尝试自动释放；若仍失败请关闭其他终端中的 java 进程后重试。`
  }
  if (/No static resource v1\/health|NoResourceFoundException.*v1\/health/i.test(text)) {
    return '当前内置后端 JAR 版本较旧，缺少 /api/v1/health 接口。请重新执行 mvn package 并重启桌面版，或重新打包安装程序。'
  }
  if (/java' 不是内部或外部命令|java is not recognized/i.test(text)) {
    return '未找到 java 命令：请安装 JDK 20+ 并配置 PATH 环境变量。'
  }
  if (exitCode != null) {
    return `后端进程异常退出（代码 ${exitCode}），请查看下方启动日志。`
  }
  return '后端启动失败，请查看下方启动日志。'
}

function probeHttpPath(path) {
  return new Promise((resolve) => {
    const req = http.request(
      {
        host: BACKEND_HOST,
        port: BACKEND_PORT,
        path,
        method: 'GET',
        timeout: 3000,
      },
      (res) => {
        resolve(res.statusCode >= 200 && res.statusCode < 300)
        res.resume()
      }
    )
    req.on('error', () => resolve(false))
    req.on('timeout', () => {
      req.destroy()
      resolve(false)
    })
    req.end()
  })
}

/** @type {string | null} */
let lastHealthyPath = null

async function probeHealth(onLog) {
  for (const path of HEALTH_CHECK_PATHS) {
    const ok = await probeHttpPath(path)
    if (ok) {
      if (path !== lastHealthyPath) {
        appendLog(`[检测] API 就绪：http://${BACKEND_HOST}:${BACKEND_PORT}${path}`, onLog)
      }
      lastHealthyPath = path
      return true
    }
  }
  return false
}

function getHealthCheckUrl() {
  const path = lastHealthyPath || HEALTH_CHECK_PATHS[0]
  return `http://${BACKEND_HOST}:${BACKEND_PORT}${path}`
}

function logJarInfo(jarPath, onLog) {
  if (!jarPath) return
  const displayPath = getBundledJarDisplayPath(jarPath)
  try {
    const stat = fs.statSync(jarPath)
    appendLog(`[配置] 后端 JAR：${displayPath}`, onLog)
    appendLog(`[配置] JAR 修改时间：${stat.mtime.toLocaleString()}`, onLog)
  } catch {
    appendLog(`[配置] 后端 JAR：${displayPath}`, onLog)
  }
}

async function buildPortInUseFailure(extra = {}) {
  const portInfo = await getPortOccupancy(DEFAULT_BACKEND_PORT)
  const procText = portInfo.processes.map((p) => `${p.name} (PID ${p.pid})`).join('、')
  return {
    started: false,
    portInUse: true,
    port: DEFAULT_BACKEND_PORT,
    portInfo,
    message: procText
      ? `端口 ${DEFAULT_BACKEND_PORT} 已被占用：${procText}`
      : `端口 ${DEFAULT_BACKEND_PORT} 已被占用`,
    logs: [...logBuffer],
    ...extra,
  }
}

async function waitForBackendReady(onLog, waitContext = {}) {
  const { spawnedFresh = false, blockedPids = [] } = waitContext
  const blockedPidSet = new Set(blockedPids)
  const deadline = Date.now() + STARTUP_TIMEOUT_MS
  let lastListenerPid = null

  while (Date.now() < deadline) {
    const listener = await getJavaListenerOnPort(DEFAULT_BACKEND_PORT)
    if (listener?.pid) {
      lastListenerPid = listener.pid
      if (!backendJavaPid) {
        backendJavaPid = listener.pid
      }
    }

    if (await probeHealth(onLog)) {
      if (spawnedFresh) {
        const currentListener = await getJavaListenerOnPort(DEFAULT_BACKEND_PORT)
        const listenerPid = currentListener?.pid

        if (listenerPid && blockedPidSet.has(listenerPid)) {
          appendLog(
            `[检测] 8080 仍被旧进程占用 (PID ${listenerPid})，新后端尚未接管端口`,
            onLog
          )
        } else if (backendJavaPid && listenerPid && listenerPid !== backendJavaPid) {
          appendLog(
            `[检测] 8080 监听 PID ${listenerPid} 与本次启动的 Java PID ${backendJavaPid} 不一致`,
            onLog
          )
        } else if (backendJavaPid && !(await isProcessRunning(backendJavaPid))) {
          return {
            ok: false,
            portInUse: !!(await getPortOccupancy(DEFAULT_BACKEND_PORT)).inUse,
            message: `后端 Java 进程 (PID ${backendJavaPid}) 已退出，请查看「${BACKEND_TERMINAL_TITLE}」终端窗口中的日志。`,
            logs: [...logBuffer],
          }
        } else if (listenerPid && (!backendJavaPid || listenerPid === backendJavaPid)) {
          backendJavaPid = listenerPid
          return { ok: true, healthUrl: getHealthCheckUrl() }
        } else if (!listenerPid) {
          appendLog('[检测] API 已响应但 8080 尚未出现 Java 监听，继续等待…', onLog)
        } else {
          appendLog('[检测] 等待本次启动的后端接管 8080…', onLog)
        }
      } else {
        return { ok: true, healthUrl: getHealthCheckUrl() }
      }
    }

    if (backendLaunchedInTerminal) {
      if (backendJavaPid && !(await isProcessRunning(backendJavaPid))) {
        const portInUse = (await getPortOccupancy(DEFAULT_BACKEND_PORT)).inUse
        return {
          ok: false,
          portInUse,
          message: portInUse
            ? `端口 ${DEFAULT_BACKEND_PORT} 已被占用，新启动的后端已退出。请先清除端口后重试。`
            : `后端 Java 进程 (PID ${backendJavaPid}) 已退出，请查看「${BACKEND_TERMINAL_TITLE}」终端窗口中的日志。`,
          logs: [...logBuffer],
        }
      }
    } else if (!backendProcess || backendProcess.exitCode != null) {
      const exitCode = backendProcess?.exitCode
      const logs = [...logBuffer]
      const portInUse = isPort8080Conflict(logs.join('\n'))
      return {
        ok: false,
        exitCode,
        portInUse,
        message: portInUse
          ? `端口 ${DEFAULT_BACKEND_PORT} 已被占用，启动程序已尝试自动释放；若仍失败请关闭其他终端中的 java 进程后重试`
          : parseStartupError(logBuffer, exitCode),
        logs,
      }
    }

    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS))
  }

  const portInUse = (await getPortOccupancy(DEFAULT_BACKEND_PORT)).inUse
  const stalePid = lastListenerPid && blockedPidSet.has(lastListenerPid)
  return {
    ok: false,
    portInUse: portInUse || stalePid,
    message: stalePid
      ? `端口 ${DEFAULT_BACKEND_PORT} 仍被旧进程 (PID ${lastListenerPid}) 占用，新后端未能启动。请先清除端口后重试。`
      : backendLaunchedInTerminal
        ? `后端在 ${STARTUP_TIMEOUT_MS / 1000} 秒内未就绪，请查看「${BACKEND_TERMINAL_TITLE}」终端窗口中的启动日志。`
        : `后端在 ${STARTUP_TIMEOUT_MS / 1000} 秒内未就绪，请查看启动日志。`,
    logs: [...logBuffer],
  }
}

function quoteBatchArg(arg) {
  return `"${String(arg).replace(/"/g, '""')}"`
}

function writeBackendInfoBatch(cwd, lines) {
  const scriptPath = path.join(cwd, 'backend-info.bat')
  const content = [
    '@echo off',
    'chcp 65001 >nul',
    `title ${BACKEND_TERMINAL_TITLE}`,
    ...lines,
    'echo.',
    'pause',
  ].join('\r\n')
  fs.writeFileSync(scriptPath, `${content}\r\n`, 'utf8')
  return scriptPath
}

function escapeVbsString(value) {
  return String(value).replace(/"/g, '""')
}

function writeBackendLauncherVbs(cwd, scriptPath) {
  const vbsPath = path.join(cwd, BACKEND_LAUNCHER_VBS)
  const content = [
    'Set WshShell = CreateObject("WScript.Shell")',
    `WshShell.CurrentDirectory = "${escapeVbsString(cwd)}"`,
    `WshShell.Run "cmd.exe /k ""${escapeVbsString(scriptPath)}""", 1, False`,
  ].join('\r\n')
  fs.writeFileSync(vbsPath, `${content}\r\n`, 'utf8')
  return vbsPath
}

function openWindowsTerminal(scriptPath, cwd, onLog) {
  appendLog(`[启动] 打开终端脚本：${scriptPath}`, onLog)

  const vbsPath = writeBackendLauncherVbs(cwd, scriptPath)
  const launcher = spawn('wscript.exe', ['//Nologo', vbsPath], {
    cwd,
    windowsHide: true,
    detached: true,
    stdio: 'ignore',
    env: process.env,
  })

  launcher.on('error', (err) => {
    appendLog(`[错误] 无法打开 CMD 窗口：${err.message}`, onLog)
  })
  launcher.unref()
  return launcher
}

function buildBackendBatchContent(cwd, javaArgs) {
  const jarPath = javaArgs[1]
  const javaCmd = `java ${javaArgs.map(quoteBatchArg).join(' ')}`
  return [
    '@echo off',
    'chcp 65001 >nul',
    `title ${BACKEND_TERMINAL_TITLE}`,
    `cd /d ${quoteBatchArg(cwd)}`,
    'set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8',
    'echo [XXG-KAMI] Java backend starting...',
    `echo JAR: ${quoteBatchArg(jarPath)}`,
    `if not exist ${quoteBatchArg(jarPath)} (`,
    '  echo.',
    '  echo [ERROR] JAR file not found.',
    '  echo Please verify: resources\\runtime\\backend-0.0.1-SNAPSHOT.jar',
    '  echo.',
    '  pause',
    '  exit /b 1',
    ')',
    'echo.',
    javaCmd,
    'echo.',
    'echo [XXG-KAMI] Backend process exited.',
  ].join('\r\n')
}

function writeBackendStartBatch(cwd, javaArgs) {
  const scriptPath = path.join(cwd, BACKEND_START_SCRIPT)
  fs.writeFileSync(scriptPath, `${buildBackendBatchContent(cwd, javaArgs)}\r\n`, 'utf8')
  return scriptPath
}

async function trackJavaPidFromPort(maxWaitMs = 45000) {
  const deadline = Date.now() + maxWaitMs
  while (Date.now() < deadline) {
    const occupancy = await getPortOccupancy(DEFAULT_BACKEND_PORT)
    const javaProc = occupancy.processes.find((p) => /^java(\.exe)?$/i.test(p.name))
    if (javaProc) {
      backendJavaPid = javaProc.pid
      return javaProc.pid
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  return null
}

function attachBackendProcessHandlers(onLog) {
  if (!backendProcess) return
  backendProcess.stdout?.on('data', (chunk) => {
    appendLog(chunk.toString(), onLog)
    console.log('[backend]', chunk.toString().trimEnd())
  })
  backendProcess.stderr?.on('data', (chunk) => {
    appendLog(chunk.toString(), onLog)
    console.error('[backend]', chunk.toString().trimEnd())
  })
  backendProcess.on('exit', (code, signal) => {
    appendLog(`[进程退出] code=${code ?? 'null'} signal=${signal ?? 'null'}`, onLog)
    console.log('[backend] process exited', { code, signal })
    backendProcess = null
  })
}

function spawnJavaBackendHidden(javaArgs, cwd, onLog) {
  backendLaunchedInTerminal = false
  backendJavaPid = null
  backendProcess = spawn('java', javaArgs, {
    cwd,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      JAVA_TOOL_OPTIONS: '-Dfile.encoding=UTF-8',
    },
  })
  attachBackendProcessHandlers(onLog)
  return backendProcess
}

function spawnJavaBackendInTerminal(javaArgs, cwd, onLog) {
  backendLaunchedInTerminal = true
  backendJavaPid = null
  backendProcess = null

  const jarPath = javaArgs[1]
  if (!jarPath || !fs.existsSync(jarPath)) {
    appendLog(`[错误] 未找到后端 JAR：${jarPath || '(空)'}`, onLog)
    appendLog('[提示] 安装包内应为 resources\\runtime\\backend-0.0.1-SNAPSHOT.jar', onLog)
  } else {
    appendLog(`[配置] JAR 绝对路径：${jarPath}`, onLog)
  }

  const scriptPath = writeBackendStartBatch(cwd, javaArgs)
  openWindowsTerminal(scriptPath, cwd, onLog)

  appendLog(`[启动] 已在独立 CMD 窗口启动 Java 后端（标题：${BACKEND_TERMINAL_TITLE}）`, onLog)
  appendLog('[提示] Spring Boot 日志请查看弹出的黑色 CMD 窗口', onLog)

  trackJavaPidFromPort().then((pid) => {
    if (pid) {
      appendLog(`[检测] Java 进程 PID ${pid}（监听 ${DEFAULT_BACKEND_PORT}）`, onLog)
    }
  })

  return backendProcess
}

function spawnJavaBackend(javaArgs, cwd, onLog) {
  if (process.platform === 'win32') {
    return spawnJavaBackendInTerminal(javaArgs, cwd, onLog)
  }
  return spawnJavaBackendHidden(javaArgs, cwd, onLog)
}

async function logPortOccupancy(onLog, prefix = '[检测]') {
  appendLog(`${prefix} 执行 netstat -ano | findstr :${DEFAULT_BACKEND_PORT}`, onLog)
  const occupancy = await getPortOccupancy(DEFAULT_BACKEND_PORT)
  if (!occupancy.inUse) {
    appendLog(`${prefix} 端口 ${DEFAULT_BACKEND_PORT} 未被占用`, onLog)
    return occupancy
  }

  appendLog(`${prefix} 端口 ${DEFAULT_BACKEND_PORT} 已被占用`, onLog)
  for (const proc of occupancy.processes) {
    const addr = proc.localAddress ? ` · ${proc.localAddress}` : ''
    appendLog(`${prefix} ${proc.name} · PID ${proc.pid}${addr}`, onLog)
    if (proc.tasklist) {
      const imageLine = proc.tasklist.split(/\r?\n/).find((line) => /Image Name:/i.test(line))
      if (imageLine) {
        appendLog(`${prefix} tasklist ${imageLine.trim()}`, onLog)
      }
    }
  }
  return occupancy
}

async function ensurePortAvailableForStart(onLog, options = {}) {
  const forceRestart = !!options.forceRestart
  const allowReuse = !!options.allowReuse

  let occupancy = await logPortOccupancy(onLog)
  const blockedPids = occupancy.processes.map((proc) => proc.pid)

  if (!occupancy.inUse) {
    return { ok: true, cleared: false, blockedPids: [] }
  }

  if (!forceRestart && allowReuse && (await probeHealth(onLog))) {
    appendLog('[检测] 端口 8080 上的 API 服务已可用，跳过重复启动', onLog)
    appendLog('[提示] 若需重启后端，请使用「一键清除并重新启动」', onLog)
    return { ok: true, reused: true, cleared: false, blockedPids }
  }

  appendLog(
    `[清除] 正在释放端口 ${DEFAULT_BACKEND_PORT}（netstat + tasklist + taskkill /F /T）…`,
    onLog
  )
  stopBackendProcess()
  const clearResult = await clearPort(DEFAULT_BACKEND_PORT)

  for (const killed of clearResult.killed || []) {
    appendLog(`[清除] taskkill 已结束 ${killed.name} · PID ${killed.pid}`, onLog)
  }
  for (const failed of clearResult.failed || []) {
    appendLog(`[清除] 结束失败 ${failed.name} · PID ${failed.pid}：${failed.error}`, onLog)
  }
  if (clearResult.message) {
    appendLog(`[清除] ${clearResult.message}`, onLog)
  }

  if (!clearResult.success) {
    occupancy = await getPortOccupancy(DEFAULT_BACKEND_PORT)
    return {
      ok: false,
      portInUse: true,
      portInfo: occupancy,
      blockedPids,
      message:
        clearResult.message ||
        `端口 ${DEFAULT_BACKEND_PORT} 仍被占用，请关闭其他终端里的 java -jar 后重试`,
    }
  }

  const waitResult = await waitForPortFree(DEFAULT_BACKEND_PORT, 12000)
  if (!waitResult.ok) {
    occupancy = await getPortOccupancy(DEFAULT_BACKEND_PORT)
    return {
      ok: false,
      portInUse: true,
      portInfo: occupancy,
      blockedPids,
      message: `端口 ${DEFAULT_BACKEND_PORT} 释放超时，仍被占用`,
    }
  }

  appendLog(`[检测] 端口 ${DEFAULT_BACKEND_PORT} 已释放，可以启动后端`, onLog)
  return { ok: true, cleared: true, blockedPids }
}

async function assertPortReadyBeforeSpawn(onLog) {
  appendLog('[检测] 启动 Java 前再次执行 netstat -ano | findstr :8080', onLog)
  const occupancy = await getPortOccupancy(DEFAULT_BACKEND_PORT)
  if (occupancy.inUse) {
    for (const proc of occupancy.processes) {
      appendLog(`[占用] 启动前仍占用：${proc.name} · PID ${proc.pid}`, onLog)
      const info = await getProcessInfoByList(proc.pid)
      if (info.detail) {
        appendLog(`[占用] tasklist /FI "PID eq ${proc.pid}" /FO LIST`, onLog)
      }
    }

    appendLog('[清除] 启动前尝试 taskkill 占用 8080 的进程…', onLog)
    const killResult = await killProcessesOnPort(DEFAULT_BACKEND_PORT)
    for (const killed of killResult.killed) {
      appendLog(`[清除] taskkill 已结束 ${killed.name} · PID ${killed.pid}`, onLog)
    }
    await waitForPortFree(DEFAULT_BACKEND_PORT, 8000)
    const after = await getPortOccupancy(DEFAULT_BACKEND_PORT)
    if (after.inUse) {
      return {
        ok: false,
        portInUse: true,
        portInfo: after,
        message: `启动 Java 前检测到端口 ${DEFAULT_BACKEND_PORT} 仍被占用，请先清除端口后重试。`,
      }
    }
  }

  appendLog(`[检测] 启动前确认：端口 ${DEFAULT_BACKEND_PORT} 未被占用`, onLog)
  return { ok: true }
}

function startBackendProcess(options = {}) {
  if (activeStartPromise && !options.forceRestart) {
    return activeStartPromise
  }

  activeStartPromise = doStartBackendProcess(options).finally(() => {
    activeStartPromise = null
  })
  return activeStartPromise
}

function doStartBackendProcess(options = {}) {
  logBuffer = []
  backendLaunchedInTerminal = false
  backendJavaPid = null
  const onLog = options.onLog
  const forceRestart = !!options.forceRestart
  const mysqlConfig = options.mysqlConfig || loadMysqlConfig()
  const jarPath = resolveJarPath()

  if (!jarPath) {
    return Promise.resolve({
      started: false,
      reason: 'jar_not_found',
      message: '未找到内置后端 JAR',
      logs: [],
      command: '',
    })
  }
  if (!fs.existsSync(jarPath)) {
    return Promise.resolve({
      started: false,
      reason: 'jar_missing',
      message: `后端 JAR 不存在: ${jarPath}`,
      jarPath,
      logs: [],
      command: buildJavaCommand(jarPath, mysqlConfig),
    })
  }

  const command = buildJavaCommand(jarPath, mysqlConfig)

  if (forceRestart) {
    stopBackendProcess()
  }

  return ensurePortAvailableForStart(onLog, { forceRestart, allowReuse: !!options.allowReuse }).then(async (portResult) => {
    if (portResult.reused) {
      const cwd = getBackendWorkDir()
      appendLog('[提示] 8080 端口已有后端在运行，未重新执行 java -jar', onLog)
      return {
        started: true,
        jarPath,
        workDir: cwd,
        reusedExisting: true,
        command,
        healthUrl: getHealthCheckUrl(),
        logs: [...logBuffer],
      }
    }

    if (!portResult.ok) {
      return buildPortInUseFailure({
        command,
        jarPath,
        message: portResult.message,
      })
    }

    const cwd = getBackendWorkDir()
    const javaArgs = buildJavaArgs(jarPath, mysqlConfig)
    const waitContext = {
      spawnedFresh: true,
      blockedPids: portResult.blockedPids || [],
    }

    appendLog(`[启动] ${command}`, onLog)
    logJarInfo(jarPath, onLog)

    const preSpawn = await assertPortReadyBeforeSpawn(onLog)
    if (!preSpawn.ok) {
      return buildPortInUseFailure({
        command,
        jarPath,
        message: preSpawn.message,
        portInfo: preSpawn.portInfo,
      })
    }

    spawnJavaBackend(javaArgs, cwd, onLog)
    await trackJavaPidFromPort(20000)

    let waitResult = await waitForBackendReady(onLog, waitContext)

    if (!waitResult.ok && waitResult.portInUse) {
      appendLog('[重试] 检测到端口冲突，再次尝试自动释放并启动…', onLog)
      stopBackendProcess()
      const retryPort = await ensurePortAvailableForStart(onLog, { forceRestart: true })
      if (retryPort.ok && !retryPort.reused) {
        backendJavaPid = null
        const preSpawn = await assertPortReadyBeforeSpawn(onLog)
        if (!preSpawn.ok) {
          return buildPortInUseFailure({
            command,
            jarPath,
            message: preSpawn.message,
            portInfo: preSpawn.portInfo,
          })
        }
        spawnJavaBackend(javaArgs, cwd, onLog)
        await trackJavaPidFromPort(20000)
        waitResult = await waitForBackendReady(onLog, {
          spawnedFresh: true,
          blockedPids: retryPort.blockedPids || [],
        })
      }
    }

    if (!waitResult.ok) {
      if (waitResult.portInUse) {
        return buildPortInUseFailure({
          message: waitResult.message,
          logs: waitResult.logs || [...logBuffer],
          command,
          jarPath,
          workDir: cwd,
          exitCode: waitResult.exitCode,
        })
      }
      return {
        started: false,
        message: waitResult.message,
        logs: waitResult.logs || [...logBuffer],
        command,
        jarPath,
        workDir: cwd,
        exitCode: waitResult.exitCode,
      }
    }
    return {
      started: true,
      jarPath,
      workDir: cwd,
      command,
      healthUrl: waitResult.healthUrl || getHealthCheckUrl(),
      logs: [...logBuffer],
    }
  })
}

function stopBackendProcess() {
  if (backendLaunchedInTerminal) {
    try {
      if (backendJavaPid) {
        execFileSync('cmd.exe', ['/d', '/s', '/c', `taskkill /PID ${backendJavaPid} /F /T`], {
          windowsHide: true,
          timeout: 15000,
        })
      } else {
        execFileSync(
          'cmd.exe',
          [
            '/d',
            '/s',
            '/c',
            `powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort ${DEFAULT_BACKEND_PORT} -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"`,
          ],
          { windowsHide: true, timeout: 15000 }
        )
      }
    } catch {
      // ignore — process may already have exited
    }
  } else if (backendProcess && backendProcess.exitCode == null) {
    try {
      backendProcess.kill('SIGTERM')
    } catch {
      try {
        backendProcess.kill()
      } catch {
        // ignore
      }
    }
  }
  backendProcess = null
  backendLaunchedInTerminal = false
  backendJavaPid = null
}

function getBackendStatus() {
  const jarPath = resolveJarPath()
  const bundledDistPath = resolveBundledDistPath()
  const mysqlConfig = loadMysqlConfig()
  return {
    jarPath,
    jarDisplayPath: getBundledJarDisplayPath(jarPath),
    jarExists: !!(jarPath && fs.existsSync(jarPath)),
    bundledDistPath,
    distDisplayPath: getBundledFrontendDisplayPath(bundledDistPath),
    installRoot: getInstallRoot(),
    frontendReady: !!(bundledDistPath && fs.existsSync(path.join(bundledDistPath, 'index.html'))),
    workDir: getBackendWorkDir(),
    running: backendLaunchedInTerminal ? !!backendJavaPid : !!(backendProcess && backendProcess.exitCode == null),
    backendTerminalTitle: backendLaunchedInTerminal ? BACKEND_TERMINAL_TITLE : null,
    backendJavaPid,
    healthUrl: getHealthCheckUrl(),
    mysqlConfig,
  }
}

module.exports = {
  startBackendProcess,
  stopBackendProcess,
  getBackendStatus,
  saveMysqlConfig,
  loadMysqlConfig,
  resolveJarPath,
  resolveBundledDistPath,
  buildJavaCommand,
  getHealthCheckUrl,
  getBundledFrontendDisplayPath,
  getBundledJarDisplayPath,
}
