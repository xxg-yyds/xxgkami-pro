<template>
  <div v-if="visible" class="created-keys-overlay" @click="$emit('close')">
    <div class="created-keys-dialog" @click.stop>
      <div class="created-keys-header">
        <h3>卡密创建成功</h3>
        <button type="button" class="close-btn" aria-label="关闭" @click="$emit('close')">×</button>
      </div>
      <p class="created-keys-meta">
        共 <strong>{{ keys.length }}</strong> 条，每行一条，可直接复制到 Excel 或外部系统。
      </p>
      <textarea
        ref="textareaRef"
        readonly
        class="created-keys-text"
        :value="keysText"
        rows="14"
        @focus="selectAll"
      ></textarea>
      <div class="created-keys-actions">
        <button type="button" class="btn-secondary" @click="$emit('close')">关闭</button>
        <button type="button" class="btn-primary" @click="copyAll">
          一键复制全部
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copyToClipboard } from '../utils/clipboard.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  keys: { type: Array, default: () => [] }
})

defineEmits(['close'])

const textareaRef = ref(null)

const keysText = computed(() =>
  (props.keys || []).filter(Boolean).join('\n')
)

const selectAll = () => {
  textareaRef.value?.select()
}

const copyAll = async () => {
  const text = keysText.value
  if (!text) {
    ElMessage.warning('没有可复制的卡密')
    return
  }
  const ok = await copyToClipboard(text)
  if (ok) {
    ElMessage.success(`已复制 ${props.keys.length} 条卡密`)
  } else {
    selectAll()
    ElMessage.warning('自动复制失败，请手动 Ctrl+C 复制文本框内容')
  }
}
</script>

<style scoped>
.created-keys-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 1rem;
  box-sizing: border-box;
}

.created-keys-dialog {
  background: #fff;
  border-radius: 12px;
  width: min(560px, 100%);
  max-height: calc(100vh - 2rem);
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.created-keys-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.25rem 0;
  flex-shrink: 0;
}

.created-keys-header h3 {
  margin: 0;
  font-size: 1.125rem;
  color: #1f2937;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.25rem;
  color: #6b7280;
  cursor: pointer;
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
}

.close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.created-keys-meta {
  margin: 0.75rem 1.25rem 0;
  font-size: 0.875rem;
  color: #4b5563;
  flex-shrink: 0;
}

.created-keys-text {
  margin: 0.75rem 1.25rem 0;
  width: calc(100% - 2.5rem);
  flex: 1;
  min-height: 200px;
  padding: 0.75rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8125rem;
  line-height: 1.5;
  resize: vertical;
  box-sizing: border-box;
  color: #111827;
  background: #f9fafb;
}

.created-keys-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.25rem 1.25rem;
  flex-shrink: 0;
}

.btn-primary,
.btn-secondary {
  padding: 0.55rem 1rem;
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
}

.btn-primary:hover {
  background: #4338ca;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-secondary:hover {
  background: #e5e7eb;
}
</style>
