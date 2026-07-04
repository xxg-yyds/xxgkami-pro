const {
  startBackendProcess,
  resolveJarPath,
  resolveBundledDistPath,
  buildJavaCommand,
  saveMysqlConfig,
  loadMysqlConfig,
  getHealthCheckUrl,
  getBundledFrontendDisplayPath,
  getBundledJarDisplayPath,
} = require('./backendProcess.cjs')
const {
  startFrontendServer,
  getFrontendStatus,
  buildFrontendCommand,
} = require('./frontendServer.cjs')

function sendProgress(webContents, payload) {
  if (webContents && !webContents.isDestroyed()) {
    webContents.send('startup-progress', payload)
  }
}

async function launchApplication(webContents, options = {}) {
  const mysqlConfig = options.mysqlConfig || loadMysqlConfig()
  if (options.mysqlConfig) {
    saveMysqlConfig(options.mysqlConfig)
  }

  const steps = [
    { id: 'java', label: '启动 Java 后端 (java -jar)', status: 'pending' },
    { id: 'health', label: '等待 API 服务就绪', status: 'pending' },
    { id: 'frontend', label: '启动前端 HTTP 服务 (HTML)', status: 'pending' },
  ]

  const pushSteps = () => {
    sendProgress(webContents, { type: 'steps', steps: steps.map((s) => ({ ...s })) })
  }

  pushSteps()

  const jarPath = resolveJarPath()
  const command = buildJavaCommand(jarPath || '', mysqlConfig)
  const logs = []
  const onLog = (line) => {
    logs.push(line)
    sendProgress(webContents, { type: 'log', line })
  }

  // Step 1: Java backend
  steps[0].status = 'running'
  steps[0].detail =
    process.platform === 'win32'
      ? '检测 8080 端口并在独立终端启动 Java 后端…'
      : '检测 8080 端口并启动 Java 后端…'
  steps[0].command = command
  pushSteps()

  if (mysqlConfig?.username) {
    onLog(`[配置] MySQL ${mysqlConfig.username}@${mysqlConfig.host || 'localhost'}:${mysqlConfig.port || 3306}/${mysqlConfig.database || 'kami'}`)
  } else {
    onLog('[配置] 使用 JAR 内置数据库配置（若连接失败请填写 MySQL 账号）')
  }

  const backendResult = await startBackendProcess({
    onLog,
    mysqlConfig,
    forceRestart: !!options.forceRestart,
  })

  if (!backendResult.started) {
    steps[0].status = 'error'
    steps[0].detail = backendResult.message || 'Java 进程启动失败'
    steps[1].status = 'error'
    steps[2].status = 'pending'
    pushSteps()
    return {
      success: false,
      message: backendResult.message || '后端启动失败',
      command: backendResult.command || command,
      steps,
      logs: backendResult.logs?.length ? backendResult.logs : logs,
      exitCode: backendResult.exitCode,
      portInUse: !!backendResult.portInUse,
      portInfo: backendResult.portInfo || null,
    }
  }

  steps[0].status = 'done'
  steps[0].detail = getBundledJarDisplayPath(backendResult.jarPath || jarPath)
  steps[0].command = backendResult.command || command
  pushSteps()

  // Step 2: Health (waited inside startBackendProcess)
  steps[1].status = 'running'
  steps[1].detail = `检测 ${getHealthCheckUrl()} …`
  pushSteps()

  steps[1].status = 'done'
  steps[1].detail = backendResult.healthUrl
    ? `API 已就绪 (${backendResult.healthUrl})`
    : 'API 已就绪'
  pushSteps()

  // Step 3: Frontend HTTP server — only after backend is ready
  const distPath = resolveBundledDistPath()
  const frontendCommand = buildFrontendCommand(distPath)
  steps[2].status = 'running'
  steps[2].detail = '正在启动前端 HTTP 静态服务…'
  steps[2].command = frontendCommand
  pushSteps()

  if (!distPath) {
    steps[2].status = 'error'
    steps[2].detail = '未找到内置前端 dist/index.html'
    pushSteps()
    return {
      success: false,
      message: '未找到内置前端 HTML 资源，请重新打包桌面版。',
      steps,
      logs: backendResult.logs?.length ? backendResult.logs : logs,
    }
  }

  try {
    const frontendResult = await startFrontendServer({ onLog })
    steps[2].status = 'done'
    steps[2].detail = `${frontendResult.url} · 终端「XXG-KAMI Frontend」 (${getBundledFrontendDisplayPath(distPath)})`
    steps[2].command = frontendResult.command || frontendCommand
    pushSteps()
  } catch (error) {
    steps[2].status = 'error'
    steps[2].detail = error.message || '前端 HTTP 服务启动失败'
    steps[2].command = frontendCommand
    pushSteps()
    return {
      success: false,
      message: error.message || '前端 HTTP 服务启动失败',
      steps,
      logs: backendResult.logs?.length ? backendResult.logs : logs,
    }
  }

  sendProgress(webContents, { type: 'complete', success: true })

  const frontendStatus = getFrontendStatus()

  return {
    success: true,
    message: '前后端已就绪，正在进入主界面',
    command: backendResult.command || command,
    frontendUrl: frontendStatus.url,
    frontendCommand: frontendStatus.command,
    steps,
    logs: backendResult.logs?.length ? backendResult.logs : logs,
  }
}

module.exports = {
  launchApplication,
  saveMysqlConfig,
  loadMysqlConfig,
}
