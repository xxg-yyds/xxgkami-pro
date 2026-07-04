const fs = require('fs')
const path = require('path')

const root = path.join(__dirname, '..')
const jarPath = path.join(root, 'backend/target/backend-0.0.1-SNAPSHOT.jar')
const distIndex = path.join(root, 'dist/index.html')

let failed = false

if (!fs.existsSync(jarPath)) {
  console.error('[electron-bundle] 缺少后端 JAR: backend/target/backend-0.0.1-SNAPSHOT.jar')
  console.error('[electron-bundle] 请先执行: cd backend && mvn -DskipTests package')
  failed = true
}

if (!fs.existsSync(distIndex)) {
  console.error('[electron-bundle] 缺少前端 dist/index.html')
  console.error('[electron-bundle] 请先执行: vite build --mode electron')
  failed = true
}

if (failed) {
  process.exit(1)
}

console.log('[electron-bundle] 前端 dist 与后端 JAR 已就绪，开始 electron-builder…')
