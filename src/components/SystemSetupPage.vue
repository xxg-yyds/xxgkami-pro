<template>
  <div class="system-setup">
    <div class="setup-bg" :style="logoMaskVars" aria-hidden="true">
      <div class="setup-bg-shape setup-bg-shape--glow"></div>
      <div class="setup-bg-shape setup-bg-shape--main"></div>
    </div>
    <div class="setup-card">
      <header class="setup-header">
        <h1>{{ pageTitle }}</h1>
        <p v-if="flowMode === 'version-upgrade'">
          检测到系统已升级至新版本，将对比当前程序版本与更新源版本，并检测数据库是否需要完善。
        </p>
        <p v-else-if="flowMode === 'installed'">使用当前 <code>application.properties</code> 中的数据库连接，检测是否有结构或数据需要更新。</p>
        <p v-else>首次部署请完成环境检测与数据库配置，完成后将不再显示本页。</p>
        <p v-if="flowMode === 'version-upgrade' && versionInfo.local" class="version-banner">
          当前版本 <strong>{{ versionInfo.local }}</strong>
          <template v-if="versionInfo.remote"> · 更新源 <strong>{{ versionInfo.remote }}</strong></template>
          <template v-if="versionInfo.recorded"> · 上次记录 <strong>{{ versionInfo.recorded }}</strong></template>
        </p>
      </header>

      <div v-if="sqlTranslating" class="translate-progress translate-progress--global">
        <p class="translate-progress-label">{{ sqlTranslateMessage || '正在将 kami.sql 转译为 5.6 兼容脚本…' }}</p>
        <div class="translate-progress-track">
          <div class="translate-progress-bar" :style="{ width: sqlTranslatePercent + '%' }"></div>
        </div>
        <p class="translate-progress-pct">{{ sqlTranslatePercent }}%</p>
      </div>

      <div v-if="flowMode === 'full'" class="step-tabs">
        <span v-for="(label, i) in stepLabels" :key="i" :class="{ active: step === i, done: step > i }">{{ i + 1 }}. {{ label }}</span>
      </div>

      <!-- 新版升级：数据库完善度检测 -->
      <section v-if="flowMode === 'version-upgrade'" class="setup-section">
        <p class="section-desc">当前连接：<strong>{{ configuredDbText }}</strong></p>
        <div v-if="mergeAnalyzing || envLoading" class="loading-hint">正在检测数据库更新…</div>
        <template v-else-if="mergePreview">
          <div class="merge-preview">
            <p class="merge-preview-summary" :class="{ 'is-ok': !mergePreview.hasChanges }">
              {{ mergePreview.summary }}
            </p>
            <p v-if="mergePreview.warning" class="hint-warn">{{ mergePreview.warning }}</p>
            <div v-if="mergePreview.hasChanges" class="merge-table-wrap">
              <table class="merge-table">
                <thead>
                  <tr>
                    <th>表名</th>
                    <th>类型</th>
                    <th>说明</th>
                    <th>预计插入</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in mergePreview.items" :key="row.table">
                    <td><code>{{ row.table }}</code></td>
                    <td>
                      <span class="merge-tag" :class="row.changeType === 'new_table' ? 'is-new' : 'is-merge'">
                        {{ row.changeType === 'new_table' ? '新表' : '补缺行' }}
                      </span>
                    </td>
                    <td>{{ row.description }}</td>
                    <td>{{ formatInsertEstimate(row) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="mergePreview.hasChanges" class="merge-confirm-hint">是否执行智能更新（将先备份数据库再插入缺失项）？</p>
          </div>
        </template>
        <div v-if="versionUpgradeDone" class="result-box">
          <h3>升级检测已完成</h3>
          <div class="admin-creds">
            <div><span>管理员账号</span><code>{{ versionUpgradeDone.adminUsername }}</code></div>
            <div><span>登录密码</span><span class="hint-text">{{ versionUpgradeDone.adminPasswordHint }}</span></div>
          </div>
          <button type="button" class="btn-primary" :disabled="finishing" @click="emitSetupFinished(versionUpgradeDone)">
            {{ finishing ? '处理中…' : '进入管理后台' }}
          </button>
        </div>
        <div v-else class="setup-actions">
          <template v-if="mergePreview && mergePreview.hasChanges">
            <button type="button" class="btn-secondary" :disabled="mergeAnalyzing || installing" @click="runVersionUpgradeUpdate(false)">
              跳过更新
            </button>
            <button
              type="button"
              class="btn-primary"
              :disabled="installing || !mergePreview.cliReady"
              @click="runVersionUpgradeUpdate(true)"
            >
              {{ installing ? '执行中…' : '智能更新' }}
            </button>
          </template>
          <button
            v-else-if="mergePreview && !mergePreview.hasChanges"
            type="button"
            class="btn-primary"
            :disabled="finishing"
            @click="finishVersionUpgradeOnly"
          >
            {{ finishing ? '处理中…' : '进入管理后台' }}
          </button>
        </div>
      </section>

      <!-- 已安装系统：直接检测更新 -->
      <section v-else-if="flowMode === 'installed'" class="setup-section">
        <p class="section-desc">当前连接：<strong>{{ configuredDbText }}</strong></p>
        <div v-if="mergeAnalyzing" class="loading-hint">正在检测数据库更新…</div>
        <template v-else-if="mergePreview">
          <div class="merge-preview">
            <p class="merge-preview-summary" :class="{ 'is-ok': !mergePreview.hasChanges }">
              {{ mergePreview.summary }}
            </p>
            <p v-if="mergePreview.warning" class="hint-warn">{{ mergePreview.warning }}</p>
            <div v-if="mergePreview.hasChanges" class="merge-table-wrap">
              <table class="merge-table">
                <thead>
                  <tr>
                    <th>表名</th>
                    <th>类型</th>
                    <th>说明</th>
                    <th>预计插入</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in mergePreview.items" :key="row.table">
                    <td><code>{{ row.table }}</code></td>
                    <td>
                      <span class="merge-tag" :class="row.changeType === 'new_table' ? 'is-new' : 'is-merge'">
                        {{ row.changeType === 'new_table' ? '新表' : '补缺行' }}
                      </span>
                    </td>
                    <td>{{ row.description }}</td>
                    <td>{{ formatInsertEstimate(row) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="mergePreview.hasChanges" class="merge-confirm-hint">是否执行上述数据库插入/更新？</p>
          </div>
        </template>
        <div class="setup-actions">
          <button type="button" class="btn-secondary" :disabled="mergeAnalyzing || installing" @click="exitInstalledMode">返回完整向导</button>
          <template v-if="mergePreview && mergePreview.hasChanges">
            <button type="button" class="btn-secondary" :disabled="installing" @click="runInstalledUpdate(false)">否，跳过进入系统</button>
            <button
              type="button"
              class="btn-primary"
              :disabled="installing || !mergePreview.cliReady"
              @click="runInstalledUpdate(true)"
            >
              {{ installing ? '执行中…' : '是，执行插入' }}
            </button>
          </template>
        </div>
      </section>

      <!-- 步骤 0：环境检测 -->
      <section v-else-if="step === 0" class="setup-section">
        <div v-if="envLoading" class="loading-hint">正在检测运行环境…</div>
        <div v-else class="env-grid">
          <div class="env-item">
            <span class="env-label">Java</span>
            <span class="env-value">{{ env.javaVersion || '—' }}</span>
            <span class="env-badge ok">已检测</span>
          </div>
          <div class="env-item">
            <span class="env-label">操作系统</span>
            <span class="env-value">{{ env.osName }} {{ env.osVersion }}</span>
          </div>
          <div class="env-item">
            <span class="env-label">Redis</span>
            <span class="env-value">{{ env.redis?.host }}:{{ env.redis?.port }}</span>
            <span class="env-badge" :class="env.redis?.status === 'online' ? 'ok' : 'warn'">{{ env.redis?.status === 'online' ? '在线' : '离线' }}</span>
          </div>
          <div class="env-item">
            <span class="env-label">MySQL 客户端</span>
            <span class="env-value">mysql / mysqldump</span>
            <span class="env-badge" :class="env.mysqlCliAvailable ? 'ok' : 'warn'">{{ env.mysqlCliAvailable ? '可用' : '未检测到' }}</span>
          </div>
          <div class="env-item wide">
            <span class="env-label">当前 JDBC 数据库</span>
            <span class="env-value">{{ configuredDbText }}</span>
          </div>
          <div class="env-item wide" v-if="env.sqlFiles">
            <span class="env-label">种子 SQL</span>
            <span class="env-value small">
              {{ env.sqlFiles?.mysql80 ? 'kami.sql: 已找到' : 'kami.sql: 未找到' }}
              · 5.6: 由 kami.sql 自动转译
              <template v-if="env.configuredDatabase?.version"> · 检测到 {{ env.configuredDatabase.version }}，推荐 {{ env.configuredDatabase.recommendedLabel || (env.configuredDatabase.recommendedSqlSeries === '80' ? '8.0' : '5.6+') }}</template>
            </span>
          </div>
        </div>
        <div class="setup-actions setup-actions--start">
          <button type="button" class="btn-secondary" :disabled="envLoading || !env.configuredDatabase?.reachable" @click="startInstalledFlow">
            我已安装系统，检测数据库更新
          </button>
          <button type="button" class="btn-primary" :disabled="envLoading" @click="step = 1">下一步：选择 MySQL 版本</button>
        </div>
        <p v-if="!envLoading && !env.configuredDatabase?.reachable" class="hint-err installed-entry-hint">
          当前 JDBC 未连接，无法使用「已安装」快捷检测，请走完整向导或检查 application.properties。
        </p>
      </section>

      <!-- 步骤 1：MySQL 版本 -->
      <section v-else-if="step === 1" class="setup-section">
        <p class="section-desc">请根据实际安装的 MySQL / MariaDB 选择对应脚本（MariaDB 请选择 5.6+ 兼容）。</p>
        <div class="version-cards">
          <label class="version-card" :class="{ selected: form.sqlSeries === '56' }">
            <input v-model="form.sqlSeries" type="radio" value="56" />
            <strong>MySQL 5.0+ / 5.6+ / MariaDB</strong>
            <span>自动从 kami.sql 转译为 5.6 兼容脚本后导入</span>
          </label>
          <label class="version-card" :class="{ selected: form.sqlSeries === '80' }">
            <input v-model="form.sqlSeries" type="radio" value="80" />
            <strong>MySQL 8.0 及以上</strong>
            <span>使用 kami.sql</span>
          </label>
        </div>
        <div class="setup-actions">
          <button type="button" class="btn-secondary" @click="step = 0">上一步</button>
          <button type="button" class="btn-primary" @click="step = 2">下一步：数据库账号</button>
        </div>
      </section>

      <!-- 步骤 2：root 账号 -->
      <section v-else-if="step === 2" class="setup-section">
        <div class="form-grid">
          <div class="form-group">
            <label>主机</label>
            <input v-model="form.host" type="text" placeholder="localhost" />
          </div>
          <div class="form-group">
            <label>端口</label>
            <input v-model.number="form.port" type="number" min="1" max="65535" />
          </div>
          <div class="form-group">
            <label>MySQL 用户名</label>
            <input v-model="form.username" type="text" placeholder="root" />
          </div>
          <div class="form-group">
            <label>MySQL 密码</label>
            <input v-model="form.password" type="password" placeholder="root 密码" />
          </div>
        </div>
        <p v-if="mysqlVersion" class="hint-ok">已连接：{{ mysqlVersion }}<span v-if="recommendedSeries">（推荐脚本：{{ recommendedSeries === '80' ? '8.0' : '5.6+' }}）</span></p>
        <p v-if="testError" class="hint-err">{{ testError }}</p>
        <div class="setup-actions">
          <button type="button" class="btn-secondary" @click="step = 1">上一步</button>
          <button type="button" class="btn-secondary" :disabled="testing" @click="testConnection">{{ testing ? '测试中…' : '测试连接' }}</button>
          <button type="button" class="btn-primary" :disabled="!connectionOk" @click="goDbStrategy">下一步</button>
        </div>
      </section>

      <!-- 步骤 3：库策略 -->
      <section v-else-if="step === 3" class="setup-section">
        <p v-if="dbCheck?.freshInstall && dbCheck?.tableCount > 0" class="section-desc">
          检测到库 <code>kami</code> 中有 {{ dbCheck.tableCount }} 张表，但尚未初始化业务数据（无 <code>admins</code> 表），将按全新安装导入。
        </p>
        <p v-else-if="dbCheck?.freshInstall" class="section-desc">未检测到已有业务库 <code>kami</code> 或库内无表，将执行全新导入。</p>
        <template v-else-if="dbCheck?.needsStrategy">
          <p class="section-desc warn">检测到数据库 <code>kami</code> 已存在（{{ dbCheck.tableCount }} 张表），请选择处理方式：</p>
          <div class="version-cards">
            <label class="version-card" :class="{ selected: form.action === 'overwrite' }">
              <input v-model="form.action" type="radio" value="overwrite" />
              <strong>覆盖安装</strong>
              <span>先备份再删除原库，全量导入种子 SQL（不可逆）</span>
            </label>
            <label class="version-card" :class="{ selected: form.action === 'merge' }">
              <input v-model="form.action" type="radio" value="merge" />
              <strong>智能更新</strong>
              <span>补全新表、insert-ignore 补缺行（需 mysql/mysqldump 客户端）</span>
            </label>
          </div>
          <div v-if="form.action === 'overwrite'" class="confirm-delete">
            <label>二次确认：请输入 <strong>DELETE</strong> 后执行</label>
            <input v-model="form.confirmDelete" type="text" placeholder="DELETE" />
          </div>

          <!-- 智能更新：差异检测结果 -->
          <div v-if="form.action === 'merge' && mergePreview" class="merge-preview">
            <p class="merge-preview-summary" :class="{ 'is-ok': !mergePreview.hasChanges }">
              {{ mergePreview.summary }}
            </p>
            <p v-if="mergePreview.warning" class="hint-warn">{{ mergePreview.warning }}</p>
            <div v-if="mergePreview.hasChanges" class="merge-table-wrap">
              <table class="merge-table">
                <thead>
                  <tr>
                    <th>表名</th>
                    <th>类型</th>
                    <th>说明</th>
                    <th>预计插入</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in mergePreview.items" :key="row.table">
                    <td><code>{{ row.table }}</code></td>
                    <td>
                      <span class="merge-tag" :class="row.changeType === 'new_table' ? 'is-new' : 'is-merge'">
                        {{ row.changeType === 'new_table' ? '新表' : '补缺行' }}
                      </span>
                    </td>
                    <td>{{ row.description }}</td>
                    <td>{{ formatInsertEstimate(row) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="mergePreview.hasChanges" class="merge-confirm-hint">是否执行上述数据库插入/更新？</p>
          </div>
        </template>
        <div class="setup-actions">
          <button type="button" class="btn-secondary" @click="onStep3Back">上一步</button>
          <template v-if="form.action === 'merge' && !mergePreview">
            <button type="button" class="btn-primary" :disabled="mergeAnalyzing" @click="analyzeMerge">
              {{ mergeAnalyzing ? '检测中…' : '检测更新差异' }}
            </button>
          </template>
          <template v-else-if="form.action === 'merge' && mergePreview">
            <button type="button" class="btn-secondary" :disabled="installing" @click="runInstall(false)">否，跳过进入系统</button>
            <button
              type="button"
              class="btn-primary"
              :disabled="installing || (mergePreview.hasChanges && !mergePreview.cliReady)"
              @click="mergePreview.hasChanges ? runInstall(true) : runInstall(false)"
            >
              {{ installing ? '执行中…' : (mergePreview.hasChanges ? '是，执行插入' : '进入系统') }}
            </button>
          </template>
          <button v-else type="button" class="btn-primary" :disabled="!canProceedInstall" @click="runInstall(true)">
            开始安装数据库
          </button>
        </div>
      </section>

      <!-- 步骤 4：安装中 / 完成 -->
      <section v-else-if="step === 4" class="setup-section">
        <div v-if="installing" class="loading-hint">正在执行数据库脚本，请稍候…</div>
        <div v-else-if="installResult" class="result-box">
          <h3>安装完成</h3>
          <p>{{ installResult.message }}</p>
          <div class="admin-creds">
            <div><span>管理员账号</span><code>{{ installResult.adminUsername }}</code></div>
            <div><span>登录密码</span><span class="hint-text">{{ installResult.adminPasswordHint || installResult.adminPassword }}</span></div>
          </div>
          <p class="hint-warn">请登录后立即修改密码。修改配置后需重启后端服务使 JDBC 生效。</p>
          <button type="button" class="btn-primary" :disabled="finishing" @click="finishSetup">
            {{ finishing ? '处理中…' : '完成并进入管理后台' }}
          </button>
        </div>
        <p v-if="installError" class="hint-err">{{ installError }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { setupApi } from '../services/api.js'
import setupLogo from '../assets/icon.png'

/** 黑底 PNG 用 mask 只露出轮廓内的渐变，避免整块黑底盖住白背景 */
const logoMaskVars = {
  '--setup-logo-mask': `url(${setupLogo})`
}

const props = defineProps({
  initialMode: {
    type: String,
    default: 'default'
  }
})

const emit = defineEmits(['setup-finished'])

const pageTitle = computed(() => {
  if (flowMode.value === 'version-upgrade') return '新版更新完善度检测'
  if (flowMode.value === 'installed') return '数据库更新检测'
  return '系统初始化向导'
})

const stepLabels = ['环境检测', 'MySQL 版本', '数据库账号', '安装策略', '完成']
const step = ref(0)
const envLoading = ref(true)
const env = ref({})
const testing = ref(false)
const connectionOk = ref(false)
const mysqlVersion = ref('')
const recommendedSeries = ref('')
const testError = ref('')
const dbCheck = ref(null)
const installing = ref(false)
const installResult = ref(null)
const installError = ref('')
const finishing = ref(false)
const mergeAnalyzing = ref(false)
const mergePreview = ref(null)
const flowMode = ref(props.initialMode === 'version-upgrade' ? 'version-upgrade' : 'full')
const versionInfo = reactive({ local: '', remote: '', recorded: '' })
const versionUpgradeDone = ref(null)
const sqlTranslating = ref(false)
const sqlTranslatePercent = ref(0)
const sqlTranslateMessage = ref('')

const form = reactive({
  sqlSeries: '80',
  host: 'localhost',
  port: 3306,
  username: 'root',
  password: '',
  action: 'fresh',
  confirmDelete: ''
})

const configuredDbText = computed(() => {
  const db = env.value.configuredDatabase
  if (!db) return '—'
  if (db.reachable) {
    return `已连接 · ${db.version || ''} · 表数量 ${db.kamiTableCount ?? '—'}`
  }
  return `未连接 · ${db.error || ''}`
})

const canProceedInstall = computed(() => {
  if (dbCheck.value?.freshInstall) return true
  if (!dbCheck.value?.needsStrategy) return true
  if (form.action === 'overwrite') return form.confirmDelete === 'DELETE'
  return false
})

const dbPayload = () => ({
  host: form.host,
  port: form.port,
  username: form.username,
  password: form.password,
  sqlSeries: form.sqlSeries
})

const formatInsertEstimate = (row) => {
  const n = row.estimatedInsertRows
  if (n == null) return '待检测'
  if (row.changeType === 'new_table') return n > 0 ? `约 ${n} 行` : '结构'
  return n > 0 ? `约 ${n} 行` : '—'
}

const onStep3Back = () => {
  mergePreview.value = null
  step.value = 2
}

const shouldUseMysql56Script = () => {
  if (form.sqlSeries === '56') return true
  const v = String(env.value.configuredDatabase?.version || '').toLowerCase()
  if (!v) return false
  if (v.includes('mariadb')) return true
  const m = v.match(/(\d+)\.(\d+)/)
  return m ? parseInt(m[1], 10) < 8 : false
}

const waitForSql56Translation = async () => {
  if (!shouldUseMysql56Script()) return true
  sqlTranslating.value = true
  sqlTranslatePercent.value = 0
  sqlTranslateMessage.value = '准备转译 kami.sql…'
  try {
    await setupApi.startSqlTranslate({ sqlSeries: '56' })
    const deadline = Date.now() + 120000
    while (Date.now() < deadline) {
      const res = await setupApi.getSqlTranslateStatus()
      const d = res.data || {}
      sqlTranslatePercent.value = d.percent ?? 0
      sqlTranslateMessage.value = d.message || '转译中…'
      if (d.ready) {
        if (d.error || d.status === 'error') {
          ElMessage.error(d.error || d.message || '转译失败')
          return false
        }
        return true
      }
      await new Promise((r) => setTimeout(r, 280))
    }
    ElMessage.error('转译超时，请重试')
    return false
  } catch (e) {
    ElMessage.error(e.message || '转译失败')
    return false
  } finally {
    sqlTranslating.value = false
  }
}

const analyzeMerge = async () => {
  if (!(await waitForSql56Translation())) return
  mergeAnalyzing.value = true
  mergePreview.value = null
  try {
    const res = await setupApi.analyzeMerge(dbPayload())
    if (res.success) {
      mergePreview.value = res.data
      if (!res.data.cliReady && res.data.hasChanges) {
        ElMessage.warning('未检测到 mysql 客户端，可能无法执行插入')
      }
    } else {
      ElMessage.error(res.message || '差异检测失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '差异检测失败')
  } finally {
    mergeAnalyzing.value = false
  }
}

const loadEnvironment = async () => {
  envLoading.value = true
  try {
    const res = await setupApi.getEnvironment()
    if (res.success) {
      env.value = res.data
      const rec = res.data?.configuredDatabase
      if (rec?.recommendedSqlSeries) {
        form.sqlSeries = rec.recommendedSqlSeries
      } else if (rec?.version) {
        const lower = String(rec.version).toLowerCase()
        if (lower.includes('mariadb')) form.sqlSeries = '56'
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '环境检测失败')
  } finally {
    envLoading.value = false
  }
}

const testConnection = async () => {
  testing.value = true
  testError.value = ''
  connectionOk.value = false
  try {
    const res = await setupApi.testMysql({
      host: form.host,
      port: form.port,
      username: form.username,
      password: form.password
    })
    if (res.success && res.data?.ok) {
      connectionOk.value = true
      mysqlVersion.value = res.data.version
      recommendedSeries.value = res.data.recommendedSqlSeries
      if (recommendedSeries.value) form.sqlSeries = recommendedSeries.value
      ElMessage.success('数据库连接成功')
    } else {
      testError.value = res.data?.message || res.message || '连接失败'
    }
  } catch (e) {
    testError.value = e.message || '连接失败'
  } finally {
    testing.value = false
  }
}

const goDbStrategy = async () => {
  try {
    const res = await setupApi.checkKamiDb({
      host: form.host,
      port: form.port,
      username: form.username,
      password: form.password
    })
    if (!res.success || res.data?.ok === false) {
      ElMessage.error(res.data?.message || res.message || '检测数据库失败')
      return
    }
    dbCheck.value = res.data
    if (res.data?.freshInstall) {
      form.action = 'fresh'
      const partial = res.data?.exists && (res.data?.tableCount ?? 0) > 0
      ElMessage.info(
        partial
          ? '检测到业务库尚未初始化，正在自动全新安装…'
          : '将执行全新安装…'
      )
      await runInstall(true)
      return
    }
    form.action = 'merge'
    step.value = 3
  } catch (e) {
    ElMessage.error(e.message || '检测数据库失败')
  }
}

const runInstall = async (applyMerge = true) => {
  if (form.action === 'merge' && applyMerge && mergePreview.value?.hasChanges && !mergePreview.value?.cliReady) {
    ElMessage.error('缺少 mysql/mysqldump 客户端，无法执行插入')
    return
  }
  step.value = 4
  if (!(await waitForSql56Translation())) {
    step.value = 3
    return
  }
  installing.value = true
  installError.value = ''
  installResult.value = null
  try {
    const res = await setupApi.installDatabase({
      ...dbPayload(),
      action: dbCheck.value?.needsStrategy ? form.action : 'fresh',
      confirmDelete: form.confirmDelete,
      applyMerge: form.action === 'merge' ? applyMerge : true
    })
    if (res.success) {
      installResult.value = res.data
      ElMessage.success('数据库安装成功')
    } else {
      installError.value = res.message || '安装失败'
      step.value = 3
    }
  } catch (e) {
    installError.value = e.message || '安装失败'
    step.value = 3
  } finally {
    installing.value = false
  }
}

const finishSetup = async () => {
  finishing.value = true
  try {
    const res = await setupApi.completeSetup({
      sqlSeries: form.sqlSeries,
      completedAt: new Date().toISOString()
    })
    if (res.success) {
      ElMessage.success('系统初始化已完成')
      emit('setup-finished', res.data)
    } else {
      ElMessage.error(res.message || '完成标记写入失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '完成标记写入失败')
  } finally {
    finishing.value = false
  }
}

watch(() => form.action, () => {
  mergePreview.value = null
})

watch(() => form.sqlSeries, (series) => {
  if (series === '56') {
    setupApi.startSqlTranslate({ sqlSeries: '56' }).catch(() => {})
  }
})

const startInstalledFlow = async () => {
  if (!env.value.configuredDatabase?.reachable) {
    ElMessage.error('当前 JDBC 数据库未连接，请检查 application.properties')
    return
  }
  flowMode.value = 'installed'
  mergePreview.value = null
  if (!(await waitForSql56Translation())) {
    return
  }
  mergeAnalyzing.value = true
  try {
    const res = await setupApi.analyzeMergeConfigured()
    if (!res.success) {
      ElMessage.error(res.message || '检测失败')
      flowMode.value = 'full'
      return
    }
    mergePreview.value = res.data
    if (!res.data.hasChanges) {
      ElMessage.success('数据库已是最新，正在进入系统')
      await enterSystemDirectly()
    } else if (!res.data.cliReady) {
      ElMessage.warning('未检测到 mysql/mysqldump 客户端，执行插入可能失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '检测失败')
    flowMode.value = 'full'
  } finally {
    mergeAnalyzing.value = false
  }
}

const exitInstalledMode = () => {
  flowMode.value = 'full'
  mergePreview.value = null
  step.value = 0
}

const enterSystemDirectly = async () => {
  finishing.value = true
  try {
    const res = await setupApi.completeSetup({
      flow: 'installed',
      completedAt: new Date().toISOString()
    })
    if (res.success) {
      emit('setup-finished', res.data)
    } else {
      ElMessage.error(res.message || '进入系统失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '进入系统失败')
  } finally {
    finishing.value = false
  }
}

const emitSetupFinished = (data) => {
  emit('setup-finished', data)
}

const finishVersionUpgradeOnly = async () => {
  finishing.value = true
  try {
    const res = await setupApi.completeVersionUpgrade({
      flow: 'version-upgrade',
      skippedMerge: true,
      completedAt: new Date().toISOString()
    })
    if (res.success) {
      versionUpgradeDone.value = res.data
      ElMessage.success('版本记录已更新')
    } else {
      ElMessage.error(res.message || '完成失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '完成失败')
  } finally {
    finishing.value = false
  }
}

const completeVersionUpgradeFlow = async (extra = {}) => {
  const res = await setupApi.completeVersionUpgrade({
    flow: 'version-upgrade',
    completedAt: new Date().toISOString(),
    ...extra
  })
  if (!res.success) {
    throw new Error(res.message || '版本记录写入失败')
  }
  versionUpgradeDone.value = res.data
  return res.data
}

const startVersionUpgradeFlow = async () => {
  if (!env.value.configuredDatabase?.reachable) {
    ElMessage.error('当前 JDBC 数据库未连接，请检查 application.properties')
    mergeAnalyzing.value = false
    return
  }
  mergePreview.value = null
  versionUpgradeDone.value = null
  if (!(await waitForSql56Translation())) {
    return
  }
  mergeAnalyzing.value = true
  try {
    const res = await setupApi.analyzeMergeConfigured()
    if (!res.success) {
      ElMessage.error(res.message || '检测失败')
      return
    }
    mergePreview.value = res.data
    if (!res.data.hasChanges) {
      ElMessage.success('数据库结构已完善')
    } else if (!res.data.cliReady) {
      ElMessage.warning('未检测到 mysql/mysqldump 客户端，智能更新可能无法执行')
    }
  } catch (e) {
    ElMessage.error(e.message || '检测失败')
  } finally {
    mergeAnalyzing.value = false
  }
}

const runVersionUpgradeUpdate = async (applyMerge) => {
  if (!applyMerge) {
    try {
      await ElMessageBox.confirm(
        '跳过数据库更新可能导致系统运行异常（缺少表或字段）。确定仍要跳过吗？',
        '警告',
        { type: 'warning', confirmButtonText: '仍要跳过', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    finishing.value = true
    try {
      await completeVersionUpgradeFlow({ skippedMerge: true })
      ElMessage.warning('已跳过数据库更新，请尽快手动核对库结构')
    } catch (e) {
      ElMessage.error(e.message || '操作失败')
    } finally {
      finishing.value = false
    }
    return
  }
  if (mergePreview.value?.hasChanges && !mergePreview.value?.cliReady) {
    ElMessage.error('缺少 mysql/mysqldump 客户端，无法执行智能更新')
    return
  }
  if (!(await waitForSql56Translation())) {
    return
  }
  installing.value = true
  try {
    if (mergePreview.value?.hasChanges) {
      const res = await setupApi.installConfigured({ applyMerge: true })
      if (!res.success) {
        ElMessage.error(res.message || '智能更新失败')
        return
      }
      ElMessage.success(res.data?.message || '数据库已备份并完成智能更新')
    }
    await completeVersionUpgradeFlow({ appliedMerge: !!mergePreview.value?.hasChanges })
    ElMessage.success('新版升级检测已完成')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    installing.value = false
  }
}

const runInstalledUpdate = async (applyMerge) => {
  if (applyMerge && mergePreview.value?.hasChanges && !mergePreview.value?.cliReady) {
    ElMessage.error('缺少 mysql/mysqldump 客户端，无法执行插入')
    return
  }
  if (!(await waitForSql56Translation())) {
    return
  }
  installing.value = true
  try {
    if (applyMerge && mergePreview.value?.hasChanges) {
      const res = await setupApi.installConfigured({ applyMerge: true })
      if (!res.success) {
        ElMessage.error(res.message || '更新失败')
        return
      }
      ElMessage.success(res.data?.message || '数据库更新完成')
    }
    await enterSystemDirectly()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    installing.value = false
  }
}

onMounted(async () => {
  if (props.initialMode === 'version-upgrade') {
    flowMode.value = 'version-upgrade'
    mergeAnalyzing.value = true
    try {
      const st = await setupApi.getStatus()
      if (st.success && st.data) {
        versionInfo.local = st.data.localVersion || ''
        versionInfo.remote = st.data.remoteVersion || ''
        versionInfo.recorded = st.data.recordedVersion || ''
      }
    } catch {
      /* 版本信息非阻塞 */
    }
    await loadEnvironment()
    await startVersionUpgradeFlow()
    return
  }
  await loadEnvironment()
})
</script>

<style scoped>
.system-setup {
  position: relative;
  min-height: 100vh;
  background: #f4f6fb;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  box-sizing: border-box;
  overflow: hidden;
}

.setup-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background: #ffffff;
}

.setup-bg-shape {
  position: absolute;
  left: 50%;
  top: 50%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  background: linear-gradient(165deg, #22d3ee 0%, #4f46e5 48%, #c026d3 100%);
  -webkit-mask-image: var(--setup-logo-mask);
  mask-image: var(--setup-logo-mask);
  -webkit-mask-mode: luminance;
  mask-mode: luminance;
  -webkit-mask-size: contain;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  will-change: opacity, transform;
}

.setup-bg-shape--main {
  width: min(108vmin, 960px);
  filter: blur(18px) saturate(1.25);
  animation: setup-logo-fade 6s ease-in-out infinite;
}

.setup-bg-shape--glow {
  width: min(128vmin, 1120px);
  filter: blur(42px) saturate(1.35);
  animation: setup-logo-fade-glow 8s ease-in-out infinite;
  animation-delay: -2.5s;
}

@keyframes setup-logo-fade {
  0%,
  100% {
    opacity: 0.62;
    transform: translate(-50%, -50%) scale(0.97);
  }
  50% {
    opacity: 0.95;
    transform: translate(-50%, -50%) scale(1.05);
  }
}

@keyframes setup-logo-fade-glow {
  0%,
  100% {
    opacity: 0.5;
    transform: translate(-50%, -50%) scale(1.02);
  }
  50% {
    opacity: 0.85;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

.setup-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 720px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.08);
  padding: 1.75rem 2rem 2rem;
}

.setup-header h1 {
  margin: 0 0 0.35rem;
  font-size: 1.35rem;
  color: #111827;
}

.setup-header p {
  margin: 0;
  font-size: 0.875rem;
  color: #6b7280;
}

.step-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.75rem;
  margin: 1.25rem 0 1rem;
  font-size: 0.75rem;
  color: #9ca3af;
}

.step-tabs span.active {
  color: #4f46e5;
  font-weight: 600;
}

.step-tabs span.done {
  color: #10b981;
}

.env-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

.env-item {
  padding: 0.65rem 0.75rem;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.env-item.wide {
  grid-column: 1 / -1;
}

.env-label {
  font-size: 0.7rem;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.env-value {
  font-size: 0.8125rem;
  color: #111827;
  word-break: break-all;
}

.env-value.small {
  font-size: 0.75rem;
}

.env-badge {
  align-self: flex-start;
  font-size: 0.65rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  margin-top: 0.15rem;
}

.env-badge.ok {
  background: #d1fae5;
  color: #047857;
}

.env-badge.warn {
  background: #fef3c7;
  color: #b45309;
}

.version-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.version-card {
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  padding: 1rem;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.version-card input {
  position: absolute;
  opacity: 0;
}

.version-card.selected {
  border-color: #4f46e5;
  background: #eef2ff;
}

.version-card strong {
  font-size: 0.9rem;
  color: #111827;
}

.version-card span {
  font-size: 0.75rem;
  color: #6b7280;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.form-group label {
  display: block;
  font-size: 0.8125rem;
  margin-bottom: 0.25rem;
  color: #374151;
}

.form-group input {
  width: 100%;
  padding: 0.5rem 0.6rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-sizing: border-box;
}

.section-desc {
  font-size: 0.875rem;
  color: #4b5563;
  margin: 0 0 1rem;
}

.section-desc.warn {
  color: #b45309;
}

.confirm-delete {
  margin-top: 1rem;
}

.confirm-delete input {
  width: 100%;
  margin-top: 0.35rem;
  padding: 0.5rem;
  border: 1px solid #fca5a5;
  border-radius: 6px;
}

.merge-preview {
  margin-top: 1rem;
  padding: 0.85rem 1rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.merge-preview-summary {
  margin: 0 0 0.65rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: #4f46e5;
}

.merge-preview-summary.is-ok {
  color: #047857;
}

.merge-table-wrap {
  max-height: 220px;
  overflow: auto;
  margin-bottom: 0.65rem;
}

.merge-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.merge-table th,
.merge-table td {
  padding: 0.45rem 0.5rem;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.merge-table th {
  color: #6b7280;
  font-weight: 600;
  background: #fff;
  position: sticky;
  top: 0;
}

.merge-tag {
  display: inline-block;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
}

.merge-tag.is-new {
  background: #dbeafe;
  color: #1d4ed8;
}

.merge-tag.is-merge {
  background: #ede9fe;
  color: #5b21b6;
}

.merge-confirm-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: #374151;
}

.setup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1.25rem;
}

.setup-actions--start {
  flex-direction: column;
  align-items: stretch;
}

.setup-actions--start .btn-primary,
.setup-actions--start .btn-secondary {
  width: 100%;
  text-align: center;
}

.installed-entry-hint {
  margin: 0.5rem 0 0;
  text-align: center;
}

.btn-primary,
.btn-secondary {
  padding: 0.55rem 1.1rem;
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.result-box h3 {
  margin: 0 0 0.5rem;
  color: #111827;
}

.admin-creds {
  margin: 1rem 0;
  padding: 1rem;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
}

.admin-creds div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}

.admin-creds code {
  font-size: 1rem;
  color: #166534;
  font-weight: 600;
}

.admin-creds .hint-text {
  max-width: 60%;
  text-align: right;
  color: #166534;
  font-size: 0.8125rem;
  line-height: 1.4;
}

.version-banner {
  margin-top: 0.5rem;
  font-size: 0.8125rem;
  color: #475569;
}

.translate-progress--global {
  margin: 0 0 1.25rem;
}

.translate-progress {
  margin: 1rem 0 1.25rem;
  padding: 1rem 1.125rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.translate-progress-label {
  margin: 0 0 0.625rem;
  font-size: 0.875rem;
  color: #334155;
}

.translate-progress-track {
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.translate-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #22d3ee);
  border-radius: 4px;
  transition: width 0.25s ease;
}

.translate-progress-pct {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  color: #64748b;
  text-align: right;
}

.hint-ok {
  color: #047857;
  font-size: 0.8125rem;
}

.hint-err {
  color: #dc2626;
  font-size: 0.8125rem;
}

.hint-warn {
  color: #b45309;
  font-size: 0.8125rem;
}

.loading-hint {
  text-align: center;
  padding: 2rem;
  color: #6b7280;
}

@media (max-width: 560px) {
  .version-cards,
  .form-grid,
  .env-grid {
    grid-template-columns: 1fr;
  }
}
</style>
