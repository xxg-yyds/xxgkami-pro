#!/usr/bin/env node
/**
 * 独立启动前端静态 HTTP 服务（与 Electron 内置逻辑一致）
 * 示例:
 *   node scripts/serve-frontend.cjs --host 127.0.0.1 --port 5174 --root dist
 */
const path = require('path')
const fs = require('fs')
const corePath = fs.existsSync(path.join(__dirname, 'staticServerCore.cjs'))
  ? path.join(__dirname, 'staticServerCore.cjs')
  : path.join(__dirname, '../electron/staticServerCore.cjs')
const { createStaticFileServer, getFrontendUrl } = require(corePath)

function parseArgs(argv) {
  const options = {
    host: process.env.XXGKAMI_FRONTEND_HOST || '127.0.0.1',
    port: Number.parseInt(process.env.XXGKAMI_FRONTEND_PORT || '5174', 10),
    root: 'dist',
  }

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--host' && argv[i + 1]) {
      options.host = argv[++i]
    } else if (arg === '--port' && argv[i + 1]) {
      options.port = Number.parseInt(argv[++i], 10)
    } else if (arg === '--root' && argv[i + 1]) {
      options.root = argv[++i]
    } else if (arg === '--help' || arg === '-h') {
      options.help = true
    }
  }

  return options
}

function printHelp() {
  console.log(`用法:
  node scripts/serve-frontend.cjs [--host 127.0.0.1] [--port 5174] [--root dist]

环境变量:
  XXGKAMI_FRONTEND_HOST  默认 127.0.0.1
  XXGKAMI_FRONTEND_PORT  默认 5174
`)
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  if (options.help) {
    printHelp()
    return
  }

  const rootDir = path.isAbsolute(options.root)
    ? options.root
    : path.resolve(process.cwd(), options.root)
  if (!fs.existsSync(path.join(rootDir, 'index.html'))) {
    console.error(`[serve-frontend] 未找到 ${path.join(rootDir, 'index.html')}`)
    process.exit(1)
  }

  const url = getFrontendUrl(options.host, options.port)
  console.log(`[serve-frontend] root=${rootDir}`)
  console.log(`[serve-frontend] listening ${url}`)

  await createStaticFileServer({
    rootDir,
    host: options.host,
    port: options.port,
    onListening: () => {
      console.log(`[serve-frontend] ready: ${url}`)
    },
  })
}

main().catch((error) => {
  console.error('[serve-frontend] failed:', error.message || error)
  process.exit(1)
})
