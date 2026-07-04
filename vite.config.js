import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import JavaScriptObfuscator from 'javascript-obfuscator'

const FEEDBACK_SUPPORTERS_FILE = '/src/data/feedbackSupporters.js'

/** 生产构建时混淆共创使用者名单模块（Vite 使用 Rollup，等价于 webpack-obfuscator 能力） */
function obfuscateFeedbackSupportersPlugin() {
  return {
    name: 'obfuscate-feedback-supporters',
    apply: 'build',
    enforce: 'post',
    transform(code, id) {
      const normalizedId = id.replace(/\\/g, '/')
      if (!normalizedId.includes(FEEDBACK_SUPPORTERS_FILE)) {
        return null
      }
      const result = JavaScriptObfuscator.obfuscate(code, {
        compact: true,
        controlFlowFlattening: true,
        controlFlowFlatteningThreshold: 0.5,
        deadCodeInjection: false,
        identifierNamesGenerator: 'hexadecimal',
        renameGlobals: false,
        selfDefending: false,
        stringArray: true,
        stringArrayEncoding: ['base64'],
        stringArrayThreshold: 0.75,
        transformObjectKeys: true,
        unicodeEscapeSequence: false
      })
      return {
        code: result.getObfuscatedCode(),
        map: null
      }
    }
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const isElectron = mode === 'electron'

  return {
    plugins: [vue(), obfuscateFeedbackSupportersPlugin()],
    base: isElectron ? './' : '/',
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
    server: {
      port: 5173,
      // 固定 IPv4，避免 Windows 下 localhost 走 IPv6 导致 wait-on / Electron 连不上
      host: '127.0.0.1',
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          secure: false
        }
      }
    }
  }
})
