<template>
  <div v-if="status" class="pipeline-panel">
    <div class="pipeline-head">
      <span>流水线进度</span>
      <span v-if="status.totalElapsedMs" class="pipeline-elapsed">
        已运行 {{ formatDuration(status.totalElapsedMs) }}
      </span>
    </div>
    <p v-if="currentLabel" class="pipeline-current">
      当前：{{ currentLabel }}
      <span v-if="status.currentDetail" class="pipeline-detail"> — {{ status.currentDetail }}</span>
    </p>
    <ul class="pipeline-stages">
      <li
        v-for="(stage, idx) in status.stages"
        :key="stage.code + '-' + idx"
        :class="['stage-row', stage.status]"
      >
        <span class="stage-icon">{{ stageIcon(stage.status) }}</span>
        <div class="stage-body">
          <div class="stage-title">
            {{ stage.label || stage.code }}
            <span v-if="stage.durationMs" class="stage-duration">
              {{ formatDuration(stage.durationMs) }}
            </span>
          </div>
          <p v-if="stage.detail" class="stage-detail">{{ stage.detail }}</p>
          <p v-if="stageMetrics(stage)" class="stage-metrics">{{ stageMetrics(stage) }}</p>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: Object, default: null },
})

const currentLabel = computed(() => {
  if (!props.status) return ''
  return props.status.currentStageLabel || props.status.currentStage || ''
})

const stageIcon = (status) => {
  if (status === 'done') return '✓'
  if (status === 'failed') return '✗'
  if (status === 'running') return '…'
  return '○'
}

const formatDuration = (ms) => {
  if (!ms || ms < 0) return '0s'
  const sec = Math.floor(ms / 1000)
  if (sec < 60) return `${sec}s`
  const min = Math.floor(sec / 60)
  const rem = sec % 60
  if (min < 60) return `${min}m ${rem}s`
  const h = Math.floor(min / 60)
  return `${h}h ${min % 60}m`
}

const stageMetrics = (stage) => {
  const m = stage.metrics
  if (!m || typeof m !== 'object') return ''
  const parts = []
  if (m.elapsedMs != null) parts.push(`耗时 ${formatDuration(m.elapsedMs)}`)
  if (m.transcriptChars != null) parts.push(`${m.transcriptChars} 字`)
  if (m.charsPerSec != null) parts.push(`${m.charsPerSec} 字/秒`)
  if (m.chunks != null) parts.push(`${m.chunks} 切片`)
  if (m.throughputMbPerSec != null) parts.push(`${m.throughputMbPerSec} MB/s`)
  if (m.fileSizeKb != null) parts.push(`${m.fileSizeKb} KB`)
  return parts.join(' · ')
}
</script>

<style scoped>
.pipeline-panel {
  width: 100%;
  max-width: 420px;
  margin-top: 0.5rem;
  padding: 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: var(--bg-base);
  text-align: left;
}

.pipeline-head {
  display: flex;
  justify-content: space-between;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.pipeline-elapsed {
  font-weight: 400;
  color: var(--text-muted);
}

.pipeline-current {
  font-size: 0.75rem;
  color: var(--accent-primary);
  margin: 0 0 0.75rem;
  line-height: 1.5;
}

.pipeline-detail {
  color: var(--text-muted);
}

.pipeline-stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.stage-row {
  display: flex;
  gap: 0.5rem;
  font-size: 0.75rem;
}

.stage-icon {
  width: 1.25rem;
  flex-shrink: 0;
  text-align: center;
  color: var(--text-muted);
}

.stage-row.done .stage-icon {
  color: #4ade80;
}

.stage-row.failed .stage-icon {
  color: var(--accent-danger);
}

.stage-row.running .stage-icon {
  color: var(--accent-primary);
}

.stage-title {
  font-weight: 500;
  color: var(--text-secondary);
}

.stage-duration {
  margin-left: 0.35rem;
  color: var(--text-muted);
  font-weight: 400;
}

.stage-detail,
.stage-metrics {
  margin: 0.15rem 0 0;
  color: var(--text-muted);
  line-height: 1.4;
}
</style>
