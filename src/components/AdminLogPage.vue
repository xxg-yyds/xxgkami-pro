<template>
  <div class="admin-log-page">
    <div class="section-header">
      <h2>管理员操作日志</h2>
      <div class="filters">
        <input
          v-model.trim="keyword"
          type="text"
          placeholder="搜索管理员/内容"
          @keyup.enter="loadLogs(1)"
        >
        <select v-model="operationType" @change="loadLogs(1)">
          <option value="">全部类型</option>
          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <button class="btn-secondary" @click="loadLogs(1)">查询</button>
      </div>
    </div>

    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>管理员</th>
            <th>类型</th>
            <th>内容</th>
            <th>IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ formatTime(log.create_time) }}</td>
            <td>{{ log.admin_username }}</td>
            <td><span class="type-tag">{{ typeLabel(log.operation_type) }}</span></td>
            <td class="content-cell">{{ log.operation_content }}</td>
            <td>{{ log.ip_address || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && logs.length === 0" class="empty">暂无日志</p>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="page <= 1" @click="loadLogs(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="loadLogs(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../services/api.js'

const logs = ref([])
const loading = ref(false)
const keyword = ref('')
const operationType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const typeOptions = [
  { value: 'login', label: '登录' },
  { value: 'card_create', label: '创建卡密' },
  { value: 'card_import', label: '导入卡密' },
  { value: 'card_update', label: '更新卡密' },
  { value: 'admin_create', label: '创建管理员' },
  { value: 'admin_update', label: '更新管理员' },
  { value: 'admin_delete', label: '删除管理员' }
]

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function typeLabel(type) {
  return typeOptions.find(t => t.value === type)?.label || type
}

async function loadLogs(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const res = await adminApi.listLogs({
      keyword: keyword.value || undefined,
      operation_type: operationType.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.success) {
      const data = res.data || {}
      logs.value = data.items || []
      total.value = data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadLogs(1))
</script>

<style scoped>
.admin-log-page { padding: 0; }
.section-header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
.filters { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.filters input, .filters select { padding: 0.45rem 0.65rem; border: 1px solid #d1d5db; border-radius: 4px; }
.table-wrap { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; overflow: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
.data-table th, .data-table td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; text-align: left; vertical-align: top; }
.data-table th { background: #f9fafb; font-weight: 600; }
.content-cell { max-width: 420px; word-break: break-word; }
.type-tag { display: inline-block; padding: 0.1rem 0.45rem; border-radius: 4px; background: #f3f4f6; font-size: 0.75rem; }
.empty { padding: 2rem; text-align: center; color: #6b7280; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 1rem; margin-top: 1rem; }
.pagination button { padding: 0.4rem 0.8rem; border: 1px solid #d1d5db; background: #fff; border-radius: 4px; cursor: pointer; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary { padding: 0.45rem 0.9rem; border: 1px solid #d1d5db; background: #f9fafb; border-radius: 4px; cursor: pointer; }
</style>
