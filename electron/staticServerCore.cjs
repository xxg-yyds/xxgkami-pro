const http = require('http')
const fs = require('fs')
const path = require('path')
const { URL } = require('url')

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
}

function resolveSafeFile(rootDir, requestPathname) {
  const decoded = decodeURIComponent(requestPathname || '/')
  const relativePath = decoded === '/' ? 'index.html' : decoded.replace(/^\/+/, '')
  const normalizedRoot = path.resolve(rootDir)
  const candidate = path.resolve(normalizedRoot, relativePath)
  const relative = path.relative(normalizedRoot, candidate)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    return null
  }
  return candidate
}

function sendFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase()
  const contentType = MIME_TYPES[ext] || 'application/octet-stream'
  fs.readFile(filePath, (error, data) => {
    if (error) {
      res.writeHead(error.code === 'ENOENT' ? 404 : 500)
      res.end(error.code === 'ENOENT' ? 'Not Found' : 'Internal Server Error')
      return
    }
    res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': 'no-cache' })
    res.end(data)
  })
}

function createStaticFileServer({ rootDir, host, port, onListening }) {
  if (!rootDir || !fs.existsSync(path.join(rootDir, 'index.html'))) {
    throw new Error(`未找到前端目录或 index.html: ${rootDir || '(empty)'}`)
  }

  const server = http.createServer((req, res) => {
    const requestUrl = new URL(req.url || '/', `http://${req.headers.host || '127.0.0.1'}`)
    let filePath = resolveSafeFile(rootDir, requestUrl.pathname)
    if (!filePath) {
      res.writeHead(403)
      res.end('Forbidden')
      return
    }

    fs.stat(filePath, (error, stats) => {
      if (error) {
        if (error.code === 'ENOENT') {
          sendFile(res, path.join(rootDir, 'index.html'))
          return
        }
        res.writeHead(500)
        res.end('Internal Server Error')
        return
      }

      if (stats.isDirectory()) {
        sendFile(res, path.join(filePath, 'index.html'))
        return
      }

      sendFile(res, filePath)
    })
  })

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, () => {
      server.removeListener('error', reject)
      onListening?.()
      resolve(server)
    })
  })
}

function probeFrontendReady(url, timeoutMs = 5000) {
  return new Promise((resolve) => {
    const request = http.get(url, (response) => {
      response.resume()
      resolve(response.statusCode >= 200 && response.statusCode < 400)
    })
    request.on('error', () => resolve(false))
    request.setTimeout(timeoutMs, () => {
      request.destroy()
      resolve(false)
    })
  })
}

async function waitForFrontendReady(url, timeoutMs = 15000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if (await probeFrontendReady(url, 2000)) {
      return true
    }
    await new Promise((resolve) => setTimeout(resolve, 300))
  }
  return false
}

function getFrontendUrl(host, port) {
  return `http://${host}:${port}/`
}

module.exports = {
  createStaticFileServer,
  probeFrontendReady,
  waitForFrontendReady,
  getFrontendUrl,
}
