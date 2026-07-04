<template>
  <div class="pricing-manage-page">
    <div class="section-header">
      <h2>卡密定价管理</h2>
      <button class="btn-primary" @click="openAddModal">
        <i class="fas fa-plus"></i>
        添加定价
      </button>
    </div>

    <!-- 时间卡定价 -->
    <div class="pricing-section">
      <h3>时间卡定价</h3>
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>描述</th>
              <th>时长(天)</th>
              <th>价格(元)</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in timeCards" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.description }}</td>
              <td>{{ item.value }}</td>
              <td>¥{{ item.price }}</td>
              <td>
                <div class="action-buttons">
                  <button class="btn-primary btn-sm" @click="editPricing(item)">编辑</button>
                  <button class="btn-danger btn-sm" @click="deletePricing(item.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 次数卡定价 -->
    <div class="pricing-section" style="margin-top: 2rem;">
      <h3>次数卡定价</h3>
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>描述</th>
              <th>次数</th>
              <th>价格(元)</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in countCards" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.description }}</td>
              <td>{{ item.value }}</td>
              <td>¥{{ item.price }}</td>
              <td>
                <div class="action-buttons">
                  <button class="btn-primary btn-sm" @click="editPricing(item)">编辑</button>
                  <button class="btn-danger btn-sm" @click="deletePricing(item.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 编辑/添加 弹窗 -->
    <div v-if="showModal" class="modal-overlay">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEditing ? '编辑定价' : '添加定价' }}</h3>
          <button class="close-btn" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="savePricing">
            <div class="form-group">
              <label>类型</label>
              <select v-model="form.type" :disabled="isEditing">
                <option value="time">时间卡</option>
                <option value="count">次数卡</option>
              </select>
            </div>
            <div class="form-group">
              <label>{{ form.type === 'time' ? '时长(天)' : '次数' }}</label>
              <input type="number" v-model.number="form.value" min="1" step="1" :disabled="saving">
            </div>
            <div class="form-group">
              <label>价格</label>
              <input type="number" v-model.number="form.price" min="0.01" step="0.01" :disabled="saving">
            </div>
            <div class="form-group">
              <label>描述</label>
              <input
                type="text"
                v-model.trim="form.description"
                maxlength="100"
                placeholder="例如: 7天时间卡"
                :disabled="saving"
              >
            </div>
            <div class="modal-actions">
              <button type="button" class="btn-secondary" :disabled="saving" @click="closeModal">取消</button>
              <button type="submit" class="btn-primary" :disabled="saving">
                {{ saving ? '保存中...' : '保存' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pricingApi } from '../services/api.js'

const timeCards = ref([])
const countCards = ref([])
const showModal = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  type: 'time',
  value: 1,
  price: 0,
  description: ''
})

const fetchPricing = async () => {
  try {
    const res = await pricingApi.getAllPricing()
    if (res.success && res.data) {
      timeCards.value = res.data.timeCards || []
      countCards.value = res.data.countCards || []
    }
  } catch (error) {
    console.error('Failed to fetch pricing:', error)
    // alert('获取定价失败')
  }
}

const openAddModal = () => {
  isEditing.value = false
  form.id = null
  form.type = 'time'
  form.value = 1
  form.price = 0
  form.description = ''
  showModal.value = true
}

const editPricing = (item) => {
  isEditing.value = true
  form.id = item.id
  form.type = item.type
  form.value = item.value
  form.price = item.price
  form.description = item.description
  showModal.value = true
}

const deletePricing = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个定价吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await pricingApi.deletePricing(id)
    if (res.success) {
      ElMessage.success('删除成功')
      fetchPricing()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    console.error('Failed to delete pricing:', error)
    ElMessage.error('删除失败')
  }
}

const closeModal = () => {
  if (saving.value) return
  showModal.value = false
}

const validateForm = () => {
  const value = Number(form.value)
  const price = Number(form.price)
  const description = (form.description || '').trim()

  if (!Number.isInteger(value) || value < 1) {
    ElMessage.warning(form.type === 'time' ? '时长须为大于 0 的整数天' : '次数须为大于 0 的整数')
    return false
  }
  if (!Number.isFinite(price) || price <= 0) {
    ElMessage.warning('价格须大于 0')
    return false
  }
  if (!description) {
    ElMessage.warning('请填写描述')
    return false
  }
  if (description.length > 100) {
    ElMessage.warning('描述不能超过 100 个字符')
    return false
  }

  const list = form.type === 'time' ? timeCards.value : countCards.value
  const duplicate = list.some(
    (item) => item.value === value && (!isEditing.value || item.id !== form.id)
  )
  if (duplicate) {
    ElMessage.warning(
      form.type === 'time' ? '已存在相同天数的时间卡定价' : '已存在相同次数的次数卡定价'
    )
    return false
  }

  form.value = value
  form.price = price
  form.description = description
  return true
}

const savePricing = async () => {
  if (saving.value) return
  if (!validateForm()) return

  saving.value = true
  try {
    const payload = {
      type: form.type,
      value: form.value,
      price: form.price,
      description: form.description
    }
    let res
    if (isEditing.value) {
      res = await pricingApi.updatePricing(form.id, payload)
    } else {
      res = await pricingApi.addPricing(payload)
    }

    if (res.success) {
      ElMessage.success(res.message || (isEditing.value ? '更新成功' : '添加成功'))
      showModal.value = false
      fetchPricing()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    console.error('Failed to save pricing:', error)
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchPricing()
})
</script>

<style scoped>
.pricing-manage-page {
  padding: 1rem;
  color: var(--text-primary, #111827);
}

.section-header h2 {
  color: var(--text-primary, #111827);
  margin: 0;
}

.pricing-section h3 {
  color: var(--text-primary, #111827);
  margin: 0 0 1rem;
  font-size: 1.125rem;
  font-weight: 600;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.table-container {
  background: var(--surface, #ffffff);
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
  border: 1px solid var(--border-color, #e5e7eb);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 1rem;
  text-align: left;
  border-bottom: 1px solid var(--border-color, #eee);
}

td {
  color: var(--text-secondary, #374151);
}

th {
  background: var(--surface-muted, #f9fafb);
  font-weight: 600;
  color: var(--text-secondary, #374151);
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
}

.btn-primary {
  background: #2563eb;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-danger {
  background: #dc2626;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.875rem;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: var(--surface, #ffffff);
  border-radius: 8px;
  width: 100%;
  max-width: 500px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
  color: var(--text-primary, #111827);
}

.modal-header h3 {
  color: var(--text-primary, #111827);
  margin: 0;
}

.modal-header {
  padding: 1rem;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-body {
  padding: 1.5rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #374151;
}

.form-group input, .form-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: var(--surface, #ffffff);
  color: var(--text-primary, #111827);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1.5rem;
}

.btn-secondary {
  background: #9ca3af;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #6b7280;
}
</style>
