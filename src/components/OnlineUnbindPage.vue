<template>
  <div class="unbind-page">
    <nav class="navbar">
      <div class="nav-container">
        <button type="button" class="nav-back brand-btn" @click="emit('backHome')">
          <img src="../assets/icon.png" alt="" class="logo-img">
          <span class="brand-label">XXG-KAMI-PRO</span>
        </button>
        <div class="nav-actions">
          <button type="button" class="login-btn" @click="emit('showLogin')">
            登录管理
          </button>
        </div>
      </div>
    </nav>

    <main class="main-content">
      <div class="panel">
        <h1 class="page-title">在线解绑</h1>
        <p class="page-desc">
          输入完整卡密查询绑定状态。仅当管理员已为该卡开启「自助解绑」且已绑定机器码时，才可在线解绑；未开启则仅可查询，无法自助解绑。
        </p>

        <div class="form-row">
          <label for="card-key-input">卡密</label>
          <input
            id="card-key-input"
            v-model.trim="cardKeyInput"
            type="text"
            autocomplete="off"
            placeholder="粘贴或输入卡密（支持高级卡密密文）"
            class="text-input"
            @keyup.enter="query"
          >
        </div>

        <div
          v-if="queryOk && dataPayload?.bound && dataPayload?.allowSelfUnbind && dataPayload?.requireDeviceUnbind"
          class="form-row"
        >
          <label for="device-code-input">原设备码</label>
          <input
            id="device-code-input"
            v-model.trim="deviceCodeInput"
            type="text"
            autocomplete="off"
            placeholder="请输入当前绑定的原设备码（机器码）"
            class="text-input"
            @keyup.enter="unbind"
          >
          <small class="field-hint">该卡已开启原设备解绑，须验证原设备码一致方可解绑。</small>
        </div>

        <div class="actions">
          <button
            type="button"
            class="btn primary"
            :disabled="loadingQuery"
            @click="query"
          >
            {{ loadingQuery ? '查询中…' : '查询' }}
          </button>
          <button
            type="button"
            class="btn danger"
            :disabled="!canUnbind || loadingUnbind || loadingQuery"
            title="需已绑定机器码且已开启自助解绑"
            @click="unbind"
          >
            {{ loadingUnbind ? '解绑中…' : '解绑设备' }}
          </button>
        </div>

        <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p>

        <div v-if="queryOk && dataPayload" class="result-card">
          <h2 class="result-title">查询结果</h2>
          <p v-if="dataPayload.bound && !dataPayload.allowSelfUnbind" class="status no-self-unbind">
            该卡密不支持自主解绑。请联系发卡方或管理员处理。
          </p>
          <p v-else-if="dataPayload.bound && dataPayload.requireDeviceUnbind" class="status bound">
            已绑定设备（原设备码已隐藏，解绑时请填写）。
            <span v-if="dataPayload.deviceId" class="sub">设备 ID：{{ dataPayload.deviceId }}</span>
          </p>
          <p v-else-if="dataPayload.bound" class="status bound">
            已绑定机器码：<code>{{ dataPayload.machineCode }}</code>
            <span v-if="dataPayload.deviceId" class="sub">设备 ID：{{ dataPayload.deviceId }}</span>
          </p>
          <p v-else class="status unbound">
            当前未绑定机器码，无需解绑也无法执行解绑操作。
            <span v-if="dataPayload.allowSelfUnbind" class="sub">（本卡已允许自助解绑，绑定机器码后即可在下方解绑。）</span>
          </p>

          <div v-if="dataPayload.allowSelfUnbind && dataPayload.bound" class="unbind-meta">
            <p v-if="dataPayload.requireDeviceUnbind" class="meta">原设备解绑：已开启（解绑须验证原设备码）</p>
            <p v-if="dataPayload.unbindMaxCount > 0" class="meta">
              解绑次数：已用 {{ dataPayload.unbindCount }} / 上限 {{ dataPayload.unbindMaxCount }}
              <span v-if="dataPayload.remainingUnbindCount != null">（剩余 {{ dataPayload.remainingUnbindCount }} 次）</span>
            </p>
            <p v-if="dataPayload.unbindCooldownHours > 0" class="meta">
              解绑冷却：{{ dataPayload.unbindCooldownHours }} 小时
            </p>
            <p v-if="dataPayload.unbindLimitReason" class="status limit-warn">
              {{ dataPayload.unbindLimitReason }}
            </p>
          </div>

          <p v-if="dataPayload.cardType" class="meta">
            卡类型：{{ dataPayload.cardType === 'time' ? '时间卡' : dataPayload.cardType === 'count' ? '次数卡' : dataPayload.cardType }}
          </p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { cardApi } from '../services/api.js'

const emit = defineEmits(['backHome', 'showLogin'])

const cardKeyInput = ref('')
const deviceCodeInput = ref('')
const loadingQuery = ref(false)
const loadingUnbind = ref(false)
const queryResult = ref(null)
const errorMsg = ref('')

const queryOk = computed(() => queryResult.value?.success === true)
const dataPayload = computed(() => queryResult.value?.data || null)

const canUnbind = computed(() => {
  if (!queryOk.value || !dataPayload.value) return false
  const d = dataPayload.value
  if (d.bound !== true || d.allowSelfUnbind !== true || d.canUnbindNow === false) {
    return false
  }
  if (d.requireDeviceUnbind && !deviceCodeInput.value.trim()) {
    return false
  }
  return true
})

async function query() {
  errorMsg.value = ''
  queryResult.value = null
  deviceCodeInput.value = ''
  const key = cardKeyInput.value.trim()
  if (!key) {
    errorMsg.value = '请输入卡密'
    return
  }
  loadingQuery.value = true
  try {
    const res = await cardApi.publicMachineBindQuery(key)
    queryResult.value = res
    if (!res.success) {
      errorMsg.value = res.message || '查询失败'
    }
  } catch (e) {
    errorMsg.value = e.message || '请求失败'
  } finally {
    loadingQuery.value = false
  }
}

async function unbind() {
  if (!canUnbind.value) return
  const key = cardKeyInput.value.trim()
  const d = dataPayload.value
  if (d?.requireDeviceUnbind && !deviceCodeInput.value.trim()) {
    errorMsg.value = '请填写原设备码'
    return
  }
  const confirmMsg = d?.requireDeviceUnbind
    ? '确定要解绑当前设备吗？须验证原设备码一致。解绑后可在新设备上重新绑定。'
    : '确定要解绑当前设备吗？解绑后可在新设备上重新绑定。'
  if (!window.confirm(confirmMsg)) {
    return
  }
  loadingUnbind.value = true
  errorMsg.value = ''
  try {
    const machineCode = d?.requireDeviceUnbind ? deviceCodeInput.value.trim() : undefined
    const res = await cardApi.publicMachineUnbind(key, machineCode)
    if (res.success) {
      window.alert(res.message || '解绑成功')
      deviceCodeInput.value = ''
      await query()
    } else {
      errorMsg.value = res.message || '解绑失败'
    }
  } catch (e) {
    errorMsg.value = e.message || '请求失败'
  } finally {
    loadingUnbind.value = false
  }
}
</script>

<style scoped>
.unbind-page {
  min-height: 100vh;
  background: #ffffff;
  color: #111827;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #e5e7eb;
  z-index: 1000;
}

.nav-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0.25rem 0;
  font: inherit;
  color: inherit;
}

.logo-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.brand-label {
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.login-btn {
  padding: 0.5rem 1rem;
  background: #111827;
  color: white;
  border: 1px solid #111827;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.login-btn:hover {
  background: #1f2937;
}

.main-content {
  padding: 88px 1.5rem 3rem;
  max-width: 560px;
  margin: 0 auto;
}

.panel {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1.75rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
}

.page-title {
  font-size: 1.5rem;
  font-weight: 800;
  margin: 0 0 0.5rem;
}

.page-desc {
  margin: 0 0 1.5rem;
  color: #6b7280;
  font-size: 0.9rem;
  line-height: 1.6;
}

.form-row label {
  display: block;
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.35rem;
  color: #374151;
}

.text-input {
  width: 100%;
  padding: 0.65rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
  box-sizing: border-box;
}

.text-input:focus {
  outline: none;
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15);
}

.field-hint {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: #6b7280;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.25rem;
}

.btn {
  padding: 0.55rem 1.1rem;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn.primary {
  background: #2563eb;
  color: #fff;
}

.btn.primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn.danger {
  background: #dc2626;
  color: #fff;
}

.btn.danger:hover:not(:disabled) {
  background: #b91c1c;
}

.msg.error {
  margin: 1rem 0 0;
  color: #b91c1c;
  font-size: 0.875rem;
}

.result-card {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid #e5e7eb;
}

.result-title {
  font-size: 1rem;
  font-weight: 700;
  margin: 0 0 0.75rem;
}

.status {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
}

.status.bound {
  color: #166534;
}

.status.unbound {
  color: #6b7280;
}

.status.no-self-unbind {
  color: #b45309;
  background: #fffbeb;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  border: 1px solid #fde68a;
}

.status.limit-warn {
  color: #b45309;
  background: #fffbeb;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  border: 1px solid #fde68a;
  margin-top: 0.5rem;
}

.status code {
  display: inline-block;
  margin-top: 0.25rem;
  padding: 0.15rem 0.4rem;
  background: #ecfdf5;
  border-radius: 4px;
  font-size: 0.85rem;
  word-break: break-all;
}

.sub {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: #4b5563;
}

.unbind-meta {
  margin-top: 0.75rem;
}

.meta {
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  color: #6b7280;
}
</style>
