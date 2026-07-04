<template>
  <div class="overview-page">
    <!-- 服务器物理位置 -->
    <div class="server-location-card">
      <div class="server-location-header">
        <div class="server-location-title">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>
          <h3>服务器物理位置</h3>
        </div>
        <button
          type="button"
          class="location-refresh-btn"
          :disabled="serverLocation.loading"
          @click="loadServerLocation"
        >
          {{ serverLocation.loading ? '查询中…' : '重新查询' }}
        </button>
      </div>

      <div class="server-location-ip-row">
        <div class="ip-chip">
          <span class="ip-label">公网 IP</span>
          <strong>{{ serverLocation.publicIp || '—' }}</strong>
        </div>
        <div class="ip-chip">
          <span class="ip-label">内网 IP</span>
          <strong>{{ serverLocation.localIp || '—' }}</strong>
        </div>
        <div v-if="serverLocation.checkedAt" class="ip-chip ip-chip-muted">
          <span class="ip-label">更新时间</span>
          <strong>{{ serverLocation.checkedAt }}</strong>
        </div>
      </div>

      <div class="location-source-toggle">
        <button
          type="button"
          class="location-source-btn"
          :class="{ active: locationSource === 'domestic' }"
          @click="locationSource = 'domestic'"
        >
          国内查询
        </button>
        <button
          type="button"
          class="location-source-btn"
          :class="{ active: locationSource === 'international' }"
          @click="locationSource = 'international'"
        >
          国外查询
        </button>
      </div>

      <div class="location-result-panel" :class="{ 'is-error': !currentLocationResult.success && !serverLocation.loading }">
        <p v-if="serverLocation.loading" class="location-result-text">正在获取 IP 并查询物理地址…</p>
        <template v-else>
          <p class="location-provider">{{ currentLocationResult.provider || '—' }}</p>
          <p class="location-result-text">{{ currentLocationDisplay }}</p>
          <p v-if="currentLocationResult.isp" class="location-isp">运营商：{{ currentLocationResult.isp }}</p>
        </template>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div
        class="stat-card-wrap"
        @mouseenter="showTotalBreakdown = true"
        @mouseleave="showTotalBreakdown = false"
      >
        <div class="stat-card stat-card--hoverable">
          <div class="stat-icon icon-blue">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6"/><path d="m15.5 7.5 3 3L22 7l-3-3"/></svg>
          </div>
          <div class="stat-info">
            <h3>{{ stats.totalKeys }}</h3>
            <p>总卡密数量</p>
          </div>
        </div>
        <Transition name="stat-pop">
          <div v-show="showTotalBreakdown" class="stat-breakdown-card">
            <div class="breakdown-row">
              <span class="breakdown-label">
                <span class="breakdown-dot simple"></span>
                普通卡密
              </span>
              <strong>{{ stats.simpleKeys ?? 0 }}</strong>
            </div>
            <div class="breakdown-row">
              <span class="breakdown-label">
                <span class="breakdown-dot encrypted"></span>
                加密卡密
              </span>
              <strong>{{ stats.encryptedKeys ?? 0 }}</strong>
            </div>
          </div>
        </Transition>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-green">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>
        </div>
        <div class="stat-info">
          <h3>{{ stats.usedKeys }}</h3>
          <p>已使用卡密</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-purple">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>
        </div>
        <div class="stat-info">
          <h3>{{ stats.activeKeys }}</h3>
          <p>有效卡密</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-orange">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        </div>
        <div class="stat-info">
          <h3>{{ stats.totalUsers }}</h3>
          <p>用户总数</p>
        </div>
      </div>
    </div>

    <!-- 卡密使用趋势 -->
    <div class="charts-section">
      <div class="chart-card chart-card-full">
        <div class="chart-header">
          <h3>卡密使用趋势</h3>
          <div class="chart-controls">
            <select v-model="chartPeriod" @change="updateChartData">
              <option value="7">最近7天</option>
              <option value="30">最近30天</option>
              <option value="90">最近90天</option>
            </select>
          </div>
        </div>
        <div class="chart-container" ref="usageChartRef">
          <p v-if="chartLoading" class="section-loading-hint">图表数据加载中…</p>
        </div>
      </div>
    </div>

    <!-- 服务器状态监控 -->
    <div class="server-status-section">
      <div class="section-header">
        <div class="section-header-left">
          <h2>服务器状态监控</h2>
          <span class="last-check" v-if="systemStatus.lastCheck">更新于 {{ systemStatus.lastCheck }}</span>
        </div>
        <div class="view-toggle">
          <button
            type="button"
            class="view-toggle-btn"
            :class="{ active: statusViewMode === 'detail' }"
            @click="setStatusView('detail')"
          >
            详情视图
          </button>
          <button
            type="button"
            class="view-toggle-btn"
            :class="{ active: statusViewMode === 'ring' }"
            @click="setStatusView('ring')"
          >
            圆环视图
          </button>
        </div>
      </div>

      <!-- 圆环视图 -->
      <div v-show="statusViewMode === 'ring'" class="ring-panel status-card">
        <p v-if="systemStatus.loading" class="section-loading-hint section-loading-hint--block">服务器状态加载中…</p>
        <template v-else>
        <div class="ring-grid">
          <div class="ring-item">
            <div ref="ringLoadRef" class="ring-chart"></div>
            <p class="ring-subtitle">{{ loadStatusText }}</p>
            <p class="ring-label">负载</p>
          </div>
          <div class="ring-item">
            <div ref="ringCpuRef" class="ring-chart"></div>
            <p class="ring-subtitle">{{ systemStatus.availableProcessors }} 核心</p>
            <p class="ring-label">CPU</p>
          </div>
          <div class="ring-item">
            <div ref="ringMemoryRef" class="ring-chart"></div>
            <p class="ring-subtitle ring-subtitle-mem">
              <span class="ring-highlight">{{ systemStatus.memory.used }}</span>
              <span> / {{ systemStatus.memory.total }}</span>
            </p>
            <p class="ring-label">内存</p>
          </div>
          <div class="ring-item">
            <div ref="ringDiskRef" class="ring-chart"></div>
            <p class="ring-subtitle ring-subtitle-mem">
              <span class="ring-highlight">{{ systemStatus.disk.used }}</span>
              <span> / {{ systemStatus.disk.total }}</span>
            </p>
            <p class="ring-label">磁盘</p>
          </div>
        </div>
        <div class="ring-footer">
          <span>运行时长 {{ systemStatus.uptime }}</span>
          <span>进程 CPU {{ systemStatus.processCpuUsage }}%</span>
          <span class="ring-health" :class="healthClass">状态 {{ healthLabel }}</span>
        </div>
        </template>
      </div>

      <!-- 详情视图 -->
      <div v-show="statusViewMode === 'detail'" class="status-grid">
        <p v-if="systemStatus.loading" class="section-loading-hint section-loading-hint--block">服务器状态加载中…</p>
        <template v-else>
        <!-- 运行负载 -->
        <div class="status-card">
          <div class="status-header">
            <h3>运行负载</h3>
            <div class="status-indicator" :class="healthClass">
              <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"></circle></svg>
            </div>
          </div>
          <div class="load-summary">
            <div class="load-main">
              <span class="load-value">{{ systemStatus.load.load1 }}</span>
              <span class="load-label">1 分钟负载</span>
            </div>
            <span class="load-level" :class="'level-' + loadLevelClass">{{ systemStatus.load.level }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">负载率</span>
            <div class="metric-bar">
              <div class="metric-fill load-fill" :style="{ width: systemStatus.load.loadPercent + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.load.loadPercent }}%</span>
          </div>
          <div class="status-info compact">
            <div class="info-item">
              <span class="info-label">5 分钟</span>
              <span class="info-value sm">{{ systemStatus.load.load5 }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">15 分钟</span>
              <span class="info-value sm">{{ systemStatus.load.load15 }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">CPU 核心</span>
              <span class="info-value sm">{{ systemStatus.availableProcessors }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">运行时长</span>
              <span class="info-value sm">{{ systemStatus.uptime }}</span>
            </div>
          </div>
        </div>

        <!-- CPU 核心负载 -->
        <div class="status-card">
          <div class="status-header">
            <h3>CPU 核心负载</h3>
            <span class="header-extra">整体 {{ systemStatus.cpuUsage }}%</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">系统 CPU</span>
            <div class="metric-bar">
              <div class="metric-fill cpu-fill" :style="{ width: systemStatus.cpuUsage + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.cpuUsage }}%</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">进程 CPU</span>
            <div class="metric-bar">
              <div class="metric-fill process-fill" :style="{ width: systemStatus.processCpuUsage + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.processCpuUsage }}%</span>
          </div>
          <div class="cores-grid" v-if="systemStatus.cpuCores.length">
            <div class="core-item" v-for="core in systemStatus.cpuCores" :key="core.index">
              <span class="core-label">{{ core.label }}</span>
              <div class="core-bar">
                <div class="core-fill" :style="{ width: core.usage + '%' }"></div>
              </div>
              <span class="core-value">{{ core.usage }}%</span>
            </div>
          </div>
          <p class="hint-text" v-else>暂无核心数据</p>
        </div>

        <!-- 内存 -->
        <div class="status-card">
          <div class="status-header">
            <h3>内存</h3>
            <span class="header-extra">{{ systemStatus.memory.usagePercent }}%</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">物理内存</span>
            <div class="metric-bar">
              <div class="metric-fill memory-fill" :style="{ width: systemStatus.memory.usagePercent + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.memory.usagePercent }}%</span>
          </div>
          <div class="status-info compact">
            <div class="info-item">
              <span class="info-label">已用</span>
              <span class="info-value sm">{{ systemStatus.memory.used }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">总量</span>
              <span class="info-value sm">{{ systemStatus.memory.total }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">可用</span>
              <span class="info-value sm">{{ systemStatus.memory.free }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">JVM 堆</span>
              <span class="info-value sm">{{ systemStatus.memory.jvmUsed }} / {{ systemStatus.memory.jvmMax }}</span>
            </div>
          </div>
          <div class="metric-item sub-metric">
            <span class="metric-label">JVM 堆占用</span>
            <div class="metric-bar">
              <div class="metric-fill jvm-fill" :style="{ width: systemStatus.memory.jvmUsagePercent + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.memory.jvmUsagePercent }}%</span>
          </div>
        </div>

        <!-- 磁盘 -->
        <div class="status-card">
          <div class="status-header">
            <h3>磁盘</h3>
            <span class="header-extra">{{ systemStatus.disk.usagePercent }}%</span>
          </div>
          <div class="metric-item">
            <span class="metric-label">磁盘占用</span>
            <div class="metric-bar">
              <div class="metric-fill disk-fill" :style="{ width: systemStatus.disk.usagePercent + '%' }"></div>
            </div>
            <span class="metric-value">{{ systemStatus.disk.usagePercent }}%</span>
          </div>
          <div class="status-info compact">
            <div class="info-item">
              <span class="info-label">已用</span>
              <span class="info-value sm">{{ systemStatus.disk.used }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">总量</span>
              <span class="info-value sm">{{ systemStatus.disk.total }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">可用</span>
              <span class="info-value sm">{{ systemStatus.disk.free }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">路径</span>
              <span class="info-value sm path-value">{{ systemStatus.disk.path }}</span>
            </div>
          </div>
        </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { monitorApi, statsApi } from '../services/api.js'

const props = defineProps({
  stats: Object
})

const showTotalBreakdown = ref(false)
const locationSource = ref('domestic')
const serverLocation = ref({
  loading: true,
  publicIp: '',
  localIp: '',
  queryIp: '',
  checkedAt: '',
  domestic: null,
  international: null,
})

const chartLoading = ref(true)

const currentLocationResult = computed(() => {
  if (locationSource.value === 'international') {
    return serverLocation.value.international || { success: false, provider: 'ipwho.is' }
  }
  return serverLocation.value.domestic || { success: false, provider: '太平洋网络 IP 库' }
})

const currentLocationDisplay = computed(() => {
  const result = currentLocationResult.value
  if (result.success) {
    return result.location || '未知位置'
  }
  return result.message || '查询失败，请稍后重试'
})

let serverLocationFetching = false

const loadServerLocation = async () => {
  if (serverLocationFetching) return
  serverLocationFetching = true
  serverLocation.value.loading = true
  try {
    const data = await monitorApi.getServerLocation()
    serverLocation.value = {
      loading: false,
      publicIp: data.publicIp || '',
      localIp: data.localIp || '',
      queryIp: data.queryIp || '',
      checkedAt: data.checkedAt || '',
      domestic: data.domestic || null,
      international: data.international || null,
    }
  } catch (error) {
    console.error('加载服务器位置失败:', error)
    serverLocation.value.loading = false
    const message = error?.message?.includes('abort') ? '查询超时，请稍后重试' : '查询失败，请稍后重试'
    serverLocation.value.domestic = { success: false, provider: '太平洋网络 IP 库', message }
    serverLocation.value.international = { success: false, provider: 'ipwho.is', message }
  } finally {
    serverLocationFetching = false
  }
}

const chartPeriod = ref('7')
const usageChartRef = ref(null)
const statusViewMode = ref('detail')
const ringLoadRef = ref(null)
const ringCpuRef = ref(null)
const ringMemoryRef = ref(null)
const ringDiskRef = ref(null)
let usageChartInstance = null
let ringChartInstances = []
let statusInterval = null
let statusLoading = false

const STATUS_REFRESH_MS = 1000

const RING_COLOR = '#7c3aed'
const RING_TRACK = '#e8e8ef'

const SERIES_META = [
  { name: '已使用', color: '#4f46e5', key: 'used' },
  { name: '未使用', color: '#10b981', key: 'unused' },
  { name: '已过期', color: '#f59e0b', key: 'expired' }
]

const defaultLoad = () => ({
  load1: 0,
  load5: 0,
  load15: 0,
  loadPercent: 0,
  level: '低'
})

const defaultMemory = () => ({
  usagePercent: 0,
  used: 'N/A',
  total: 'N/A',
  free: 'N/A',
  jvmUsed: 'N/A',
  jvmMax: 'N/A',
  jvmUsagePercent: 0
})

const defaultDisk = () => ({
  usagePercent: 0,
  used: 'N/A',
  total: 'N/A',
  free: 'N/A',
  path: '-'
})

const systemStatus = ref({
  status: 'loading',
  health: 'healthy',
  cpuUsage: 0,
  processCpuUsage: 0,
  memoryUsage: 0,
  diskUsage: 0,
  availableProcessors: 0,
  uptime: '-',
  lastCheck: '',
  load: defaultLoad(),
  memory: defaultMemory(),
  disk: defaultDisk(),
  cpuCores: [],
  loading: true
})

const healthClass = computed(() => {
  const h = systemStatus.value.health
  if (h === 'warning') return 'warning'
  if (systemStatus.value.status === 'offline') return 'offline'
  return 'online'
})

const loadLevelClass = computed(() => {
  const level = systemStatus.value.load?.level || '低'
  if (level === '高') return 'high'
  if (level === '中') return 'medium'
  return 'low'
})

const loadStatusText = computed(() => {
  const level = systemStatus.value.load?.level || '低'
  if (level === '高') return '负载较高'
  if (level === '中') return '负载适中'
  return '运行流畅'
})

const healthLabel = computed(() => {
  const h = systemStatus.value.health
  if (h === 'warning') return '需关注'
  if (systemStatus.value.status === 'offline') return '离线'
  return '正常'
})

const buildRingOption = (value, color = RING_COLOR) => {
  const pct = Math.max(0, Math.min(100, Number(value) || 0))
  return {
    animationDuration: 600,
    series: [{
      type: 'gauge',
      radius: '92%',
      center: ['50%', '50%'],
      startAngle: 90,
      endAngle: -270,
      min: 0,
      max: 100,
      pointer: { show: false },
      progress: {
        show: true,
        width: 11,
        roundCap: true,
        itemStyle: { color }
      },
      axisLine: {
        lineStyle: {
          width: 11,
          color: [[1, RING_TRACK]]
        }
      },
      splitLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      title: { show: false },
      detail: {
        valueAnimation: true,
        fontSize: 24,
        fontWeight: 700,
        color: RING_COLOR,
        offsetCenter: [0, 0],
        formatter: `${Math.round(pct)}%`
      },
      data: [{ value: pct }]
    }]
  }
}

const getRingChartRefs = () => [
  ringLoadRef.value,
  ringCpuRef.value,
  ringMemoryRef.value,
  ringDiskRef.value
]

const getRingValues = () => [
  systemStatus.value.load.loadPercent,
  systemStatus.value.cpuUsage,
  systemStatus.value.memory.usagePercent,
  systemStatus.value.disk.usagePercent
]

const disposeRingCharts = () => {
  ringChartInstances.forEach((chart) => chart?.dispose())
  ringChartInstances = []
}

const initRingCharts = () => {
  disposeRingCharts()
  const refs = getRingChartRefs()
  const values = getRingValues()
  refs.forEach((el, index) => {
    if (!el) return
    const chart = echarts.init(el)
    chart.setOption(buildRingOption(values[index]))
    ringChartInstances.push(chart)
  })
}

const updateRingCharts = () => {
  const values = getRingValues()
  if (!ringChartInstances.length) {
    if (statusViewMode.value === 'ring') {
      nextTick(() => initRingCharts())
    }
    return
  }
  ringChartInstances.forEach((chart, index) => {
    if (chart) {
      chart.setOption(buildRingOption(values[index]), { notMerge: true })
    }
  })
}

const resizeRingCharts = () => {
  ringChartInstances.forEach((chart) => chart?.resize())
}

const setStatusView = (mode) => {
  statusViewMode.value = mode
  if (mode === 'ring') {
    nextTick(() => initRingCharts())
  }
}

const chartData = ref({
  dates: [],
  used: [],
  unused: [],
  expired: []
})

const buildChartOption = () => {
  const labels = chartData.value.dates.map((date) => (date ? date.substring(5) : ''))

  return {
    color: SERIES_META.map((item) => item.color),
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: SERIES_META.map((item) => item.name),
      top: 0,
      selectedMode: true
    },
    grid: {
      left: 48,
      right: 24,
      top: 48,
      bottom: 32
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af' }
    },
    series: SERIES_META.map((item) => ({
      name: item.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' },
      data: chartData.value[item.key] || []
    }))
  }
}

const initUsageChart = () => {
  if (!usageChartRef.value) return

  if (usageChartInstance) {
    usageChartInstance.dispose()
  }

  usageChartInstance = echarts.init(usageChartRef.value)
  usageChartInstance.setOption(buildChartOption())
}

const updateUsageChart = () => {
  if (!usageChartInstance) {
    initUsageChart()
    return
  }
  usageChartInstance.setOption(buildChartOption(), { notMerge: true })
}

const resizeUsageChart = () => {
  usageChartInstance?.resize()
  resizeRingCharts()
}

const loadChartData = async () => {
  chartLoading.value = true
  try {
    const usageData = await statsApi.getCardUsageTrends(chartPeriod.value)

    if (usageData?.dates) {
      chartData.value = {
        dates: usageData.dates || [],
        used: usageData.used || usageData.counts || [],
        unused: usageData.unused || [],
        expired: usageData.expired || []
      }
    }
  } catch (error) {
    console.error('加载图表数据失败:', error)
  } finally {
    chartLoading.value = false
  }
}

const updateChartData = async () => {
  await loadChartData()
  nextTick(() => {
    updateUsageChart()
  })
}

const loadServerStatus = async () => {
  if (statusLoading) return
  statusLoading = true
  try {
    const systemData = await monitorApi.getSystemStatus()

    systemStatus.value = {
      status: systemData.status || 'offline',
      health: systemData.health || 'healthy',
      cpuUsage: systemData.cpuUsage || 0,
      processCpuUsage: systemData.processCpuUsage || 0,
      memoryUsage: systemData.memoryUsage || 0,
      diskUsage: systemData.diskUsage || 0,
      availableProcessors: systemData.availableProcessors || 0,
      uptime: systemData.uptime || '-',
      lastCheck: systemData.lastCheck || '',
      load: { ...defaultLoad(), ...(systemData.load || {}) },
      memory: { ...defaultMemory(), ...(systemData.memory || {}) },
      disk: { ...defaultDisk(), ...(systemData.disk || {}) },
      cpuCores: systemData.cpuCores || [],
      loading: false
    }
    updateRingCharts()
  } catch (error) {
    console.error('加载服务器状态失败:', error)
    systemStatus.value.loading = false
    systemStatus.value.status = 'offline'
    systemStatus.value.health = 'warning'
  } finally {
    statusLoading = false
  }
}

onMounted(() => {
  // 各区域独立加载，互不阻塞
  loadServerLocation()

  loadChartData().then(() => {
    nextTick(() => initUsageChart())
  })

  loadServerStatus()

  window.addEventListener('resize', resizeUsageChart)

  statusInterval = setInterval(loadServerStatus, STATUS_REFRESH_MS)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeUsageChart)
  if (usageChartInstance) {
    usageChartInstance.dispose()
    usageChartInstance = null
  }
  disposeRingCharts()
  if (statusInterval) {
    clearInterval(statusInterval)
  }
})
</script>

<style scoped>
.overview-page {
  padding: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-x: auto;
}

.server-location-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: var(--card-radius);
  padding: 1.25rem 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.server-location-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.server-location-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #111827;
}

.server-location-title h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
}

.server-location-title svg {
  color: #4f46e5;
  flex-shrink: 0;
}

.location-refresh-btn {
  border: 1px solid #d1d5db;
  background: white;
  color: #374151;
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 0.35rem 0.85rem;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.location-refresh-btn:hover:not(:disabled) {
  border-color: #4f46e5;
  color: #4f46e5;
}

.location-refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.server-location-ip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.ip-chip {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 120px;
  padding: 0.65rem 0.85rem;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.ip-chip-muted strong {
  font-size: 0.8125rem;
  font-weight: 600;
}

.ip-label {
  font-size: 0.75rem;
  color: #64748b;
}

.ip-chip strong {
  font-size: 0.95rem;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.location-source-toggle {
  display: inline-flex;
  gap: 0.35rem;
  padding: 0.2rem;
  background: #f3f4f6;
  border-radius: 999px;
  border: 1px solid #e5e7eb;
  margin-bottom: 0.85rem;
}

.location-source-btn {
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.location-source-btn.active {
  background: white;
  color: #4f46e5;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.location-result-panel {
  padding: 0.9rem 1rem;
  border-radius: 10px;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 100%);
  border: 1px solid #dbeafe;
}

.location-result-panel.is-error {
  background: #fef2f2;
  border-color: #fecaca;
}

.location-provider {
  margin: 0 0 0.35rem;
  font-size: 0.75rem;
  color: #64748b;
}

.location-result-text {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.5;
}

.location-result-panel.is-error .location-result-text {
  color: #b91c1c;
  font-weight: 600;
}

.location-isp {
  margin: 0.35rem 0 0;
  font-size: 0.8125rem;
  color: #475569;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card-wrap {
  position: relative;
  z-index: 1;
}

.stat-card-wrap:hover {
  z-index: 30;
}

.stat-card--hoverable {
  cursor: default;
}

.stat-breakdown-card {
  position: absolute;
  top: calc(100% + 0.5rem);
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: var(--card-radius);
  padding: 0.75rem 1rem;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
}

.stat-breakdown-card::before {
  content: '';
  position: absolute;
  top: -6px;
  left: 1.5rem;
  width: 12px;
  height: 12px;
  background: white;
  border-left: 1px solid #e5e7eb;
  border-top: 1px solid #e5e7eb;
  transform: rotate(45deg);
}

.breakdown-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.4rem 0;
  font-size: 0.875rem;
  color: #374151;
}

.breakdown-row + .breakdown-row {
  border-top: 1px solid #f3f4f6;
  margin-top: 0.25rem;
  padding-top: 0.65rem;
}

.breakdown-label {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.breakdown-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.breakdown-dot.simple {
  background: #10b981;
}

.breakdown-dot.encrypted {
  background: #6366f1;
}

.breakdown-row strong {
  font-size: 1rem;
  font-weight: 700;
  color: #111827;
  font-variant-numeric: tabular-nums;
}

.stat-pop-enter-active,
.stat-pop-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.stat-pop-enter-from,
.stat-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.stat-card {
  background: white;
  border: 1px solid #e5e7eb;
  color: #111827;
  padding: 1.5rem;
  border-radius: var(--card-radius);
  display: flex;
  align-items: center;
  gap: 1.25rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--card-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.icon-blue { background: #3b82f6; }
.icon-green { background: #10b981; }
.icon-purple { background: #8b5cf6; }
.icon-orange { background: #f59e0b; }

.stat-info h3 {
  font-size: 1.8rem;
  margin: 0;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-info p {
  margin: 0;
  color: #6b7280;
  font-size: 0.875rem;
  font-weight: 500;
}

.charts-section {
  margin: 2rem 0;
}

.chart-card {
  background: white;
  border-radius: var(--card-radius);
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid #e5e7eb;
}

.chart-card-full {
  width: 100%;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.chart-header h3 {
  color: #111827;
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.chart-controls select {
  padding: 0.4rem 0.8rem;
  border: 1px solid #d1d5db;
  border-radius: var(--card-radius);
  background: white;
  color: #374151;
  font-size: 0.875rem;
  outline: none;
}

.chart-controls select:focus {
  border-color: #4f46e5;
}

.chart-container {
  height: 320px;
  width: 100%;
  position: relative;
}

.section-loading-hint {
  margin: 0;
  color: #9ca3af;
  font-size: 0.875rem;
  text-align: center;
  padding: 2rem 1rem;
}

.section-loading-hint--block {
  width: 100%;
  padding: 3rem 1rem;
  background: white;
  border-radius: var(--card-radius);
  border: 1px solid #e5e7eb;
  grid-column: 1 / -1;
}

.server-status-section {
  margin: 2rem 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  gap: 1rem;
  flex-wrap: wrap;
}

.section-header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.view-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem;
  background: #f3f4f6;
  border-radius: 999px;
  border: 1px solid #e5e7eb;
}

.view-toggle-btn {
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 0.8rem;
  font-weight: 600;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 1.2;
}

.view-toggle-btn:hover {
  color: #374151;
}

.view-toggle-btn.active {
  background: white;
  color: #7c3aed;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.ring-panel {
  padding: 1.75rem 1.5rem 1.25rem;
}

.ring-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

.ring-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.ring-chart {
  width: 100%;
  height: 150px;
  max-width: 180px;
}

.ring-subtitle {
  margin: 0.35rem 0 0.15rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: #374151;
  line-height: 1.3;
}

.ring-subtitle-mem {
  font-weight: 600;
}

.ring-highlight {
  color: #7c3aed;
  font-weight: 700;
}

.ring-label {
  margin: 0;
  font-size: 0.8rem;
  color: #9ca3af;
}

.ring-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem 1.5rem;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid #f3f4f6;
  font-size: 0.8rem;
  color: #6b7280;
}

.ring-health.online { color: #059669; }
.ring-health.warning { color: #d97706; }
.ring-health.offline { color: #dc2626; }

.section-header h2 {
  color: #111827;
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
}

.last-check {
  font-size: 0.8rem;
  color: #9ca3af;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}

.status-card {
  background: white;
  border-radius: var(--card-radius);
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid #e5e7eb;
  transition: all 0.2s ease;
}

.status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid #f3f4f6;
  padding-bottom: 0.75rem;
}

.status-header h3 {
  color: #111827;
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.header-extra {
  font-size: 0.875rem;
  color: #6b7280;
  font-weight: 600;
}

.status-indicator {
  width: 12px;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-indicator svg {
  width: 10px;
  height: 10px;
}

.status-indicator.online { color: #10b981; }
.status-indicator.offline { color: #ef4444; }
.status-indicator.warning { color: #f59e0b; }

.load-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.load-main {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.load-value {
  font-size: 2rem;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.load-label {
  font-size: 0.8rem;
  color: #6b7280;
}

.load-level {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.load-level.level-low {
  background: #ecfdf5;
  color: #059669;
}

.load-level.level-medium {
  background: #fffbeb;
  color: #d97706;
}

.load-level.level-high {
  background: #fef2f2;
  color: #dc2626;
}

.status-metrics,
.metric-item {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.metric-item {
  margin-bottom: 1rem;
}

.metric-item.sub-metric {
  margin-top: 0.5rem;
  margin-bottom: 0;
}

.metric-label {
  font-size: 0.875rem;
  color: #4b5563;
  min-width: 72px;
  font-weight: 500;
}

.metric-bar {
  flex: 1;
  height: 6px;
  background: #f3f4f6;
  border-radius: 2px;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.load-fill { background: #8b5cf6; }
.cpu-fill { background: #3b82f6; }
.process-fill { background: #6366f1; }
.memory-fill { background: #10b981; }
.jvm-fill { background: #14b8a6; }
.disk-fill { background: #f59e0b; }

.metric-value {
  font-size: 0.875rem;
  color: #111827;
  font-weight: 600;
  min-width: 45px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.cores-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.core-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.core-label {
  font-size: 0.75rem;
  color: #6b7280;
  min-width: 42px;
}

.core-bar {
  flex: 1;
  height: 4px;
  background: #f3f4f6;
  border-radius: 2px;
  overflow: hidden;
}

.core-fill {
  height: 100%;
  background: #3b82f6;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.core-value {
  font-size: 0.75rem;
  color: #374151;
  font-weight: 600;
  min-width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.hint-text {
  font-size: 0.8rem;
  color: #9ca3af;
  margin: 0;
}

.status-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

.status-info.compact {
  gap: 1rem;
  margin-top: 0.25rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.75rem;
  color: #6b7280;
  letter-spacing: 0.02em;
}

.info-value {
  font-size: 1.25rem;
  color: #111827;
  font-weight: 700;
}

.info-value.sm {
  font-size: 0.95rem;
  font-weight: 600;
}

.path-value {
  word-break: break-all;
  font-size: 0.8rem;
}

@media (max-width: 1024px) {
  .status-grid {
    grid-template-columns: 1fr;
  }

  .ring-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .status-info {
    grid-template-columns: 1fr;
  }

  .chart-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .ring-grid {
    grid-template-columns: 1fr 1fr;
  }

  .ring-chart {
    max-width: 160px;
    height: 130px;
  }
}
</style>
