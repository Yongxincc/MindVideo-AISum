<template>
  <div class="task-card">
    <button class="delete-btn" aria-label="删除" @click.stop="$emit('delete', item)">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="18" y1="6" x2="6" y2="18" />
        <line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    </button>

    <div class="card-meta">
      <div class="meta-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <polygon points="23 7 16 12 23 17 23 7" />
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
        </svg>
      </div>
      <div class="meta-info">
        <div v-if="!editing" class="title-row">
          <div class="task-title" :title="taskTitle">{{ taskTitle }}</div>
          <button class="rename-btn" aria-label="重命名" @click.stop="startEdit">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
          </button>
        </div>
        <form v-else class="rename-form" @submit.prevent="submitRename">
          <input
            ref="inputRef"
            v-model="draftName"
            class="rename-input"
            maxlength="128"
            placeholder="输入任务名称"
            @keydown.esc.prevent="cancelEdit"
          />
          <div class="rename-actions">
            <button type="submit" class="rename-save" :disabled="saving">保存</button>
            <button type="button" class="rename-cancel" @click="cancelEdit">取消</button>
          </div>
        </form>
        <div v-if="item.displayName && !editing" class="original-name" :title="item.filename">
          {{ item.filename }}
        </div>
        <div class="meta-row">
          <span class="time-tag">{{ formatTime(item.uploadTime) }}</span>
          <span class="status-badge" :class="item.status.toLowerCase()">
            {{ item.status === 'COMPLETED' ? '就绪' : '处理中' }}
          </span>
        </div>
      </div>
    </div>

    <div class="step-bar">
      <div
        v-for="(step, idx) in steps"
        :key="step.key"
        class="step"
        :class="{ done: step.done, active: step.active }"
      >
        <div class="step-dot" />
        <span class="step-label">{{ step.label }}</span>
        <div v-if="idx < steps.length - 1" class="step-line" :class="{ done: step.done }" />
      </div>
    </div>

    <div class="action-dock">
      <button class="dock-btn" @click="$emit('download', item)">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M9 18V5l12-2v13" />
          <circle cx="6" cy="18" r="3" />
          <circle cx="18" cy="16" r="3" />
        </svg>
        <span>下载音频</span>
      </button>

      <button
        class="dock-btn"
        :disabled="item.status !== 'COMPLETED' || textLoading"
        @click="$emit('transcribe', item.id)"
      >
        <LoadingSpinner v-if="textLoading" size="small" />
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
        </svg>
        <span>提取文字</span>
      </button>

      <button
        class="dock-btn ai-btn"
        :disabled="item.status !== 'COMPLETED' || aiLoading"
        @click="$emit('ai-analyze', item.id)"
      >
        <LoadingSpinner v-if="aiLoading" size="small" />
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="4" y="4" width="16" height="16" rx="2" />
          <rect x="9" y="9" width="6" height="6" />
        </svg>
        <span>AI 总结</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import LoadingSpinner from '../ui/LoadingSpinner.vue'
import { getTaskTitle } from '../../utils/format.js'

const props = defineProps({
  item: { type: Object, required: true },
  textLoading: Boolean,
  aiLoading: Boolean,
  rename: { type: Function, default: null },
})

const emit = defineEmits(['delete', 'download', 'transcribe', 'ai-analyze'])

const editing = ref(false)
const draftName = ref('')
const saving = ref(false)
const inputRef = ref(null)

const taskTitle = computed(() => getTaskTitle(props.item))

const startEdit = async () => {
  draftName.value = props.item.displayName || ''
  editing.value = true
  await nextTick()
  inputRef.value?.focus()
  inputRef.value?.select()
}

const cancelEdit = () => {
  editing.value = false
  draftName.value = ''
}

const submitRename = async () => {
  const nextName = draftName.value.trim()
  if (nextName === (props.item.displayName || '')) {
    cancelEdit()
    return
  }

  saving.value = true
  try {
    const ok = props.rename ? await props.rename(props.item, nextName) : false
    if (ok) cancelEdit()
  } finally {
    saving.value = false
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  const date = new Date(timeStr)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const steps = computed(() => {
  const uploaded = true
  const ready = props.item.status === 'COMPLETED'
  const hasTranscript =
    props.item.transcriptStatus === 'OK' ||
    (!props.item.transcriptStatus && !!props.item.transcriptText)
  const hasAi = props.item.aiSummary?.includes('##')

  return [
    { key: 'upload', label: '上传', done: uploaded, active: !ready },
    { key: 'ready', label: '转码', done: ready, active: uploaded && !ready },
    { key: 'text', label: '文字', done: hasTranscript, active: ready && !hasTranscript },
    { key: 'ai', label: 'AI', done: hasAi, active: hasTranscript && !hasAi },
  ]
})
</script>

<style scoped>
.task-card {
  position: relative;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
  backdrop-filter: blur(8px);
}

.task-card:hover {
  border-color: var(--border-default);
  box-shadow: var(--shadow-sm);
}

.delete-btn {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  background: rgba(9, 9, 11, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  padding: 0.35rem;
  display: flex;
  z-index: 2;
  opacity: 1;
  transition: all 0.2s;
}

.delete-btn:hover {
  color: var(--accent-danger);
  border-color: rgba(248, 113, 113, 0.4);
}

.card-meta {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  align-items: center;
  border-bottom: 1px solid var(--border-subtle);
}

.meta-icon {
  width: 48px;
  height: 48px;
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.2);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-primary);
  flex-shrink: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.task-title {
  font-size: 0.95rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.rename-btn {
  flex-shrink: 0;
  display: flex;
  padding: 0.2rem;
  color: var(--text-muted);
  border-radius: var(--radius-sm);
  opacity: 0.45;
  transition: all 0.2s;
}

.task-card:hover .rename-btn,
.rename-btn:focus-visible {
  opacity: 1;
}

.rename-btn:hover {
  color: var(--accent-primary);
  background: rgba(129, 140, 248, 0.1);
}

.original-name {
  margin-top: 0.2rem;
  font-size: 0.7rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

.rename-form {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.rename-input {
  width: 100%;
  max-width: 220px;
  padding: 0.35rem 0.5rem;
  font-size: 0.85rem;
  color: var(--text-primary);
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.rename-input:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.rename-actions {
  display: flex;
  gap: 0.35rem;
}

.rename-save,
.rename-cancel {
  padding: 0.2rem 0.55rem;
  font-size: 0.7rem;
  border-radius: var(--radius-sm);
}

.rename-save {
  color: white;
  background: var(--accent-primary);
}

.rename-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.rename-cancel {
  color: var(--text-secondary);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
}

.meta-row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 0.35rem;
  font-size: 0.75rem;
}

.time-tag {
  color: var(--text-muted);
}

.status-badge {
  padding: 0.1rem 0.5rem;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.status-badge.completed {
  color: var(--accent-success);
  background: rgba(52, 211, 153, 0.1);
  border: 1px solid rgba(52, 211, 153, 0.3);
}

.status-badge.processing {
  color: var(--accent-primary);
  background: rgba(129, 140, 248, 0.1);
  border: 1px solid rgba(129, 140, 248, 0.3);
  animation: blink 1.5s infinite;
}

.step-bar {
  display: flex;
  align-items: center;
  padding: 0.75rem 1.25rem;
  gap: 0;
  border-bottom: 1px solid var(--border-subtle);
}

.step {
  display: flex;
  align-items: center;
  flex: 1;
  position: relative;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  flex-shrink: 0;
  transition: all 0.2s;
}

.step.done .step-dot {
  background: var(--accent-success);
  border-color: var(--accent-success);
}

.step.active .step-dot {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  box-shadow: 0 0 8px rgba(129, 140, 248, 0.5);
}

.step-label {
  font-size: 0.65rem;
  color: var(--text-muted);
  margin-left: 0.35rem;
  white-space: nowrap;
}

.step.done .step-label {
  color: var(--text-secondary);
}

.step-line {
  flex: 1;
  height: 1px;
  background: var(--border-subtle);
  margin: 0 0.5rem;
}

.step-line.done {
  background: var(--accent-success);
}

.action-dock {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0.5rem;
  padding: 0.75rem;
}

.dock-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 0.75rem 0.5rem;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.7rem;
  transition: all 0.2s;
}

.dock-btn:hover:not(:disabled) {
  color: var(--text-primary);
  border-color: var(--border-default);
}

.dock-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.dock-btn.ai-btn {
  border-color: rgba(129, 140, 248, 0.3);
  background: linear-gradient(135deg, rgba(129, 140, 248, 0.08), rgba(99, 102, 241, 0.05));
  color: var(--accent-primary);
}

.dock-btn.ai-btn:hover:not(:disabled) {
  border-color: var(--accent-primary);
  background: rgba(129, 140, 248, 0.15);
}

@media (max-width: 768px) {
  .action-dock {
    grid-template-columns: 1fr 1fr;
  }

  .dock-btn.ai-btn {
    grid-column: span 2;
    flex-direction: row;
    justify-content: center;
  }

  .step-label {
    display: none;
  }
}
</style>
