<template>
  <div class="upload-progress">
    <div class="progress-header">
      <div class="progress-info">
        <p class="progress-title">{{ title }}</p>
        <p class="progress-subtitle">{{ subtitle }}</p>
      </div>
      <span v-if="!indeterminate" class="progress-percent">{{ displayPercent }}%</span>
      <span v-else class="progress-percent indeterminate-label">处理中</span>
    </div>

    <div class="progress-track" :class="{ indeterminate }">
      <div
        class="progress-fill"
        :class="{ indeterminate }"
        :style="indeterminate ? undefined : { width: `${displayPercent}%` }"
      />
    </div>

    <div class="progress-stats">
      <template v-if="!indeterminate">
        <span>{{ formatBytes(loaded) }} / {{ formatBytes(total) }}</span>
        <span class="stat-speed">{{ formatSpeed(speed) }}</span>
      </template>
      <template v-else>
        <span>{{ message || '服务端正在拉取资源...' }}</span>
        <span v-if="elapsed > 0">已用时 {{ formatDuration(elapsed) }}</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatBytes, formatSpeed, formatDuration } from '../../utils/format'

const props = defineProps({
  mode: { type: String, default: 'file' },
  percent: { type: Number, default: 0 },
  loaded: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  speed: { type: Number, default: 0 },
  fileName: { type: String, default: '' },
  message: { type: String, default: '' },
  elapsed: { type: Number, default: 0 },
  indeterminate: Boolean,
})

const displayPercent = computed(() => Math.min(100, Math.max(0, props.percent)))

const title = computed(() => {
  if (props.mode === 'url') return '链接导入中'
  return props.fileName || '正在上传视频'
})

const subtitle = computed(() => {
  if (props.indeterminate) return '请稍候，正在解析并下载视频'
  if (props.percent >= 99) return '上传完成，正在入库...'
  return '正在传输到服务器'
})
</script>

<style scoped>
.upload-progress {
  width: 100%;
  max-width: 420px;
  padding: 0.25rem 0;
}

.progress-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.875rem;
}

.progress-info {
  min-width: 0;
  text-align: left;
}

.progress-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-subtitle {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 0.2rem;
}

.progress-percent {
  flex-shrink: 0;
  font-size: 1.25rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--accent-primary);
  line-height: 1;
}

.indeterminate-label {
  font-size: 0.875rem;
  font-weight: 600;
  padding-top: 0.15rem;
}

.progress-track {
  height: 8px;
  background: var(--bg-base);
  border-radius: 999px;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-primary), #a5b4fc);
  border-radius: 999px;
  transition: width 0.15s ease-out;
  box-shadow: 0 0 12px rgba(129, 140, 248, 0.5);
}

.progress-fill.indeterminate {
  width: 40% !important;
  animation: progressIndeterminate 1.4s ease-in-out infinite;
}

.progress-track.indeterminate {
  position: relative;
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.625rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.stat-speed {
  color: var(--accent-primary);
  font-weight: 600;
}

@keyframes progressIndeterminate {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}
</style>
