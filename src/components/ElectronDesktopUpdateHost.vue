<script setup>
import { watch } from 'vue'
import brandLogo from '../assets/icon.png'
import { useElectronDesktopUpdate } from '../composables/useElectronDesktopUpdate.js'

const props = defineProps({
  enabled: {
    type: Boolean,
    default: false,
  },
})

const {
  autoCheckOnStartup,
  updateVisible,
  cleanupVisible,
  updateInfo,
  downloadProgress,
  downloading,
  installMode,
  installDir,
  cleanupInfo,
  startDownload,
  dismissUpdate,
  confirmCleanup,
} = useElectronDesktopUpdate()

const changelogPreviewCount = 4

watch(
  () => props.enabled,
  (ready) => {
    if (ready) autoCheckOnStartup()
  },
  { immediate: true }
)
</script>

<template>
  <template v-if="enabled">
    <el-dialog
      v-model="updateVisible"
      class="electron-update-dialog"
      width="560px"
      align-center
      :close-on-click-modal="!downloading"
      :show-close="!downloading"
      destroy-on-close
      @close="dismissUpdate"
    >
      <template #header>
        <div class="dialog-header">
          <img :src="brandLogo" alt="" class="dialog-logo" />
          <div>
            <h3>发现新桌面版</h3>
            <p v-if="updateInfo">
              v{{ updateInfo.localVersion }} → v{{ updateInfo.version }}
              <span v-if="updateInfo.channelLabel"> · {{ updateInfo.channelLabel }}</span>
            </p>
          </div>
        </div>
      </template>

      <div v-if="updateInfo" class="dialog-body">
        <p v-if="updateInfo.buildDate" class="meta">发布时间 {{ updateInfo.buildDate }}</p>

        <div v-if="updateInfo.changelog?.length" class="changelog-panel">
          <div class="panel-head">更新内容</div>
          <ul>
            <li v-for="(item, index) in updateInfo.changelog.slice(0, changelogPreviewCount)" :key="index">
              {{ item }}
            </li>
          </ul>
        </div>

        <div class="install-mode-block">
          <div class="panel-head">安装方式</div>
          <el-radio-group v-model="installMode" :disabled="downloading">
            <el-radio label="update">覆盖更新</el-radio>
            <el-radio label="fresh">全新安装</el-radio>
          </el-radio-group>
          <p v-if="installDir" class="install-dir">当前目录：{{ installDir }}</p>
        </div>

        <div v-if="downloadProgress" class="progress-block">
          <el-progress
            :percentage="downloadProgress.percent ?? 0"
            :status="downloadProgress.status === 'error' ? 'exception' : downloadProgress.status === 'done' ? 'success' : undefined"
            :stroke-width="10"
            striped
            striped-flow
          />
          <p>{{ downloadProgress.message }}</p>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="downloading" @click="dismissUpdate">稍后</el-button>
        <el-button type="primary" :loading="downloading" @click="startDownload">
          {{ downloading ? '下载中…' : '下载更新' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="cleanupVisible"
      title="更新完成"
      width="460px"
      align-center
      :close-on-click-modal="false"
      :show-close="false"
    >
      <p>
        桌面版已更新
        <template v-if="cleanupInfo?.targetVersion">至 v{{ cleanupInfo.targetVersion }}</template>
        。
      </p>
      <p v-if="cleanupInfo?.fileExists" class="cleanup-tip">
        点击确认后将自动删除下载的安装包，释放磁盘空间。
      </p>
      <template #footer>
        <el-button type="primary" @click="confirmCleanup">确认</el-button>
      </template>
    </el-dialog>
  </template>
</template>

<style scoped>
.dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
}

.dialog-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.dialog-logo {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.meta {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
}

.changelog-panel,
.install-mode-block,
.progress-block {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8f9fc;
  border: 1px solid #ebeef5;
}

.panel-head {
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}

.changelog-panel ul {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.install-dir,
.cleanup-tip {
  margin: 8px 0 0;
  color: #909399;
  font-size: 12px;
  word-break: break-all;
}

.progress-block p {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
}
</style>
