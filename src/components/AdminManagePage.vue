<template>
  <div class="admin-manage-page">
    <div class="section-header">
      <h2>管理员管理</h2>
      <button class="btn-primary" @click="openCreate">
        <i class="fas fa-plus"></i> 添加管理员
      </button>
    </div>

    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>最后登录</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in admins" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.username }}</td>
            <td>{{ item.email || '—' }}</td>
            <td>
              <span v-if="item.is_super" class="badge super">超级管理员</span>
              <span v-else class="badge normal">普通管理员</span>
            </td>
            <td>
              <span :class="['badge', item.status === 1 ? 'ok' : 'off']">
                {{ item.status === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ formatTime(item.last_login) }}</td>
            <td class="actions">
              <button class="btn-link" @click="openEdit(item)">编辑</button>
              <button
                v-if="item.id !== currentUserId"
                class="btn-link danger"
                @click="removeAdmin(item)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && admins.length === 0" class="empty">暂无管理员</p>
    </div>

    <div v-if="showModal" class="modal-overlay" @click="showModal = false">
      <div class="admin-modal" @click.stop>
        <div class="modal-header">
          <div>
            <h3>{{ editingId ? '编辑管理员' : '添加管理员' }}</h3>
            <p class="modal-subtitle">配置登录信息与可访问的后台功能</p>
          </div>
          <button type="button" class="close-btn" aria-label="关闭" @click="showModal = false">×</button>
        </div>

        <div class="modal-body">
          <section class="form-section">
            <h4 class="section-label">基本信息</h4>
            <div class="field-grid">
              <div class="form-group">
                <label for="admin-username">用户名</label>
                <input
                  id="admin-username"
                  v-model.trim="form.username"
                  type="text"
                  placeholder="登录用户名"
                  class="text-input"
                >
              </div>
              <div class="form-group">
                <label for="admin-email">邮箱</label>
                <input
                  id="admin-email"
                  v-model.trim="form.email"
                  type="email"
                  placeholder="可选"
                  class="text-input"
                >
              </div>
            </div>
            <div class="form-group">
              <label for="admin-password">{{ editingId ? '新密码（留空不改）' : '密码' }}</label>
              <input
                id="admin-password"
                v-model="form.password"
                type="password"
                placeholder="至少 6 位"
                class="text-input"
              >
            </div>
          </section>

          <section v-if="userInfo?.isSuper" class="form-section">
            <h4 class="section-label">角色类型</h4>
            <div
              class="role-card"
              :class="{ 'role-card--active': form.is_super }"
            >
              <label class="role-toggle-row">
                <span class="role-switch">
                  <input
                    v-model="form.is_super"
                    type="checkbox"
                    class="role-switch-input"
                  >
                  <span class="role-switch-track">
                    <span class="role-switch-thumb"></span>
                  </span>
                </span>
                <span class="role-copy">
                  <span class="role-title">
                    超级管理员
                    <span v-if="form.is_super" class="role-pill">已开启</span>
                  </span>
                  <span class="role-desc">拥有全部后台权限，并可管理其他管理员账号</span>
                </span>
              </label>
            </div>
          </section>

          <section v-if="!form.is_super" class="form-section">
            <div class="section-head-row">
              <h4 class="section-label">功能权限</h4>
              <div class="perm-actions">
                <button type="button" class="perm-action-btn" @click="selectAllPermissions">全选</button>
                <span class="perm-divider">|</span>
                <button type="button" class="perm-action-btn" @click="clearPermissions">清空</button>
              </div>
            </div>
            <p class="section-hint">勾选该管理员可访问的菜单与功能，至少选择一项。</p>
            <div class="perm-grid">
              <label
                v-for="(label, code) in permissionLabels"
                :key="code"
                class="perm-card"
                :class="{
                  'perm-card--active': isPermissionChecked(code),
                  'perm-card--disabled': isPermissionDisabled(code)
                }"
              >
                <input
                  v-model="form.permissions"
                  type="checkbox"
                  class="perm-native-input"
                  :value="code"
                  :disabled="isPermissionDisabled(code)"
                >
                <span class="perm-check" aria-hidden="true">
                  <svg v-if="isPermissionChecked(code)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                </span>
                <span class="perm-label">{{ label }}</span>
              </label>
            </div>
            <p class="perm-count">已选 {{ form.permissions.length }} / {{ permissionCount }} 项</p>
          </section>

          <section v-if="editingId" class="form-section">
            <h4 class="section-label">账号状态</h4>
            <div class="status-toggle">
              <button
                type="button"
                class="status-btn"
                :class="{ 'status-btn--active': form.status === 1 }"
                @click="form.status = 1"
              >
                启用
              </button>
              <button
                type="button"
                class="status-btn"
                :class="{ 'status-btn--active': form.status === 0 }"
                @click="form.status = 0"
              >
                禁用
              </button>
            </div>
          </section>
        </div>

        <div class="modal-actions">
          <button type="button" class="btn-secondary" @click="showModal = false">取消</button>
          <button type="button" class="btn-primary" :disabled="saving" @click="saveAdmin">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../services/api.js'
import { ADMIN_PERMISSION_LABELS } from '../utils/adminPermission.js'

const props = defineProps({
  userInfo: Object
})

const permissionLabels = ADMIN_PERMISSION_LABELS
const permissionCount = computed(() => Object.keys(permissionLabels).length)
const admins = ref([])
const loading = ref(false)
const saving = ref(false)
const showModal = ref(false)
const editingId = ref(null)

const form = reactive({
  username: '',
  password: '',
  email: '',
  is_super: false,
  permissions: ['overview', 'keys'],
  status: 1
})

const currentUserId = computed(() => props.userInfo?.id)

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function isPermissionChecked(code) {
  return form.permissions.includes(code)
}

function isPermissionDisabled(code) {
  return code === 'admins' && !props.userInfo?.isSuper
}

function selectablePermissionCodes() {
  return Object.keys(permissionLabels).filter((code) => !isPermissionDisabled(code))
}

function selectAllPermissions() {
  form.permissions = selectablePermissionCodes()
}

function clearPermissions() {
  form.permissions = []
}

async function loadAdmins() {
  loading.value = true
  try {
    const res = await adminApi.listAccounts()
    if (res.success) {
      admins.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.email = ''
  form.is_super = false
  form.permissions = ['overview', 'keys']
  form.status = 1
}

function openCreate() {
  editingId.value = null
  resetForm()
  showModal.value = true
}

function openEdit(item) {
  editingId.value = item.id
  form.username = item.username
  form.password = ''
  form.email = item.email || ''
  form.is_super = !!item.is_super
  form.permissions = Array.isArray(item.permissions) ? [...item.permissions] : []
  form.status = item.status ?? 1
  showModal.value = true
}

async function saveAdmin() {
  if (!form.username) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!editingId.value && (!form.password || form.password.length < 6)) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (!form.is_super && form.permissions.length === 0) {
    ElMessage.warning('请至少选择一项权限')
    return
  }
  saving.value = true
  try {
    const payload = {
      username: form.username,
      email: form.email,
      is_super: form.is_super,
      permissions: form.is_super ? [] : form.permissions
    }
    if (form.password) payload.password = form.password
    if (editingId.value) payload.status = form.status

    const res = editingId.value
      ? await adminApi.updateAccount(editingId.value, payload)
      : await adminApi.createAccount(payload)

    if (res.success) {
      ElMessage.success(editingId.value ? '已更新' : '已创建')
      showModal.value = false
      await loadAdmins()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeAdmin(item) {
  try {
    await ElMessageBox.confirm(`确定删除管理员「${item.username}」？`, '确认删除', { type: 'warning' })
    const res = await adminApi.deleteAccount(item.id)
    if (res.success) {
      ElMessage.success('已删除')
      await loadAdmins()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    // cancelled
  }
}

onMounted(loadAdmins)
</script>

<style scoped>
.admin-manage-page {
  padding: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.table-wrap {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.data-table th,
.data-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f3f4f6;
  text-align: left;
}

.data-table th {
  background: #f9fafb;
  font-weight: 600;
  color: #374151;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
}

.badge.super { background: #ede9fe; color: #5b21b6; }
.badge.normal { background: #e0f2fe; color: #0369a1; }
.badge.ok { background: #dcfce7; color: #166534; }
.badge.off { background: #fee2e2; color: #991b1b; }

.actions { display: flex; gap: 0.5rem; }
.btn-link { border: none; background: none; color: #2563eb; cursor: pointer; padding: 0; }
.btn-link.danger { color: #dc2626; }
.empty { padding: 2rem; text-align: center; color: #6b7280; }

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 1rem;
}

.admin-modal {
  background: #fff;
  border-radius: 14px;
  width: min(580px, 100%);
  max-height: min(90vh, 820px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem 1.5rem 1rem;
  border-bottom: 1px solid #f3f4f6;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  color: #111827;
}

.modal-subtitle {
  margin: 0.35rem 0 0;
  font-size: 0.8125rem;
  color: #6b7280;
}

.close-btn {
  border: none;
  background: #f3f4f6;
  color: #6b7280;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
}

.close-btn:hover {
  background: #e5e7eb;
  color: #374151;
}

.modal-body {
  padding: 0.25rem 1.5rem 1rem;
  overflow-y: auto;
}

.form-section {
  padding: 1rem 0;
  border-bottom: 1px solid #f3f4f6;
}

.form-section:last-child {
  border-bottom: none;
}

.section-label {
  margin: 0 0 0.75rem;
  font-size: 0.8125rem;
  font-weight: 700;
  color: #374151;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.section-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.35rem;
}

.section-head-row .section-label {
  margin-bottom: 0;
}

.section-hint {
  margin: 0 0 0.85rem;
  font-size: 0.8125rem;
  color: #6b7280;
  line-height: 1.5;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.form-group {
  margin-bottom: 0.75rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #374151;
}

.text-input {
  width: 100%;
  padding: 0.6rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 0.875rem;
  box-sizing: border-box;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.text-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

/* 超级管理员开关 */
.role-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 1rem 1.125rem;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.role-card--active {
  border-color: #c7d2fe;
  background: linear-gradient(165deg, #f8faff 0%, #ffffff 55%);
  box-shadow:
    0 0 0 1px rgba(79, 70, 229, 0.07),
    0 6px 20px rgba(79, 70, 229, 0.08);
}

.role-toggle-row {
  display: flex;
  align-items: flex-start;
  gap: 0.875rem;
  cursor: pointer;
  margin: 0;
}

.role-switch {
  position: relative;
  width: 48px;
  height: 28px;
  flex-shrink: 0;
  margin-top: 2px;
}

.role-switch-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  z-index: 2;
  cursor: pointer;
}

.role-switch-track {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #d1d5db;
  transition: background 0.22s ease;
}

.role-switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.18);
  transition: transform 0.22s cubic-bezier(0.34, 1.1, 0.64, 1);
  pointer-events: none;
}

.role-switch-input:checked + .role-switch-track {
  background: #4f46e5;
}

.role-switch-input:checked + .role-switch-track .role-switch-thumb {
  transform: translateX(20px);
}

.role-switch-input:focus-visible + .role-switch-track {
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.28);
}

.role-copy {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.role-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9375rem;
  font-weight: 600;
  color: #111827;
}

.role-pill {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
}

.role-desc {
  font-size: 0.8125rem;
  color: #6b7280;
  line-height: 1.45;
}

/* 权限多选卡片 */
.perm-actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.perm-action-btn {
  border: none;
  background: none;
  color: #4f46e5;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  padding: 0.15rem 0.25rem;
}

.perm-action-btn:hover {
  color: #3730a3;
  text-decoration: underline;
}

.perm-divider {
  color: #d1d5db;
  font-size: 0.75rem;
}

.perm-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.55rem;
}

.perm-card {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  user-select: none;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.12s ease;
}

.perm-card:hover:not(.perm-card--disabled) {
  border-color: #c7d2fe;
  background: #fafbff;
}

.perm-card--active {
  border-color: #818cf8;
  background: linear-gradient(165deg, #eef2ff 0%, #f5f7ff 100%);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.12);
}

.perm-card--disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.perm-native-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.perm-check {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border: 2px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.18s, background 0.18s, transform 0.12s;
}

.perm-check svg {
  width: 13px;
  height: 13px;
  color: #fff;
}

.perm-card--active .perm-check {
  border-color: #4f46e5;
  background: #4f46e5;
  transform: scale(1.02);
}

.perm-label {
  font-size: 0.8125rem;
  font-weight: 500;
  color: #374151;
  line-height: 1.3;
}

.perm-card--active .perm-label {
  color: #312e81;
  font-weight: 600;
}

.perm-count {
  margin: 0.75rem 0 0;
  font-size: 0.75rem;
  color: #9ca3af;
  text-align: right;
}

/* 状态切换 */
.status-toggle {
  display: inline-flex;
  padding: 3px;
  background: #f3f4f6;
  border-radius: 10px;
  gap: 2px;
}

.status-btn {
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 0.8125rem;
  font-weight: 500;
  padding: 0.45rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.status-btn--active {
  background: #fff;
  color: #111827;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

.status-btn:first-child.status-btn--active {
  color: #166534;
}

.status-btn:last-child.status-btn--active {
  color: #991b1b;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem 1.25rem;
  border-top: 1px solid #f3f4f6;
  background: #fafafa;
}

.btn-primary,
.btn-secondary {
  padding: 0.55rem 1.15rem;
  border-radius: 8px;
  border: none;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.1s;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #4338ca;
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-secondary {
  background: #fff;
  color: #374151;
  border: 1px solid #d1d5db;
}

.btn-secondary:hover {
  background: #f9fafb;
}

@media (max-width: 560px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .perm-grid {
    grid-template-columns: 1fr;
  }
}
</style>
