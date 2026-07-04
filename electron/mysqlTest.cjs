const fs = require('fs')
const os = require('os')
const path = require('path')
const net = require('net')
const { execFile } = require('child_process')
const { promisify } = require('util')

const execFileAsync = promisify(execFile)

async function runShell(command) {
  try {
    const { stdout } = await execFileAsync('cmd.exe', ['/d', '/s', '/c', command], {
      windowsHide: true,
      timeout: 15000,
      maxBuffer: 1024 * 1024,
    })
    return String(stdout || '').trim()
  } catch {
    return ''
  }
}

function firstLine(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find(Boolean) || ''
}

function escapeCnfValue(value) {
  return String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
}

function probeTcp(host, port, timeoutMs = 4000) {
  return new Promise((resolve) => {
    const socket = new net.Socket()
    const done = (ok) => {
      socket.destroy()
      resolve(ok)
    }
    socket.setTimeout(timeoutMs)
    socket.once('connect', () => done(true))
    socket.once('timeout', () => done(false))
    socket.once('error', () => done(false))
    socket.connect(port, host)
  })
}

async function findMysqlCli() {
  const stdout = await runShell('where mysql')
  return firstLine(stdout)
}

function parseMysqlCliError(error) {
  const text = [error?.stderr, error?.stdout, error?.message].filter(Boolean).join('\n')
  if (/Access denied/i.test(text)) {
    return '连接被拒绝：用户名或密码不正确'
  }
  if (/Can't connect|Connection refused|timed out/i.test(text)) {
    return '无法连接 MySQL 服务，请确认服务已启动且主机/端口正确'
  }
  if (/Unknown database/i.test(text)) {
    return '连接成功，但指定数据库不存在'
  }
  const line = firstLine(text)
  return line || 'MySQL 连接测试失败'
}

async function runMysqlQuery(mysqlPath, cnfPath, sql) {
  const { stdout, stderr } = await execFileAsync(
    mysqlPath,
    [`--defaults-extra-file=${cnfPath}`, '-N', '-B', '-e', sql],
    { windowsHide: true, timeout: 20000, maxBuffer: 1024 * 1024 }
  )
  if (stderr && /ERROR/i.test(stderr)) {
    throw new Error(stderr.trim())
  }
  return String(stdout || '').trim()
}

async function testMysqlConnection(config = {}) {
  const host = String(config.host || 'localhost').trim() || 'localhost'
  const port = Number(config.port) || 3306
  const username = String(config.username || 'root').trim() || 'root'
  const password = config.password ?? ''
  const database = String(config.database || 'kami').trim() || 'kami'

  const tcpOk = await probeTcp(host, port)
  if (!tcpOk) {
    return {
      ok: false,
      tcpOk: false,
      message: `无法连接 ${host}:${port}，请确认 MySQL 服务已启动`,
    }
  }

  const mysqlPath = await findMysqlCli()
  if (!mysqlPath) {
    return {
      ok: false,
      tcpOk: true,
      message: 'MySQL 端口可连通，但未找到 mysql 命令行工具，无法验证账号密码',
      hint: '请安装 MySQL 并将 mysql.exe 加入 PATH，或确认服务正常运行后尝试直接启动',
    }
  }

  const cnfPath = path.join(os.tmpdir(), `xxgkami-mysql-test-${Date.now()}.cnf`)
  const cnfContent = `[client]
host=${host}
port=${port}
user=${username}
password="${escapeCnfValue(password)}"
`

  try {
    fs.writeFileSync(cnfPath, cnfContent, { encoding: 'utf8', mode: 0o600 })

    const version = await runMysqlQuery(mysqlPath, cnfPath, 'SELECT VERSION();')
    const dbCountRaw = await runMysqlQuery(
      mysqlPath,
      cnfPath,
      `SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${database.replace(/'/g, "''")}';`
    )
    const dbExists = Number(dbCountRaw) > 0

    return {
      ok: true,
      tcpOk: true,
      version,
      database,
      dbExists,
      message: dbExists
        ? `连接成功 · MySQL ${version} · 数据库 ${database} 已存在`
        : `连接成功 · MySQL ${version} · 数据库 ${database} 不存在，启动后需执行安装向导建库`,
    }
  } catch (error) {
    return {
      ok: false,
      tcpOk: true,
      message: parseMysqlCliError(error),
    }
  } finally {
    try {
      fs.unlinkSync(cnfPath)
    } catch {
      // ignore
    }
  }
}

module.exports = {
  testMysqlConnection,
}
