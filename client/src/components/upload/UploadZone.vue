<template>
  <div class="upload-zone">
    <input
      ref="fileInput"
      type="file"
      accept="video/*"
      hidden
      @change="onFileChange"
    />

    <div class="upload-tabs">
      <button
        class="tab"
        :class="{ active: activeTab === 'file' }"
        @click="activeTab = 'file'"
      >
        本地文件
      </button>
      <button
        class="tab"
        :class="{ active: activeTab === 'url' }"
        @click="activeTab = 'url'"
      >
        链接导入
      </button>
    </div>

    <div
      class="upload-panel"
      :class="{
        'is-dragover': isDragOver,
        processing: uploading,
        'needs-auth': !isLoggedIn,
      }"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      @click="onPanelClick"
    >
      <template v-if="uploading">
        <UploadProgress
          v-if="uploadProgress?.active"
          :mode="uploadProgress.mode"
          :percent="uploadProgress.percent"
          :loaded="uploadProgress.loaded"
          :total="uploadProgress.total"
          :speed="uploadProgress.speed"
          :file-name="uploadProgress.fileName"
          :message="uploadProgress.message"
          :elapsed="uploadProgress.elapsed"
          :indeterminate="uploadProgress.indeterminate"
        />
        <template v-else>
          <LoadingSpinner />
          <p class="busy-text">{{ uploadMessage || '正在处理...' }}</p>
        </template>
      </template>

      <template v-else-if="activeTab === 'file'">
        <div class="panel-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </div>
        <p class="panel-title">{{ isDragOver ? '松手即可上传' : '点击或拖拽视频文件' }}</p>
        <p class="panel-hint">支持 MP4、MOV、AVI 等常见格式</p>
        <p v-if="!isLoggedIn" class="auth-hint">登录后可用</p>
      </template>

      <template v-else>
        <div class="panel-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z" />
          </svg>
        </div>
        <p class="panel-title">粘贴视频链接</p>
        <p class="panel-hint">B站 / YouTube / 抖音</p>
        <div class="url-form" @click.stop>
          <div class="url-input-wrap">
            <input
              v-model="videoUrl"
              type="url"
              placeholder="粘贴 B站 / YouTube / 抖音链接..."
              @keyup.enter="submitUrl"
            />
          </div>
          <button class="url-submit" :disabled="!videoUrl.trim()" @click="submitUrl">
            <span class="url-submit-text">开始导入</span>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
        <p v-if="!isLoggedIn" class="auth-hint">登录后可用</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import LoadingSpinner from '../ui/LoadingSpinner.vue'
import UploadProgress from './UploadProgress.vue'

const props = defineProps({
  uploading: Boolean,
  uploadMessage: { type: String, default: '' },
  uploadProgress: { type: Object, default: null },
  isLoggedIn: Boolean,
})

const emit = defineEmits(['upload-file', 'upload-url', 'require-auth', 'invalid-file'])

const activeTab = ref('file')
const videoUrl = ref('')
const isDragOver = ref(false)
const fileInput = ref(null)

const onDragOver = () => {
  if (activeTab.value !== 'file') return
  isDragOver.value = true
}

const onDragLeave = () => {
  isDragOver.value = false
}

const onPanelClick = () => {
  if (props.uploading) return
  if (!props.isLoggedIn) {
    emit('require-auth')
    return
  }
  if (activeTab.value === 'file') {
    fileInput.value?.click()
  }
}

const onFileChange = (e) => {
  if (!props.isLoggedIn) {
    e.target.value = ''
    emit('require-auth')
    return
  }
  const file = e.target.files?.[0]
  if (file) {
    emit('upload-file', file)
    e.target.value = ''
  }
}

const onDrop = (e) => {
  isDragOver.value = false
  if (!props.isLoggedIn) {
    emit('require-auth')
    return
  }
  if (activeTab.value !== 'file') return
  const file = e.dataTransfer.files?.[0]
  if (!file) return
  if (!file.type.startsWith('video/')) {
    emit('invalid-file')
    return
  }
  emit('upload-file', file)
}

const submitUrl = () => {
  if (!props.isLoggedIn) {
    emit('require-auth')
    return
  }
  if (!videoUrl.value.trim()) return
  emit('upload-url', videoUrl.value.trim())
  videoUrl.value = ''
}
</script>

<style scoped>
.upload-zone {
  max-width: 640px;
  margin: 0 auto;
  width: 100%;
}

.upload-tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
  background: var(--bg-surface);
  padding: 0.25rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
}

.tab {
  flex: 1;
  padding: 0.5rem 1rem;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s;
}

.tab.active {
  background: var(--bg-elevated);
  color: var(--text-primary);
}

.tab:hover:not(.active) {
  color: var(--text-secondary);
}

.upload-panel {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.25s;
  backdrop-filter: blur(8px);
}

.upload-panel:hover:not(.processing) {
  border-color: rgba(129, 140, 248, 0.4);
  box-shadow: var(--shadow-glow);
}

.upload-panel.is-dragover {
  border-color: var(--accent-primary);
  animation: dragPulse 1.5s infinite;
  background: rgba(129, 140, 248, 0.05);
}

.upload-panel.processing {
  cursor: default;
  pointer-events: none;
}

.upload-panel.needs-auth {
  opacity: 0.85;
}

.panel-icon {
  color: var(--accent-primary);
  margin-bottom: 0.5rem;
}

.panel-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.panel-hint {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.auth-hint {
  font-size: 0.75rem;
  color: var(--accent-primary);
  margin-top: 0.5rem;
  opacity: 0.8;
}

.busy-text {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-top: 0.5rem;
}

.url-form {
  display: flex;
  align-items: stretch;
  gap: 0.625rem;
  width: 100%;
  max-width: 420px;
  margin-top: 1.25rem;
}

.url-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
  background: var(--bg-base);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 0 0.875rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.url-input-wrap:focus-within {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.15);
}

.url-input-wrap input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 0.9rem;
  padding: 0.75rem 0;
}

.url-input-wrap input::placeholder {
  color: var(--text-muted);
}

.url-submit {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  padding: 0 1.25rem;
  min-height: 44px;
  background: var(--accent-primary);
  border: none;
  border-radius: var(--radius-md);
  color: #fff;
  font-size: 0.875rem;
  font-weight: 600;
  white-space: nowrap;
  transition: background 0.2s, transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(129, 140, 248, 0.35);
}

.url-submit:hover:not(:disabled) {
  background: var(--accent-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(129, 140, 248, 0.45);
}

.url-submit:active:not(:disabled) {
  transform: translateY(0);
}

.url-submit:disabled {
  background: var(--bg-elevated);
  color: var(--text-muted);
  box-shadow: none;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .upload-panel {
    min-height: 200px;
    padding: 1.5rem;
  }

  .url-form {
    flex-direction: column;
    max-width: 100%;
  }

  .url-submit {
    width: 100%;
    min-height: 46px;
    padding: 0.75rem 1rem;
  }
}
</style>
