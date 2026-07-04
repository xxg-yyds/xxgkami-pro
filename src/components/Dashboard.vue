<template>
  <div class="dashboard" :class="{ 'dashboard-sidebar-collapsed': sidebarCollapsed }">
    <!-- 导航栏组件 -->
    <NavigationBar 
      :user-info="userInfo"
      :active-tab="activeTab"
      @tab-change="handleTabChange"
      @logout="handleLogout"
      @collapse-change="handleSidebarCollapse"
    />

    <!-- 创建完成：一键复制卡密 -->
    <CreatedKeysDialog
      :visible="createdKeysDialog.visible"
      :keys="createdKeysDialog.keys"
      @close="createdKeysDialog.visible = false"
    />

    <!-- 主要内容区域 -->
    <main class="dashboard-main">
      <!-- 概览页面 -->
      <OverviewPage 
        v-if="activeTab === 'overview'"
        :key="`page-${tabRefreshKey}`"
        :stats="stats"
        :carousel-data="carouselData"
        :features="features"
        @prev-slide="prevSlide"
        @next-slide="nextSlide"
        @slide-change="handleSlideChange"
      />

      <!-- 卡密管理页面 -->
      <KeysManagePage 
        v-if="activeTab === 'keys'"
        :key="`page-${tabRefreshKey}`"
        :keys="keys"
        @create-keys="handleCreateKeys"
        @import-keys="handleImportKeys"
        @delete-key="handleDeleteKey"
        @batch-delete-keys="handleBatchDeleteKeys"
        @batch-unbind-keys="handleBatchUnbindKeys"
        @batch-adjust-keys="handleBatchAdjustKeys"
        @toggle-key-status="handleToggleKeyStatus"
        @update-key="handleUpdateKey"
      />

      <!-- 定价管理页面 -->
      <PricingManagePage 
        v-if="activeTab === 'pricing'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 订单管理页面 -->
      <OrdersManagePage 
        v-if="activeTab === 'orders'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- API管理页面 -->
      <ApiManagePage 
        v-if="activeTab === 'api'"
        :key="`page-${tabRefreshKey}`"
        :api-keys="apiKeys"
        @generate-api-key="handleGenerateApiKey"
        @delete-api-key="handleDeleteApiKey"
        @update-api-key="handleUpdateApiKey"
        @toggle-api-key="handleToggleApiKey"
      />

      <!-- API 开放中心 -->
      <ApiOpenCenterPage
        v-if="activeTab === 'api_open'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 用户管理页面 -->
      <UserManagePage 
        v-if="activeTab === 'users'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 通知管理页面 -->
      <NotificationPage 
        v-if="activeTab === 'notification'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 系统设置页面 -->
      <SettingsPage 
        v-if="activeTab === 'settings'"
        :key="`page-${tabRefreshKey}`"
        :user-info="userInfo"
        @save-settings="handleSaveSettings"
        @clear-cache="handleClearCache"
        @optimize-database="handleOptimizeDatabase"
        @clear-logs="handleClearLogs"
        @create-backup="handleCreateBackup"
      />

      <!-- 系统维护页面 -->
      <MaintenanceAdmin 
        v-if="activeTab === 'maintenance'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 系统信息页面 -->
      <SystemInfo 
        v-if="activeTab === 'system_info'"
        :key="`page-${tabRefreshKey}`"
      />

      <!-- 管理员管理 -->
      <AdminManagePage
        v-if="activeTab === 'admins'"
        :key="`page-${tabRefreshKey}`"
        :user-info="userInfo"
      />

      <!-- 操作日志 -->
      <AdminLogPage
        v-if="activeTab === 'logs'"
        :key="`page-${tabRefreshKey}`"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { mockAdmins, mockUsers, mockCards, mockApiKeys, mockSettings, mockSlides, mockFeatures, mockDeleteKey } from '../data/mockData.js'
import { cardApi, statsApi } from '../services/api.js'
import { ElMessage } from 'element-plus'
import NavigationBar from './NavigationBar.vue'
import OverviewPage from './OverviewPage.vue'
import KeysManagePage from './KeysManagePage.vue'
import PricingManagePage from './PricingManagePage.vue'
import OrdersManagePage from './OrdersManagePage.vue'
import ApiManagePage from './ApiManagePage.vue'
import ApiOpenCenterPage from './ApiOpenCenterPage.vue'
import UserManagePage from './UserManagePage.vue'
import SettingsPage from './SettingsPage.vue'
import NotificationPage from './NotificationPage.vue'
import MaintenanceAdmin from './MaintenanceAdmin.vue'
import SystemInfo from './SystemInfo.vue'
import AdminManagePage from './AdminManagePage.vue'
import AdminLogPage from './AdminLogPage.vue'
import CreatedKeysDialog from './CreatedKeysDialog.vue'
import { hasAdminPermission, firstAllowedTab } from '../utils/adminPermission.js'

const props = defineProps({
  userInfo: Object
})

const emit = defineEmits(['logout'])

// 响应式数据
const activeTab = ref('overview')
const tabRefreshKey = ref(0)
/** 与 NavigationBar 折叠状态同步，用于主区域 padding 动画 */
const sidebarCollapsed = ref(false)

const handleSidebarCollapse = (collapsed) => {
  sidebarCollapsed.value = collapsed
}
const currentSlide = ref(0)

const stats = reactive({
  totalKeys: 0,
  encryptedKeys: 0,
  simpleKeys: 0,
  usedKeys: 0,
  activeKeys: 0,
  totalUsers: 0
})

const carouselData = ref([])
const features = ref([])
const keys = ref([])
const apiKeys = ref([])

const createdKeysDialog = reactive({
  visible: false,
  keys: []
})

const extractCardKeysFromResult = (result) => {
  if (!result?.success || !Array.isArray(result.data)) return []
  return result.data.map((c) => c.card_key).filter(Boolean)
}

const openCreatedKeysDialog = (keysList) => {
  if (!keysList?.length) return
  createdKeysDialog.keys = keysList
  createdKeysDialog.visible = true
}

// 创建模拟数据对象
const mockData = {
  users: mockUsers,
  admins: mockAdmins,
  keys: mockCards,
  apiKeys: mockApiKeys,
  carouselData: mockSlides,
  features: mockFeatures
}

// 方法
const handleLogout = () => {
  emit('logout')
}

const refreshPageData = async (tab) => {
  if (tab === 'overview') {
    await loadDashboardStats()
  } else if (tab === 'keys') {
    await loadKeys()
  }
}

const handleTabChange = async (tab, section) => {
  if (!hasAdminPermission(props.userInfo, tab)) {
    activeTab.value = firstAllowedTab(props.userInfo)
    ElMessage.warning('无权限访问该页面')
    return
  }
  activeTab.value = tab
  tabRefreshKey.value += 1

  await refreshPageData(tab)
  await nextTick()

  if (section && tab === 'settings') {
    setTimeout(() => {
      const element = document.getElementById('settings-' + section)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    }, 100)
  }
}

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleString('zh-CN')
}

const prevSlide = () => {
  currentSlide.value = currentSlide.value === 0 ? carouselData.value.length - 1 : currentSlide.value - 1
}

const nextSlide = () => {
  currentSlide.value = currentSlide.value === carouselData.value.length - 1 ? 0 : currentSlide.value + 1
}

const handleSlideChange = (index) => {
  currentSlide.value = index
}

const handleCreateKeys = async (keyData) => {
  const totalCount = keyData.count || 1
  let loadingMsg = null
  if (totalCount > 3) {
    loadingMsg = ElMessage({
      message: `正在创建 ${totalCount} 条卡密...`,
      duration: 0,
      type: 'info'
    })
  }
  try {
    const result = await cardApi.createCards(keyData)
    if (result.success) {
      await loadKeys()
      const created = extractCardKeysFromResult(result)
      ElMessage.success(`成功创建 ${created.length || totalCount} 条卡密`)
      openCreatedKeysDialog(created)
    } else {
      ElMessage.error(result.message || '生成卡密失败')
    }
  } catch (error) {
    console.error('生成卡密失败:', error)
    ElMessage.error('生成卡密失败: ' + (error.message || '未知错误'))
  } finally {
    loadingMsg?.close()
  }
}

const handleImportKeys = async (keyData) => {
  const totalCount = keyData.import_items?.length || keyData.manual_card_keys?.length || keyData.count || 0
  let loadingMsg = null
  if (totalCount > 3) {
    loadingMsg = ElMessage({
      message: `正在导入 ${totalCount} 条卡密...`,
      duration: 0,
      type: 'info'
    })
  }
  try {
    const result = await cardApi.importCards(keyData)
    if (result.success) {
      await loadKeys()
      const created = extractCardKeysFromResult(result)
      ElMessage.success(result.message || `成功导入 ${created.length || totalCount} 条卡密`)
      openCreatedKeysDialog(created)
    } else {
      ElMessage.error(result.message || '导入失败')
    }
  } catch (error) {
    ElMessage.error('导入失败: ' + (error.message || '未知错误'))
  } finally {
    loadingMsg?.close()
  }
}

const handleDeleteKey = async ({ id, storage_type }) => {
  try {
    const result = await cardApi.deleteCard(id, storage_type || 'encrypted')
    if (result.success) {
      // 重新加载卡密数据
      await loadKeys()
      alert(result.message)
    } else {
      alert(result.message)
    }
  } catch (error) {
    console.error('删除卡密失败:', error)
    alert('删除卡密失败')
  }
}

const handleBatchDeleteKeys = async (payload) => {
  const ids = Array.isArray(payload) ? payload : payload?.ids
  const storageTypes = Array.isArray(payload) ? null : payload?.storageTypes
  if (!ids?.length) return
  try {
    const result = await cardApi.batchDeleteCards(ids, storageTypes)
    if (result.success) {
      ElMessage.success(result.message || `已删除 ${result.deleted ?? ids.length} 条卡密`)
      await loadKeys()
    } else {
      ElMessage.error(result.message || '批量删除失败')
    }
  } catch (error) {
    console.error('批量删除卡密失败:', error)
    ElMessage.error(error.message || '批量删除卡密失败')
  }
}

const handleBatchUnbindKeys = async (payload) => {
  try {
    const result = await cardApi.batchUnbindCards(payload)
    if (result.success) {
      ElMessage.success(result.message || `已解绑 ${result.unbound ?? 0} 条卡密`)
      await loadKeys()
    } else {
      ElMessage.error(result.message || '批量解绑失败')
    }
  } catch (error) {
    console.error('批量解绑失败:', error)
    ElMessage.error(error.message || '批量解绑失败')
  }
}

const handleBatchAdjustKeys = async (payload) => {
  try {
    const result = await cardApi.batchAdjustCards(payload)
    if (result.success) {
      ElMessage.success(result.message || '批量加扣时完成')
      await loadKeys()
    } else {
      ElMessage.error(result.message || '批量加扣时失败')
    }
  } catch (error) {
    console.error('批量加扣时失败:', error)
    ElMessage.error(error.message || '批量加扣时失败')
  }
}

const handleUpdateKey = async (keyData) => {
  try {
    const result = await cardApi.updateCard(keyData.id, keyData)
    if (result.success) {
      ElMessage.success(result.message || '卡密更新成功')
      await loadKeys()
    } else {
      ElMessage.error(result.message || '更新失败')
    }
  } catch (error) {
    console.error('更新卡密失败:', error)
    ElMessage.error(error.message || '更新卡密失败')
  }
}

const handleToggleKeyStatus = async ({ id, status, storage_type }) => {
  try {
    const result = await cardApi.updateAdminStatus(id, status, storage_type || 'encrypted')
    if (result.success) {
      const msg = result.message || '操作成功'
      if (status === 2 && msg.includes('停止使用')) {
        ElMessage.warning(msg)
      } else {
        ElMessage.success(msg)
      }
      await loadKeys()
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  } catch (error) {
    console.error('更新卡密状态失败:', error)
    ElMessage.error(error.message || '更新卡密状态失败')
  }
}

// 加载统计数据
const loadDashboardStats = async () => {
  try {
    const result = await statsApi.getDashboardStats()
    if (result) {
      stats.totalKeys = result.totalKeys
      stats.encryptedKeys = result.encryptedKeys ?? 0
      stats.simpleKeys = result.simpleKeys ?? 0
      stats.usedKeys = result.usedKeys
      stats.activeKeys = result.activeKeys
      stats.totalUsers = result.totalUsers
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 加载卡密数据
const loadKeys = async () => {
  try {
    const result = await cardApi.getAllCards()
    if (result.success) {
      keys.value = result.data
    }
    // 加载统计数据
    await loadDashboardStats()
  } catch (error) {
    console.error('加载卡密数据失败:', error)
  }
}

const handleGenerateApiKey = () => {
  const apiKey = 'ak_' + Math.random().toString(36).substr(2, 32)
  apiKeys.value.push({
    id: Date.now(),
    name: `API密钥 ${apiKeys.value.length + 1}`,
    key: apiKey,
    createdAt: Date.now(),
    lastUsed: null,
    isActive: true,
    permissions: {
      read: true,
      write: false,
      delete: false
    }
  })
}

const handleDeleteApiKey = (keyId) => {
  apiKeys.value = apiKeys.value.filter(key => key.id !== keyId)
}

const handleUpdateApiKey = (updatedKey) => {
  const index = apiKeys.value.findIndex(key => key.id === updatedKey.id)
  if (index !== -1) {
    apiKeys.value[index] = { ...apiKeys.value[index], ...updatedKey }
  }
}

const handleToggleApiKey = (keyId) => {
  const key = apiKeys.value.find(key => key.id === keyId)
  if (key) {
    key.isActive = !key.isActive
  }
}

const handleSaveSettings = (settingsData) => {
  console.log('保存设置:', settingsData)
  // 这里可以添加保存设置的逻辑
}

const handleClearCache = () => {
  console.log('清理缓存')
  // 这里可以添加清理缓存的逻辑
}

const handleOptimizeDatabase = () => {
  console.log('优化数据库')
  // 这里可以添加优化数据库的逻辑
}

const handleClearLogs = () => {
  console.log('清理日志')
  // 这里可以添加清理日志的逻辑
}

const handleCreateBackup = () => {
  console.log('创建备份')
  // 这里可以添加创建备份的逻辑
}


// 初始化数据
onMounted(async () => {
  if (!hasAdminPermission(props.userInfo, activeTab.value)) {
    activeTab.value = firstAllowedTab(props.userInfo)
  }
  carouselData.value = mockData.carouselData
  features.value = mockData.features
  apiKeys.value = mockData.apiKeys
  
  // 异步加载卡密数据
  await loadKeys()
  
  // 自动轮播
  setInterval(() => {
    nextSlide()
  }, 5000)
})
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f9fafb;
  width: 100%;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: row;
  padding-left: 220px;
  transition: padding-left 0.3s ease;
  box-sizing: border-box;
}

.dashboard.dashboard-sidebar-collapsed {
  padding-left: 64px;
}

.dashboard-main {
  padding: 1rem 1.25rem;
  max-width: 100%;
  margin: 0;
  width: 100%;
  box-sizing: border-box;
  flex: 1;
  min-width: 0;
  overflow-x: hidden;
}

@media (max-width: 768px) {
  .dashboard,
  .dashboard.dashboard-sidebar-collapsed {
    padding-left: 64px;
  }

  .dashboard-main {
    padding: 1rem;
  }
}

/* 创建进度条 */
.create-progress-bar {
  position: fixed;
  top: 0;
  left: 220px;
  right: 0;
  z-index: 999;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  animation: slideDown 0.3s ease;
  transition: left 0.3s ease;
}

.dashboard.dashboard-sidebar-collapsed .create-progress-bar {
  left: 64px;
}

@keyframes slideDown {
  from { transform: translateY(-100%); opacity: 0; }
  to   { transform: translateY(0); opacity: 1; }
}

@media (max-width: 768px) {
  .create-progress-bar {
    left: 64px;
  }
}

.progress-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 12px 2rem;
  position: relative;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.progress-icon {
  display: flex;
  align-items: center;
  color: #2563eb;
}

.spinning {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.progress-text {
  font-size: 0.85rem;
  font-weight: 500;
  color: #374151;
}

.progress-track {
  width: 100%;
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #0ea5e9);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-close {
  position: absolute;
  top: 8px;
  right: 2rem;
  background: none;
  border: none;
  font-size: 1.25rem;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px 8px;
  line-height: 1;
  border-radius: 4px;
}

.progress-close:hover {
  background: #f3f4f6;
  color: #374151;
}
</style>