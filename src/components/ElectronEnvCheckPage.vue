<template>
  <div class="electron-env-check">
    <div class="env-check-bg" :style="logoMaskVars" aria-hidden="true">
      <div class="env-check-bg-shape env-check-bg-shape--glow"></div>
      <div class="env-check-bg-shape env-check-bg-shape--main"></div>
    </div>

    <div class="env-check-card">
      <header class="env-check-header">
        <img :src="brandLogo" class="env-check-logo" alt="" width="56" height="56" />
        <h1>桌面版启动准备</h1>
        <p>程序已内置前端 HTML 与后端 JAR，无需从 Git 或网络下载。请先检测 Java 与 MySQL，通过后点击「下一步」启动服务。前端会在「XXG-KAMI Frontend」终端中运行，可通过 <code>http://127.0.0.1:5174</code> 访问，便于云桌面端口映射。</p>
      </header>

      <div v-if="checking" class="env-check-loading">
        <span class="env-check-spinner" aria-hidden="true"></span>
        <p>正在检测系统环境…</p>
      </div>

      <template v-else>
      <h2 v-if="envItems.length" class="env-section-title env-deps-title">运行环境（需本机安装）</h2>
      <ul v-if="envItems.length" class="env-check-list">
        <li
          v-for="item in envItems"
          :key="item.key"
          class="env-check-item"
          :class="item.installed ? 'is-ok' : 'is-missing'"
        >
          <div class="env-check-item-icon" aria-hidden="true">
            <span v-if="item.installed">✓</span>
            <span v-else>!</span>
          </div>
          <div class="env-check-item-body">
            <div class="env-check-item-title">
              <strong>{{ item.label }}</strong>
              <span class="env-check-badge" :class="item.installed ? 'ok' : 'warn'">
                {{ item.statusLabel || (item.installed ? '已就绪' : '未检测到') }}
              </span>
            </div>
            <p v-if="item.version" class="env-check-version">版本：{{ item.version }}</p>
            <p v-if="item.path" class="env-check-path">资源位置：{{ item.path }}<span v-if="item.isBundled">（相对安装目录）</span></p>
            <p class="env-check-hint">{{ item.hint }}</p>

            <div v-if="item.needsInstall && item.installer" class="env-check-installer">
              <p class="env-check-installer-title">
                推荐安装包：<strong>{{ item.installer.label }}</strong>
                <span class="env-check-installer-file">{{ item.installer.fileName }}</span>
              </p>

              <div v-if="downloadState[item.key]?.status === 'downloading' || downloadState[item.key]?.status === 'starting'" class="env-check-progress-wrap">
                <div class="env-check-progress-track">
                  <div
                    class="env-check-progress-bar"
                    :class="{ indeterminate: downloadState[item.key]?.percent == null }"
                    :style="downloadState[item.key]?.percent != null ? { width: downloadState[item.key].percent + '%' } : undefined"
                  ></div>
                </div>
                <p class="env-check-progress-text">
                  {{ formatDownloadProgress(item.key) }}
                </p>
              </div>

              <p v-else-if="downloadState[item.key]?.status === 'done'" class="env-check-download-done">
                已保存至：{{ downloadState[item.key].savePath }}
              </p>

              <p v-else-if="downloadState[item.key]?.status === 'error'" class="env-check-download-error">
                下载失败：{{ downloadState[item.key].message }}
              </p>

              <div class="env-check-installer-actions">
                <button
                  type="button"
                  class="btn-download"
                  :disabled="isDownloading(item.key)"
                  @click="startDownload(item.key)"
                >
                  {{ isDownloading(item.key) ? '下载中…' : '下载安装包' }}
                </button>
                <button
                  v-if="isDownloading(item.key)"
                  type="button"
                  class="btn-cancel-download"
                  @click="cancelDownload(item.key)"
                >
                  取消
                </button>
              </div>

              <div
                v-if="item.installer.installGuide"
                class="env-check-guide"
                :class="{ 'is-post-download': downloadState[item.key]?.status === 'done' }"
              >
                <p class="env-check-guide-title">安装说明</p>
                <p class="env-check-guide-steps">{{ item.installer.installGuide.steps }}</p>
                <button
                  v-if="item.installer.installGuide.tutorialUrl"
                  type="button"
                  class="env-check-guide-link"
                  @click="openTutorial(item.installer.installGuide.tutorialUrl)"
                >
                  {{ item.installer.installGuide.tutorialLabel }}
                </button>
                <p v-else-if="item.installer.installGuide.type === 'auto'" class="env-check-guide-auto">
                  安装方式：双击 MSI 按提示完成即可
                </p>
              </div>
            </div>
          </div>
        </li>
      </ul>

      <h2 v-if="bundledItems.length" class="env-section-title env-deps-title">内置程序资源</h2>
      <ul v-if="bundledItems.length" class="env-check-list env-bundled-list">
        <li
          v-for="item in bundledItems"
          :key="item.key"
          class="env-check-item"
          :class="item.installed ? 'is-ok' : 'is-missing'"
        >
          <div class="env-check-item-icon" aria-hidden="true">
            <span v-if="item.installed">✓</span>
            <span v-else>!</span>
          </div>
          <div class="env-check-item-body">
            <div class="env-check-item-title">
              <strong>{{ item.label }}</strong>
              <span class="env-check-badge" :class="item.installed ? 'ok' : 'warn'">
                {{ item.statusLabel }}
              </span>
            </div>
            <p v-if="item.path" class="env-check-path">资源位置：{{ item.path }}<span v-if="item.isBundled">（相对安装目录）</span></p>
            <p class="env-check-hint">{{ item.hint }}</p>
          </div>
        </li>
      </ul>

      <section v-if="!checking && allInstalled" class="env-mysql-config">
        <h2 class="env-section-title">MySQL 连接配置（启动后端时使用）</h2>
        <p class="env-mysql-hint">若 JAR 内置密码与本机 MySQL 不一致，请在此填写正确账号，点击「下一步」时会传给 java -jar。</p>
        <div class="env-mysql-grid">
          <label>
            <span>主机</span>
            <input v-model="mysqlConfig.host" type="text" placeholder="localhost" />
          </label>
          <label>
            <span>端口</span>
            <input v-model.number="mysqlConfig.port" type="number" placeholder="3306" />
          </label>
          <label>
            <span>用户名</span>
            <input v-model="mysqlConfig.username" type="text" placeholder="root" />
          </label>
          <label>
            <span>密码</span>
            <input
              v-model="mysqlConfig.password"
              type="password"
              placeholder="MySQL 密码"
              @blur="onMysqlPasswordBlur"
            />
          </label>
          <label class="env-mysql-db">
            <span>数据库</span>
            <input v-model="mysqlConfig.database" type="text" placeholder="kami" />
          </label>
        </div>

        <div class="env-mysql-test-row">
          <button
            type="button"
            class="btn-mysql-test"
            :disabled="mysqlTesting || !canTestMysql"
            @click="testMysqlConnection"
          >
            {{ mysqlTesting ? '检测中…' : '检测连接状态' }}
          </button>
          <p
            v-if="mysqlTestStatus !== 'idle'"
            class="env-mysql-test-result"
            :class="{
              ok: mysqlTestStatus === 'ok',
              warn: mysqlTestStatus === 'warn',
              error: mysqlTestStatus === 'error',
            }"
          >
            {{ mysqlTestMessage }}
          </p>
        </div>
      </section>

      <p v-if="startError && !showStartupModal" class="env-check-start-error">{{ startError }}</p>

      <p v-if="summaryMessage && !checking && !showStartupModal" class="env-check-summary" :class="{ ok: allInstalled }">
        {{ summaryMessage }}
      </p>

      <div class="env-check-actions">
        <button type="button" class="btn-secondary" :disabled="checking || anyDownloading || starting" @click="runCheck">
          重新检测
        </button>
        <button
          type="button"
          class="btn-primary"
          :disabled="checking || anyDownloading || starting || !allInstalled"
          @click="goNext"
        >
          {{ starting ? '启动中…' : '下一步' }}
        </button>
      </div>
      </template>
    </div>

    <div v-if="showStartupModal" class="startup-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="startup-modal-title">
      <div class="startup-modal">
        <header class="startup-modal-header">
          <h2 id="startup-modal-title">正在启动程序</h2>
          <p>按顺序启动 Java 后端与前端 HTTP 静态服务</p>
        </header>

        <ol class="startup-steps">
          <li
            v-for="step in startupSteps"
            :key="step.id"
            class="startup-step"
            :class="'is-' + (step.status || 'pending')"
          >
            <div class="startup-step-head">
              <span class="startup-step-icon">{{ stepStatusIcon(step.status) }}</span>
              <strong>{{ step.label }}</strong>
              <span class="startup-step-badge">{{ stepStatusLabel(step.status) }}</span>
            </div>
            <p v-if="step.command" class="startup-step-command">{{ step.command }}</p>
            <p v-if="step.detail" class="startup-step-detail">{{ step.detail }}</p>
          </li>
        </ol>

        <div v-if="startupLogs.length" class="startup-log-wrap">
          <p class="startup-log-title">启动日志</p>
          <pre class="startup-log">{{ startupLogs.join('\n') }}</pre>
        </div>

        <p v-if="startupFailed" class="startup-failed-msg">{{ startError }}</p>

        <div v-if="startupFailed && port8080Blocked" class="startup-port-blocked">
          <p class="startup-port-title">端口 8080 被占用</p>
          <ul v-if="port8080Processes.length" class="startup-port-list">
            <li v-for="proc in port8080Processes" :key="proc.pid">
              {{ proc.name }} · PID {{ proc.pid }}
            </li>
          </ul>
          <p class="startup-port-hint">可能原因：终端里手动运行了 java -jar / mvn spring-boot:run，与桌面版同时占用 8080。请先关闭其他终端的后端，再点击清除。</p>
          <button
            type="button"
            class="btn-primary btn-clear-port"
            :disabled="clearingPort || starting"
            @click="clearPortAndRestart"
          >
            {{ clearingPort ? '正在清除…' : '一键清除并重新启动' }}
          </button>
        </div>

        <div class="startup-modal-actions">
          <button
            v-if="startupFailed && !port8080Blocked"
            type="button"
            class="btn-secondary"
            @click="closeStartupModal"
          >
            关闭
          </button>
          <button
            v-if="startupFailed && !port8080Blocked"
            type="button"
            class="btn-primary"
            @click="retryStartup"
          >
            重试
          </button>
          <button
            v-if="startupFailed && port8080Blocked"
            type="button"
            class="btn-secondary"
            @click="closeStartupModal"
          >
            关闭
          </button>
          <p v-else-if="starting" class="startup-running-hint">
            <span class="env-check-spinner env-check-spinner--inline" aria-hidden="true"></span>
            请勿关闭窗口…
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import brandLogo from '../assets/icon.png'

const emit = defineEmits(['passed'])

const logoMaskVars = {
  '--env-logo-mask': `url(${brandLogo})`,
}

const checking = ref(true)
const starting = ref(false)
const showStartupModal = ref(false)
const startupFailed = ref(false)
const startupSteps = ref([])
const startupLogs = ref([])
const startError = ref('')
const port8080Blocked = ref(false)
const port8080Processes = ref([])
const clearingPort = ref(false)
const envItems = ref([])
const bundledItems = ref([])
const allInstalled = ref(false)
const summaryMessage = ref('')
const downloadState = reactive({})
const mysqlConfig = reactive({
  host: 'localhost',
  port: 3306,
  username: 'root',
  password: '',
  database: 'kami',
})
const mysqlTesting = ref(false)
const mysqlTestStatus = ref('idle')
const mysqlTestMessage = ref('')

let removeProgressListener = null
let removeStartupListener = null
let mysqlAutoTestTimer = null

const canTestMysql = computed(() =>
  !!(mysqlConfig.host && mysqlConfig.port && mysqlConfig.username && mysqlConfig.password !== '')
)

const anyDownloading = computed(() =>
  Object.values(downloadState).some((s) => s?.status === 'downloading' || s?.status === 'starting')
)

function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let idx = 0
  while (value >= 1024 && idx < units.length - 1) {
    value /= 1024
    idx += 1
  }
  return `${value.toFixed(idx === 0 ? 0 : 1)} ${units[idx]}`
}

function formatDownloadProgress(key) {
  const state = downloadState[key]
  if (!state) return ''
  if (state.status === 'starting') return '准备下载…'
  if (state.percent != null) {
    const sizeText = state.total > 0
      ? `${formatBytes(state.received)} / ${formatBytes(state.total)}`
      : `已下载 ${formatBytes(state.received)}`
    return `下载中 ${state.percent}% · ${sizeText}`
  }
  return `已下载 ${formatBytes(state.received)}…`
}

function isDownloading(key) {
  const status = downloadState[key]?.status
  return status === 'starting' || status === 'downloading'
}

function handleDownloadProgress(data) {
  if (!data?.key) return
  downloadState[data.key] = { ...downloadState[data.key], ...data }
}

async function startDownload(key) {
  if (!window.electronAPI?.downloadInstaller) return
  downloadState[key] = { status: 'starting', percent: 0, received: 0, total: 0 }
  try {
    const result = await window.electronAPI.downloadInstaller(key)
    if (result?.canceled) {
      downloadState[key] = { status: 'idle' }
      return
    }
    if (!result?.success && result?.message) {
      downloadState[key] = { status: 'error', message: result.message }
    }
  } catch (error) {
    downloadState[key] = { status: 'error', message: error.message || '下载失败' }
  }
}

async function cancelDownload(key) {
  await window.electronAPI?.cancelInstallerDownload?.(key)
  downloadState[key] = { status: 'idle' }
}

function openTutorial(url) {
  if (!url) return
  window.electronAPI?.openExternal?.(url)?.catch((error) => {
    console.error('打开教程链接失败:', error)
  })
}

function applyCheckResult(result) {
  envItems.value = result?.envItems || []
  bundledItems.value = result?.bundledItems || []
  allInstalled.value = !!result?.allInstalled
  summaryMessage.value = result?.message || ''
  startError.value = ''
}

async function runCheck() {
  checking.value = true
  summaryMessage.value = ''
  startError.value = ''
  try {
    const result = await window.electronAPI?.checkEnvironment?.()
    applyCheckResult(result)
  } catch (error) {
    console.error('环境检测失败:', error)
    envItems.value = [
      {
        key: 'error',
        label: '环境检测',
        installed: false,
        statusLabel: '失败',
        version: '',
        path: '',
        hint: error.message || '检测失败，请重试',
      },
    ]
    bundledItems.value = []
    allInstalled.value = false
    summaryMessage.value = '环境检测失败，请稍后重试'
  } finally {
    checking.value = false
  }
}

function handleStartupProgress(data) {
  if (data?.type === 'steps' && Array.isArray(data.steps)) {
    startupSteps.value = data.steps
  }
  if (data?.type === 'log' && data.line) {
    startupLogs.value = [...startupLogs.value, data.line].slice(-150)
  }
}

function stepStatusIcon(status) {
  if (status === 'done') return '✓'
  if (status === 'running') return '…'
  if (status === 'error') return '!'
  return '○'
}

function stepStatusLabel(status) {
  if (status === 'done') return '完成'
  if (status === 'running') return '进行中'
  if (status === 'error') return '失败'
  return '等待'
}

function resetStartupState() {
  startupSteps.value = [
    { id: 'java', label: '启动 Java 后端 (java -jar)', status: 'pending' },
    { id: 'health', label: '等待 API 服务就绪', status: 'pending' },
    { id: 'frontend', label: '启动前端 HTTP 服务 (HTML)', status: 'pending' },
  ]
  startupLogs.value = []
  startupFailed.value = false
  startError.value = ''
  port8080Blocked.value = false
  port8080Processes.value = []
}

function closeStartupModal() {
  showStartupModal.value = false
  starting.value = false
  startupFailed.value = false
}

function resetMysqlTest() {
  mysqlTestStatus.value = 'idle'
  mysqlTestMessage.value = ''
}

function onMysqlPasswordBlur() {
  if (!canTestMysql.value || mysqlTesting.value) return
  clearTimeout(mysqlAutoTestTimer)
  mysqlAutoTestTimer = setTimeout(() => {
    testMysqlConnection({ auto: true })
  }, 400)
}

async function testMysqlConnection(options = {}) {
  if (!canTestMysql.value || mysqlTesting.value) return
  mysqlTesting.value = true
  if (!options.auto) {
    mysqlTestStatus.value = 'idle'
    mysqlTestMessage.value = ''
  }
  try {
    const result = await window.electronAPI?.testMysqlConnection?.({
      host: mysqlConfig.host || 'localhost',
      port: mysqlConfig.port || 3306,
      username: mysqlConfig.username || 'root',
      password: mysqlConfig.password ?? '',
      database: mysqlConfig.database || 'kami',
    })
    if (result?.ok) {
      mysqlTestStatus.value = result.dbExists ? 'ok' : 'warn'
      mysqlTestMessage.value = result.message || '连接成功'
    } else {
      mysqlTestStatus.value = 'error'
      mysqlTestMessage.value = result?.message || '连接失败'
    }
  } catch (error) {
    mysqlTestStatus.value = 'error'
    mysqlTestMessage.value = error.message || '连接检测失败'
  } finally {
    mysqlTesting.value = false
  }
}

async function loadMysqlConfigFromDisk() {
  try {
    const saved = await window.electronAPI?.loadMysqlConfig?.()
    if (saved) {
      mysqlConfig.host = saved.host || 'localhost'
      mysqlConfig.port = saved.port || 3306
      mysqlConfig.username = saved.username || 'root'
      mysqlConfig.password = saved.password || ''
      mysqlConfig.database = saved.database || 'kami'
    }
  } catch (error) {
    console.warn('读取 MySQL 配置失败:', error)
  }
}

async function runStartup(options = {}) {
  resetStartupState()
  showStartupModal.value = true
  starting.value = true
  startupFailed.value = false

  removeStartupListener?.()
  removeStartupListener = window.electronAPI?.onStartupProgress?.(handleStartupProgress)

  try {
    await window.electronAPI?.saveMysqlConfig?.({
      host: mysqlConfig.host || 'localhost',
      port: mysqlConfig.port || 3306,
      username: mysqlConfig.username || 'root',
      password: mysqlConfig.password ?? '',
      database: mysqlConfig.database || 'kami',
    })

    const result = await window.electronAPI?.startApplication?.({
      forceRestart: !!options.forceRestart,
      mysqlConfig: {
        host: mysqlConfig.host || 'localhost',
        port: mysqlConfig.port || 3306,
        username: mysqlConfig.username || 'root',
        password: mysqlConfig.password ?? '',
        database: mysqlConfig.database || 'kami',
      },
    })

    if (Array.isArray(result?.steps)) {
      startupSteps.value = result.steps
    }
    if (Array.isArray(result?.logs)) {
      startupLogs.value = result.logs.slice(-150)
    }

    if (!result?.success) {
      startupFailed.value = true
      port8080Blocked.value = !!result.portInUse
      port8080Processes.value = result.portInfo?.processes || []
      startError.value = result?.message || '启动失败，请查看日志'
      return
    }

    showStartupModal.value = false
    emit('passed')
  } catch (error) {
    console.error('启动失败:', error)
    startupFailed.value = true
    startError.value = error.message || '启动失败'
  } finally {
    starting.value = false
    removeStartupListener?.()
    removeStartupListener = null
  }
}

async function goNext() {
  if (!allInstalled.value || starting.value) return
  await runStartup()
}

function retryStartup() {
  runStartup()
}

async function clearPortAndRestart() {
  if (clearingPort.value || starting.value) return
  clearingPort.value = true
  startError.value = ''
  try {
    const result = await window.electronAPI?.clearBackendPort?.()
    if (result?.message) {
      startupLogs.value = [...startupLogs.value, `[清除端口] ${result.message}`].slice(-150)
    }
    if (!result?.success) {
      startupFailed.value = true
      port8080Blocked.value = true
      port8080Processes.value = result?.remaining || port8080Processes.value
      startError.value = result?.message || '清除端口失败'
      return
    }
    port8080Blocked.value = false
    port8080Processes.value = []
    await runStartup({ forceRestart: true })
  } catch (error) {
    startupFailed.value = true
    startError.value = error.message || '清除端口失败'
  } finally {
    clearingPort.value = false
  }
}

watch(
  () => [mysqlConfig.host, mysqlConfig.port, mysqlConfig.username, mysqlConfig.password, mysqlConfig.database],
  () => {
    if (mysqlTestStatus.value !== 'idle') {
      resetMysqlTest()
    }
  }
)

onMounted(async () => {
  removeProgressListener = window.electronAPI?.onDownloadProgress?.(handleDownloadProgress)
  await loadMysqlConfigFromDisk()
  runCheck()
})

onUnmounted(() => {
  removeProgressListener?.()
  removeStartupListener?.()
  clearTimeout(mysqlAutoTestTimer)
})
</script>

<style scoped>
.electron-env-check {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 1.5rem;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 50% 0%, rgba(79, 70, 229, 0.08), transparent 55%),
    #f4f6fb;
}

.env-check-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: #fff;
}

.env-check-bg-shape {
  position: absolute;
  left: 50%;
  top: 42%;
  transform: translate(-50%, -50%);
  width: min(420px, 70vw);
  height: min(420px, 70vw);
  mask-image: var(--env-logo-mask);
  mask-repeat: no-repeat;
  mask-position: center;
  mask-size: contain;
  -webkit-mask-image: var(--env-logo-mask);
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-position: center;
  -webkit-mask-size: contain;
}

.env-check-bg-shape--main {
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.12), rgba(34, 211, 238, 0.08));
  opacity: 0.55;
}

.env-check-bg-shape--glow {
  background: radial-gradient(circle, rgba(79, 70, 229, 0.18), transparent 70%);
  filter: blur(18px);
  opacity: 0.7;
}

.env-check-card {
  position: relative;
  z-index: 1;
  width: min(820px, 100%);
  max-height: calc(100vh - 2rem);
  overflow-y: auto;
  padding: 1.75rem 1.75rem 1.5rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(226, 232, 240, 0.95);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
}

.env-check-header {
  text-align: center;
  margin-bottom: 1.25rem;
}

.env-check-logo {
  display: block;
  margin: 0 auto 0.75rem;
}

.env-check-header h1 {
  margin: 0 0 0.45rem;
  font-size: 1.35rem;
  color: #111827;
}

.env-check-header p {
  margin: 0;
  font-size: 0.875rem;
  color: #64748b;
  line-height: 1.5;
}

.env-section-title {
  margin: 0 0 0.65rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #334155;
  letter-spacing: 0.02em;
}

.env-deps-title {
  margin-top: 0.25rem;
}

.env-deploy-section {
  margin-bottom: 1rem;
}

.env-deploy-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

@media (max-width: 560px) {
  .env-deploy-options {
    grid-template-columns: 1fr;
  }
}

.env-deploy-option {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 0.85rem;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fafbfc;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.env-deploy-option input {
  margin-top: 0.2rem;
  flex-shrink: 0;
}

.env-deploy-option.active {
  border-color: #818cf8;
  background: #eef2ff;
}

.env-deploy-option-body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.8125rem;
  color: #64748b;
  line-height: 1.4;
}

.env-deploy-option-body strong {
  color: #1e293b;
  font-size: 0.875rem;
}

.env-deploy-req {
  font-size: 0.75rem;
  color: #6366f1;
}

.env-region-section {
  margin-bottom: 1rem;
  padding: 0.85rem 1rem;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.env-region-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.35rem;
}

.env-region-header .env-section-title {
  margin: 0;
}

.env-region-badge {
  font-size: 0.75rem;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-weight: 500;
}

.env-region-badge.domestic {
  background: #fef3c7;
  color: #92400e;
}

.env-region-badge.international {
  background: #dbeafe;
  color: #1e40af;
}

.env-region-hint {
  margin: 0 0 0.65rem;
  font-size: 0.8125rem;
  color: #64748b;
  line-height: 1.45;
}

.env-repo-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.env-repo-link {
  padding: 0.4rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #475569;
  font-size: 0.8125rem;
  cursor: pointer;
}

.env-repo-link.primary {
  border-color: #818cf8;
  background: #eef2ff;
  color: #4338ca;
}

.env-repo-link:hover {
  border-color: #94a3b8;
}

.env-region-detecting {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  color: #94a3b8;
}

.env-check-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2rem 0;
  color: #64748b;
}

.env-check-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e2e8f0;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: env-spin 0.8s linear infinite;
}

@keyframes env-spin {
  to { transform: rotate(360deg); }
}

.env-check-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.env-check-item {
  display: flex;
  gap: 0.85rem;
  padding: 0.9rem 1rem;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fafbfc;
}

.env-check-item.is-ok {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.env-check-item.is-missing {
  border-color: #fecaca;
  background: #fff7f7;
}

.env-check-item.is-skipped {
  border-color: #e2e8f0;
  background: #f8fafc;
  opacity: 0.85;
}

.env-check-item.is-skipped .env-check-item-icon {
  background: #e2e8f0;
  color: #64748b;
}

.env-check-item-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  font-weight: 700;
}

.env-check-item.is-ok .env-check-item-icon {
  background: #dcfce7;
  color: #15803d;
}

.env-check-item.is-missing .env-check-item-icon {
  background: #fee2e2;
  color: #b91c1c;
}

.env-check-item-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.env-check-badge {
  font-size: 0.75rem;
  padding: 0.12rem 0.5rem;
  border-radius: 999px;
}

.env-check-badge.ok {
  background: #dcfce7;
  color: #166534;
}

.env-check-badge.warn {
  background: #fee2e2;
  color: #991b1b;
}

.env-check-badge.skip {
  background: #e2e8f0;
  color: #64748b;
}

.env-check-version,
.env-check-path,
.env-check-hint {
  margin: 0.15rem 0 0;
  font-size: 0.8125rem;
  line-height: 1.45;
  color: #64748b;
  word-break: break-all;
}

.env-check-installer {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px dashed #e2e8f0;
}

.env-check-installer-title {
  margin: 0 0 0.65rem;
  font-size: 0.8125rem;
  color: #475569;
  line-height: 1.5;
}

.env-check-installer-file {
  display: block;
  margin-top: 0.2rem;
  font-family: Consolas, monospace;
  font-size: 0.75rem;
  color: #64748b;
}

.env-check-progress-wrap {
  margin-bottom: 0.65rem;
}

.env-check-progress-track {
  height: 8px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.env-check-progress-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #6366f1, #4f46e5);
  transition: width 0.2s ease;
}

.env-check-progress-bar.indeterminate {
  width: 35% !important;
  animation: env-progress-indeterminate 1.2s ease-in-out infinite;
}

@keyframes env-progress-indeterminate {
  0% { transform: translateX(-120%); }
  100% { transform: translateX(320%); }
}

.env-check-progress-text {
  margin: 0.35rem 0 0;
  font-size: 0.75rem;
  color: #6366f1;
}

.env-check-download-done {
  margin: 0 0 0.65rem;
  font-size: 0.8125rem;
  color: #166534;
  word-break: break-all;
}

.env-check-download-error {
  margin: 0 0 0.65rem;
  font-size: 0.8125rem;
  color: #b91c1c;
}

.env-check-installer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.btn-download {
  padding: 0.45rem 0.85rem;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  font-size: 0.8125rem;
  cursor: pointer;
}

.btn-download:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-cancel-download {
  padding: 0.45rem 0.85rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  font-size: 0.8125rem;
  cursor: pointer;
}

.env-check-guide {
  margin-top: 0.75rem;
  padding: 0.75rem 0.85rem;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.env-check-guide.is-post-download {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.env-check-guide-title {
  margin: 0 0 0.35rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #334155;
}

.env-check-guide-steps {
  margin: 0 0 0.5rem;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: #64748b;
}

.env-check-guide-link {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: none;
  background: none;
  color: #2563eb;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.env-check-guide-link:hover {
  color: #1d4ed8;
}

.env-check-guide-auto {
  margin: 0;
  font-size: 0.8125rem;
  color: #059669;
  line-height: 1.45;
}

.env-check-start-error {
  margin: 0.85rem 0 0;
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  font-size: 0.8125rem;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.env-mysql-config {
  margin: 1rem 0 0;
  padding: 0.85rem 1rem;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.env-mysql-hint {
  margin: 0 0 0.75rem;
  font-size: 0.8125rem;
  color: #64748b;
  line-height: 1.45;
}

.env-mysql-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

.env-mysql-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8125rem;
  color: #475569;
}

.env-mysql-grid label.env-mysql-db {
  grid-column: 1 / -1;
}

.env-mysql-grid input {
  padding: 0.45rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 0.8125rem;
}

.env-mysql-test-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-top: 0.75rem;
}

.btn-mysql-test {
  padding: 0.45rem 0.85rem;
  border: 1px solid #818cf8;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 0.8125rem;
  cursor: pointer;
}

.btn-mysql-test:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.env-mysql-test-result {
  margin: 0;
  flex: 1;
  min-width: 200px;
  font-size: 0.8125rem;
  line-height: 1.45;
}

.env-mysql-test-result.ok {
  color: #15803d;
}

.env-mysql-test-result.warn {
  color: #b45309;
}

.env-mysql-test-result.error {
  color: #b91c1c;
}

.startup-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
}

.startup-modal {
  width: min(640px, 100%);
  max-height: calc(100vh - 3rem);
  overflow-y: auto;
  padding: 1.25rem 1.35rem 1.1rem;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.2);
}

.startup-modal-header h2 {
  margin: 0 0 0.35rem;
  font-size: 1.15rem;
  color: #111827;
}

.startup-modal-header p {
  margin: 0 0 1rem;
  font-size: 0.8125rem;
  color: #64748b;
}

.startup-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.startup-step {
  padding: 0.75rem 0.85rem;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fafbfc;
}

.startup-step.is-done {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.startup-step.is-running {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.startup-step.is-error {
  border-color: #fecaca;
  background: #fef2f2;
}

.startup-step-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.startup-step-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  background: #e2e8f0;
  color: #475569;
}

.startup-step.is-done .startup-step-icon {
  background: #dcfce7;
  color: #15803d;
}

.startup-step.is-running .startup-step-icon {
  background: #dbeafe;
  color: #1d4ed8;
}

.startup-step.is-error .startup-step-icon {
  background: #fee2e2;
  color: #b91c1c;
}

.startup-step-badge {
  margin-left: auto;
  font-size: 0.75rem;
  color: #64748b;
}

.startup-step-command,
.startup-step-detail {
  margin: 0.45rem 0 0 1.75rem;
  font-size: 0.75rem;
  line-height: 1.45;
  color: #64748b;
  word-break: break-all;
}

.startup-step-command {
  font-family: Consolas, monospace;
  color: #4338ca;
  background: #eef2ff;
  padding: 0.45rem 0.55rem;
  border-radius: 6px;
}

.startup-log-wrap {
  margin-top: 0.85rem;
}

.startup-log-title {
  margin: 0 0 0.35rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #334155;
}

.startup-log {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  padding: 0.65rem 0.75rem;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 0.72rem;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.startup-failed-msg {
  margin: 0.75rem 0 0;
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  font-size: 0.8125rem;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.startup-port-blocked {
  margin-top: 0.75rem;
  padding: 0.85rem 1rem;
  border-radius: 10px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
}

.startup-port-title {
  margin: 0 0 0.45rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: #9a3412;
}

.startup-port-list {
  margin: 0 0 0.5rem;
  padding-left: 1.1rem;
  font-size: 0.8125rem;
  color: #7c2d12;
  line-height: 1.5;
}

.startup-port-hint {
  margin: 0 0 0.75rem;
  font-size: 0.8125rem;
  color: #9a3412;
  line-height: 1.45;
}

.btn-clear-port {
  width: 100%;
}

.startup-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.65rem;
  margin-top: 1rem;
}

.startup-running-hint {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  font-size: 0.8125rem;
  color: #6366f1;
}

.env-check-spinner--inline {
  width: 18px;
  height: 18px;
  border-width: 2px;
}

.env-bundled-list {
  margin-bottom: 0.25rem;
}

.env-check-summary {
  margin: 1rem 0 0;
  padding: 0.75rem 0.9rem;
  border-radius: 8px;
  font-size: 0.875rem;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.env-check-summary.ok {
  color: #166534;
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.env-check-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.25rem;
}

.btn-primary,
.btn-secondary {
  min-width: 108px;
  padding: 0.55rem 1rem;
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #fff;
  color: #475569;
  border: 1px solid #cbd5e1;
}
</style>
