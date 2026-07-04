const { spawn, execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const { app } = require('electron')
const { resolveBundledDistPath, getBundledFrontendDisplayPath } = require('./backendProcess.cjs')
const {
  probeFrontendReady,
  waitForFrontendReady,
  getFrontendUrl,
} = require('./staticServerCore.cjs')
const { getPortOccupancy, clearPort } = require('./portUtils.cjs')

const FRONTEND_HOST = process.env.XXGKAMI_FRONTEND_HOST || '127.0.0.1'
const FRONTEND_PORT = Number.parseInt(process.env.XXGKAMI_FRONTEND_PORT || '5174', 10)
const FRONTEND_TERMINAL_TITLE = 'XXG-KAMI Frontend'
const FRONTEND_START_SCRIPT = 'start-frontend.bat'
const FRONTEND_LAUNCHER_VBS = 'open-frontend-terminal.vbs'
const STANDALONE_SERVE_SCRIPT = path.join('scripts', 'serve-frontend.cjs')
const RUNTIME_SERVE_SCRIPT = path.join('runtime', 'serve-frontend.cjs')

/** @type {import('child_process').ChildProcess | null} */
let frontendProcess = null
/** @type {boolean} */
let frontendLaunchedInTerminal = false
/** @type {number | null} */
let frontendNodePid = null
/** @type {string | null} */
let activeRootDir = null
/** @type {Promise<any> | null} */
let activeStartPromise = null

function getFrontendHost() {
  return FRONTEND_HOST
}

function getFrontendPort() {
  return FRONTEND_PORT
}

function getFrontendWorkDir() {
  const dir = path.join(app.getPath('userData'), 'frontend-runtime')
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

function resolveServeScriptPath() {
  if (app.isPackaged) {
    const packaged = path.join(process.resourcesPath, 'runtime', 'serve-frontend.cjs')
    if (fs.existsSync(packaged)) {
      return packaged
    }
  }
  return path.join(__dirname, '..', 'scripts', 'serve-frontend.cjs')
}

function resolveNodeRunner() {
  if (app.isPackaged) {
    return {
      executable: process.execPath,
      useElectronAsNode: true,
    }
  }
  return {
    executable: 'node',
    useElectronAsNode: false,
  }
}

function buildFrontendCommand(rootDir, host = FRONTEND_HOST, port = FRONTEND_PORT) {
  const scriptPath = resolveServeScriptPath()
  const rootArg = rootDir || resolveBundledDistPath() || 'dist'
  const runner = resolveNodeRunner()
  const scriptDisplay = app.isPackaged
    ? RUNTIME_SERVE_SCRIPT
    : STANDALONE_SERVE_SCRIPT
  const rootDisplay = getBundledFrontendDisplayPath(rootArg) || rootArg
  const args = `--host ${host} --port ${port} --root "${rootDisplay}"`

  if (runner.useElectronAsNode) {
    return `set ELECTRON_RUN_AS_NODE=1 && "${path.basename(process.execPath)}" "${scriptDisplay}" ${args}`
  }
  return `${runner.executable} ${scriptDisplay} ${args}`
}

function quoteBatchArg(arg) {
  return `"${String(arg).replace(/"/g, '""')}"`
}

function escapeVbsString(value) {
  return String(value).replace(/"/g, '""')
}

function writeFrontendLauncherVbs(cwd, scriptPath) {
  const vbsPath = path.join(cwd, FRONTEND_LAUNCHER_VBS)
  const content = [
    'Set WshShell = CreateObject("WScript.Shell")',
    `WshShell.CurrentDirectory = "${escapeVbsString(cwd)}"`,
    `WshShell.Run "cmd.exe /k ""${escapeVbsString(scriptPath)}""", 1, False`,
  ].join('\r\n')
  fs.writeFileSync(vbsPath, `${content}\r\n`, 'utf8')
  return vbsPath
}

function openWindowsTerminal(scriptPath, cwd, onLog) {
  onLog?.(`[前端] 打开终端脚本：${scriptPath}`)
  const vbsPath = writeFrontendLauncherVbs(cwd, scriptPath)
  const launcher = spawn('wscript.exe', ['//Nologo', vbsPath], {
    cwd,
    windowsHide: true,
    detached: true,
    stdio: 'ignore',
    env: process.env,
  })
  launcher.on('error', (err) => {
    onLog?.(`[前端] 无法打开 CMD 窗口：${err.message}`)
  })
  launcher.unref()
  return launcher
}

function buildFrontendBatchContent(cwd, runner, scriptPath, rootDir, host, port) {
  const url = getFrontendUrl(host, port)
  const serveCmd = runner.useElectronAsNode
    ? `set ELECTRON_RUN_AS_NODE=1\r\n${quoteBatchArg(runner.executable)} ${quoteBatchArg(scriptPath)} --host ${host} --port ${port} --root ${quoteBatchArg(rootDir)}`
    : `${quoteBatchArg(runner.executable)} ${quoteBatchArg(scriptPath)} --host ${host} --port ${port} --root ${quoteBatchArg(rootDir)}`

  return [
    '@echo off',
    'chcp 65001 >nul',
    `title ${FRONTEND_TERMINAL_TITLE}`,
    `cd /d ${quoteBatchArg(cwd)}`,
    'echo [XXG-KAMI] Frontend HTTP server starting...',
    `echo URL: ${url}`,
    `echo Root: ${quoteBatchArg(rootDir)}`,
    `if not exist ${quoteBatchArg(path.join(rootDir, 'index.html'))} (`,
    '  echo.',
    '  echo [ERROR] Frontend dist/index.html not found.',
    '  echo Please verify: resources\\runtime\\dist\\index.html',
    '  echo.',
    '  pause',
    '  exit /b 1',
    ')',
    'echo.',
    serveCmd,
    'echo.',
    'echo [XXG-KAMI] Frontend HTTP server exited.',
  ].join('\r\n')
}

function writeFrontendStartBatch(cwd, runner, scriptPath, rootDir, host, port) {
  const scriptPathOut = path.join(cwd, FRONTEND_START_SCRIPT)
  fs.writeFileSync(
    scriptPathOut,
    `${buildFrontendBatchContent(cwd, runner, scriptPath, rootDir, host, port)}\r\n`,
    'utf8'
  )
  return scriptPathOut
}

async function trackNodePidFromPort(maxWaitMs = 20000) {
  const deadline = Date.now() + maxWaitMs
  while (Date.now() < deadline) {
    const occupancy = await getPortOccupancy(FRONTEND_PORT)
    const listener = occupancy.processes[0]
    if (listener) {
      frontendNodePid = listener.pid
      return listener.pid
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  return null
}

function spawnFrontendInTerminal(scriptPath, rootDir, host, port, onLog) {
  frontendLaunchedInTerminal = true
  frontendProcess = null
  frontendNodePid = null

  const cwd = getFrontendWorkDir()
  const runner = resolveNodeRunner()

  if (!fs.existsSync(scriptPath)) {
    onLog?.(`[前端] 未找到启动脚本：${scriptPath}`)
  }
  if (!fs.existsSync(path.join(rootDir, 'index.html'))) {
    onLog?.(`[前端] 未找到 index.html：${path.join(rootDir, 'index.html')}`)
  }

  const batchPath = writeFrontendStartBatch(cwd, runner, scriptPath, rootDir, host, port)
  openWindowsTerminal(batchPath, cwd, onLog)
  onLog?.(`[前端] 已在独立 CMD 窗口启动 HTTP 服务（标题：${FRONTEND_TERMINAL_TITLE}）`)
  onLog?.('[前端] 访问日志请查看弹出的黑色 CMD 窗口')

  trackNodePidFromPort().then((pid) => {
    if (pid) {
      onLog?.(`[前端] 检测到监听进程 PID ${pid}（端口 ${FRONTEND_PORT}）`)
    }
  })
}

function spawnFrontendHidden(scriptPath, rootDir, host, port, onLog) {
  frontendLaunchedInTerminal = false
  frontendNodePid = null
  const runner = resolveNodeRunner()
  const args = [scriptPath, '--host', host, '--port', String(port), '--root', rootDir]
  const env = {
    ...process.env,
    XXGKAMI_FRONTEND_HOST: host,
    XXGKAMI_FRONTEND_PORT: String(port),
  }
  if (runner.useElectronAsNode) {
    env.ELECTRON_RUN_AS_NODE = '1'
  }

  frontendProcess = spawn(runner.executable, args, {
    cwd: getFrontendWorkDir(),
    windowsHide: true,
    detached: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env,
  })

  frontendProcess.stdout?.on('data', (chunk) => {
    onLog?.(`[frontend] ${chunk.toString().trimEnd()}`)
  })
  frontendProcess.stderr?.on('data', (chunk) => {
    onLog?.(`[frontend] ${chunk.toString().trimEnd()}`)
  })
  frontendProcess.on('exit', (code, signal) => {
    onLog?.(`[前端] 进程退出 code=${code ?? 'null'} signal=${signal ?? 'null'}`)
    frontendProcess = null
  })
  frontendProcess.unref()
}

async function ensureFrontendPortAvailable(options = {}) {
  const occupancy = await getPortOccupancy(FRONTEND_PORT)
  if (!occupancy.inUse) {
    return { ok: true, reused: false }
  }

  const ready = await probeFrontendReady(getFrontendUrl(FRONTEND_HOST, FRONTEND_PORT))
  if (ready && !options.forceRestart) {
    await trackNodePidFromPort(5000)
    return { ok: true, reused: true }
  }

  if (options.forceRestart) {
    const cleared = await clearPort(FRONTEND_PORT)
    if (!cleared.success) {
      return {
        ok: false,
        reused: false,
        message: cleared.message || `端口 ${FRONTEND_PORT} 仍被占用`,
      }
    }
    return { ok: true, reused: false }
  }

  return {
    ok: false,
    reused: false,
    message: `端口 ${FRONTEND_PORT} 已被占用，请先关闭其他前端 HTTP 服务后重试。`,
  }
}

async function startFrontendServer(options = {}) {
  if (activeStartPromise && !options.forceRestart) {
    return activeStartPromise
  }

  if (options.forceRestart) {
    stopFrontendServer()
  }

  activeStartPromise = (async () => {
    const rootDir = options.rootDir || resolveBundledDistPath()
    if (!rootDir) {
      throw new Error('未找到内置前端 dist/index.html')
    }

    const host = options.host || FRONTEND_HOST
    const port = options.port || FRONTEND_PORT
    const command = buildFrontendCommand(rootDir, host, port)
    options.onLog?.(`[前端] ${command}`)

    const portResult = await ensureFrontendPortAvailable(options)
    if (!portResult.ok) {
      throw new Error(portResult.message || `端口 ${port} 不可用`)
    }

    if (portResult.reused) {
      activeRootDir = rootDir
      options.onLog?.(`[前端] 端口 ${port} 已有 HTTP 服务在运行，未重新启动`)
      return getFrontendStatus()
    }

    const scriptPath = resolveServeScriptPath()
    options.onLog?.(`[前端] 正在启动 HTTP 服务 ${getFrontendUrl(host, port)} …`)

    if (process.platform === 'win32') {
      spawnFrontendInTerminal(scriptPath, rootDir, host, port, options.onLog)
    } else {
      spawnFrontendHidden(scriptPath, rootDir, host, port, options.onLog)
    }

    activeRootDir = rootDir
    await trackNodePidFromPort(20000)

    const ready = await waitForFrontendReady(getFrontendUrl(host, port))
    if (!ready) {
      stopFrontendServer()
      throw new Error(
        frontendLaunchedInTerminal
          ? `前端 HTTP 服务启动超时，请查看「${FRONTEND_TERMINAL_TITLE}」终端窗口中的日志。`
          : `前端 HTTP 服务启动超时: ${getFrontendUrl(host, port)}`
      )
    }

    options.onLog?.(`[前端] HTTP 服务已就绪: ${getFrontendUrl(host, port)}`)
    return getFrontendStatus()
  })()

  try {
    return await activeStartPromise
  } finally {
    activeStartPromise = null
  }
}

function stopFrontendServer() {
  if (frontendLaunchedInTerminal) {
    try {
      if (frontendNodePid) {
        execFileSync('cmd.exe', ['/d', '/s', '/c', `taskkill /PID ${frontendNodePid} /F /T`], {
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
            `powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort ${FRONTEND_PORT} -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"`,
          ],
          { windowsHide: true, timeout: 15000 }
        )
      }
    } catch {
      // ignore
    }
  } else if (frontendProcess && frontendProcess.exitCode == null) {
    try {
      frontendProcess.kill('SIGTERM')
    } catch {
      try {
        frontendProcess.kill()
      } catch {
        // ignore
      }
    }
  }

  frontendProcess = null
  frontendLaunchedInTerminal = false
  frontendNodePid = null
  activeRootDir = null
}

function getFrontendStatus() {
  const rootDir = activeRootDir || resolveBundledDistPath()
  const host = FRONTEND_HOST
  const port = FRONTEND_PORT
  const running = frontendLaunchedInTerminal
    ? !!frontendNodePid
    : !!(frontendProcess && frontendProcess.exitCode == null)

  return {
    running,
    host,
    port,
    url: getFrontendUrl(host, port),
    command: buildFrontendCommand(rootDir, host, port),
    rootDir,
    rootDisplayPath: getBundledFrontendDisplayPath(rootDir),
    ready: !!(rootDir && fs.existsSync(path.join(rootDir, 'index.html'))),
    frontendTerminalTitle: frontendLaunchedInTerminal ? FRONTEND_TERMINAL_TITLE : null,
    frontendNodePid,
    launchedInTerminal: frontendLaunchedInTerminal,
  }
}

module.exports = {
  FRONTEND_HOST,
  FRONTEND_PORT,
  FRONTEND_TERMINAL_TITLE,
  STANDALONE_SERVE_SCRIPT,
  getFrontendHost,
  getFrontendPort,
  getFrontendUrl: (host = FRONTEND_HOST, port = FRONTEND_PORT) => getFrontendUrl(host, port),
  buildFrontendCommand,
  probeFrontendReady,
  waitForFrontendReady,
  startFrontendServer,
  stopFrontendServer,
  getFrontendStatus,
}
