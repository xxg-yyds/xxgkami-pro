const { execFile } = require('child_process')
const { promisify } = require('util')

const execFileAsync = promisify(execFile)

const BACKEND_PORT = 8080

function parseImageNameFromTasklistList(output) {
  const text = String(output || '')
  const imageMatch = text.match(/(?:Image Name|映像名称)\s*:\s*(.+)/i)
  if (imageMatch) return imageMatch[1].trim()

  const exeMatch = text.match(/^\s*[^:]+:\s*(\S+\.exe)\s*$/im)
  if (exeMatch) return exeMatch[1].trim()

  return '未知进程'
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function parseLocalPort8080ListeningLine(line, port = BACKEND_PORT) {
  if (!/LISTENING/i.test(line)) return null

  const parts = line.trim().split(/\s+/)
  if (parts.length < 5) return null

  const localAddress = parts[1]
  const pid = parseInt(parts[parts.length - 1], 10)
  const portSuffix = `:${port}`

  if (!localAddress.endsWith(portSuffix)) return null
  if (!Number.isFinite(pid) || pid <= 0) return null

  return { pid, localAddress }
}

async function runNetstatFindPort(port = BACKEND_PORT) {
  try {
    const { stdout, stderr } = await execFileAsync(
      'cmd.exe',
      ['/d', '/s', '/c', `netstat -ano | findstr :${port}`],
      {
        windowsHide: true,
        timeout: 20000,
        maxBuffer: 1024 * 1024,
      }
    )
    return String(stdout || stderr || '').trim()
  } catch (error) {
    return String(error.stdout || error.stderr || error.message || '').trim()
  }
}

async function getProcessInfoByList(pid) {
  try {
    const { stdout, stderr } = await execFileAsync(
      'tasklist.exe',
      ['/FI', `PID eq ${pid}`, '/FO', 'LIST'],
      {
        windowsHide: true,
        timeout: 15000,
        maxBuffer: 1024 * 1024,
      }
    )
    const output = String(stdout || stderr || '').trim()
    if (!output || /No tasks are running/i.test(output)) {
      return { pid, name: '未知进程', exists: false, detail: output }
    }

    const nameMatch = output.match(/(?:Image Name|映像名称)\s*:\s*(.+)/i)
    const parsedName = nameMatch ? nameMatch[1].trim() : parseImageNameFromTasklistList(output)
    return {
      pid,
      name: parsedName,
      exists: true,
      detail: output,
    }
  } catch (error) {
    const output = String(error.stdout || error.stderr || error.message || '').trim()
    return { pid, name: '未知进程', exists: false, detail: output }
  }
}

async function getPortOccupancyByNetstat(port = BACKEND_PORT) {
  const output = await runNetstatFindPort(port)
  const pidMap = new Map()

  for (const line of output.split(/\r?\n/)) {
    const entry = parseLocalPort8080ListeningLine(line, port)
    if (!entry) continue
    if (!pidMap.has(entry.pid)) {
      pidMap.set(entry.pid, entry.localAddress)
    }
  }

  const processes = []
  for (const [pid, localAddress] of pidMap.entries()) {
    const info = await getProcessInfoByList(pid)
    processes.push({
      pid,
      name: info.name,
      localAddress,
      tasklist: info.detail,
    })
  }

  return processes
}

async function getPortOccupancy(port = BACKEND_PORT) {
  if (process.platform !== 'win32') {
    return { inUse: false, port, processes: [] }
  }

  const processes = await getPortOccupancyByNetstat(port)
  return {
    inUse: processes.length > 0,
    port,
    processes,
  }
}

async function killProcess(pid) {
  const info = await getProcessInfoByList(pid)
  await execFileAsync('taskkill.exe', ['/PID', String(pid), '/F', '/T'], {
    windowsHide: true,
    timeout: 15000,
  })
  return {
    pid,
    name: info.name,
    tasklist: info.detail,
  }
}

async function killProcessesOnPort(port = BACKEND_PORT) {
  const occupancy = await getPortOccupancy(port)
  const killed = []
  const failed = []

  for (const proc of occupancy.processes) {
    try {
      const result = await killProcess(proc.pid)
      killed.push(result)
    } catch (error) {
      failed.push({
        ...proc,
        error: error.message || '结束进程失败',
      })
    }
  }

  return { killed, failed, occupancy }
}

async function waitForPortFree(port = BACKEND_PORT, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const occupancy = await getPortOccupancy(port)
    if (!occupancy.inUse) {
      return { ok: true, waitedMs: timeoutMs - (deadline - Date.now()) }
    }
    await sleep(400)
  }

  const remaining = await getPortOccupancy(port)
  return {
    ok: !remaining.inUse,
    remaining: remaining.processes,
  }
}

async function clearPort(port = BACKEND_PORT) {
  let occupancy = await getPortOccupancy(port)
  const killed = []
  const failed = []

  if (!occupancy.inUse) {
    return {
      success: true,
      port,
      killed,
      failed,
      message: `端口 ${port} 当前未被占用`,
    }
  }

  for (let round = 0; round < 3 && occupancy.inUse; round += 1) {
    for (const proc of occupancy.processes) {
      if (killed.some((item) => item.pid === proc.pid)) continue
      try {
        const result = await killProcess(proc.pid)
        killed.push(result)
      } catch (error) {
        const exists = failed.find((item) => item.pid === proc.pid)
        if (!exists) {
          failed.push({
            ...proc,
            error: error.message || '结束进程失败',
          })
        }
      }
    }
    await sleep(800)
    occupancy = await getPortOccupancy(port)
  }

  const waitResult = await waitForPortFree(port, 10000)
  if (!waitResult.ok) {
    const after = await getPortOccupancy(port)
    return {
      success: false,
      port,
      killed,
      failed,
      remaining: after.processes,
      message: `端口 ${port} 仍被占用（可能由终端手动启动的 java 占用）。请关闭其他终端里的后端进程后重试。`,
    }
  }

  return {
    success: true,
    port,
    killed,
    failed,
    message:
      killed.length > 0
        ? `已结束占用端口 ${port} 的进程：${killed.map((p) => `${p.name}(PID ${p.pid})`).join('、')}`
        : `端口 ${port} 已释放`,
  }
}

async function isProcessRunning(pid) {
  if (!pid || !Number.isFinite(pid)) return false
  const info = await getProcessInfoByList(pid)
  return info.exists
}

async function getJavaListenerOnPort(port = BACKEND_PORT) {
  const occupancy = await getPortOccupancy(port)
  return occupancy.processes.find((proc) => /^java(\.exe)?$/i.test(proc.name)) || null
}

function isPort8080Conflict(text) {
  return /Port 8080 was already in use|Address already in use|BindException.*8080|Web server failed to start\. Port 8080/i.test(
    String(text || '')
  )
}

module.exports = {
  BACKEND_PORT,
  getPortOccupancy,
  clearPort,
  waitForPortFree,
  isPort8080Conflict,
  isProcessRunning,
  getJavaListenerOnPort,
  getProcessInfoByList,
  killProcess,
  killProcessesOnPort,
}
