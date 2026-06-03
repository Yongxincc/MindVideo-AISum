<template>
  <Teleport to="body">
    <Transition name="backdrop">
      <div v-if="visible" class="sidebar-backdrop" @click="$emit('close')" />
    </Transition>

    <div class="sidebar-panel" :class="{ 'is-open': visible }">
      <div class="sidebar-header">
        <div class="sidebar-title">
          <span class="title-icon">
            <svg v-if="type === 'ai'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M2 12h2" /><path d="M20 12h2" /><path d="M12 2v2" /><path d="M12 20v2" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
          </span>
          {{ title }}
        </div>
        <div class="header-actions">
          <button class="close-btn" aria-label="关闭" @click="$emit('close')">×</button>
        </div>
      </div>

      <div class="sidebar-body">
        <div v-if="loading" class="loading-state">
          <LoadingSpinner />
          <p>数据流处理中...</p>
        </div>
        <div v-else-if="type === 'ai'" class="markdown-content" v-html="renderedMarkdown" />
        <div v-else class="text-content">
          <pre>{{ content }}</pre>
        </div>

        <div v-if="showRetry && !loading" class="sidebar-footer">
          <button class="retry-btn primary" @click="$emit('retry')">
            {{ retryLabel }}
          </button>
          <button
            v-if="showSecondaryRetry"
            class="retry-btn secondary"
            @click="$emit('retry-transcribe')"
          >
            重新提取音频
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, watch, onMounted, onUnmounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import LoadingSpinner from '../ui/LoadingSpinner.vue'

const props = defineProps({
  visible: Boolean,
  type: { type: String, default: 'ai' },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  loading: Boolean,
  showRetry: Boolean,
  retryLabel: { type: String, default: '重试' },
  showSecondaryRetry: Boolean,
})

const emit = defineEmits(['close', 'retry', 'retry-transcribe'])

const renderedMarkdown = computed(() => {
  if (!props.content) return ''
  let cleanText = props.content.replace(/<think>[\s\S]*?<\/think>/gi, '')
  if (cleanText.includes('</think>')) {
    cleanText = cleanText.split('</think>').pop()
  }
  if (!cleanText.trim()) cleanText = props.content
  const html = marked.parse(cleanText)
  return DOMPurify.sanitize(html)
})

const onKeydown = (e) => {
  if (e.key === 'Escape' && props.visible) {
    emit('close')
  }
}

watch(
  () => props.visible,
  (open) => {
    document.body.classList.toggle('scroll-locked', open)
  }
)

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.remove('scroll-locked')
})
</script>

<style scoped>
.sidebar-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 998;
}

.sidebar-panel {
  position: fixed;
  top: 0;
  right: -100%;
  width: 550px;
  max-width: 90vw;
  height: 100%;
  background: var(--bg-surface);
  border-left: 1px solid var(--border-default);
  z-index: 999;
  transition: right 0.35s cubic-bezier(0.19, 1, 0.22, 1);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-md);
}

.sidebar-panel.is-open {
  right: 0;
}

.sidebar-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-overlay);
  backdrop-filter: blur(12px);
}

.sidebar-title {
  font-size: 1.125rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-icon {
  color: var(--accent-primary);
  display: flex;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sidebar-footer {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.retry-btn {
  width: 100%;
  padding: 0.65rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  transition: background 0.2s, border-color 0.2s;
}

.retry-btn.primary {
  border: 1px solid var(--accent-primary);
  background: rgba(129, 140, 248, 0.12);
  color: var(--accent-primary);
}

.retry-btn.primary:hover {
  background: rgba(129, 140, 248, 0.22);
}

.retry-btn.secondary {
  border: 1px solid var(--border-default);
  background: transparent;
  color: var(--text-secondary);
}

.retry-btn.secondary:hover {
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 1.5rem;
  line-height: 1;
  padding: 0.25rem;
  transition: color 0.2s;
}

.close-btn:hover {
  color: var(--text-primary);
}

.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 1rem;
  color: var(--text-muted);
  font-size: 0.875rem;
}

.text-content pre {
  white-space: pre-wrap;
  font-family: 'Inter', monospace;
  font-size: 0.875rem;
  line-height: 1.7;
  background: var(--bg-base);
  padding: 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  color: var(--text-secondary);
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  color: var(--accent-primary);
  margin-top: 1.25em;
  margin-bottom: 0.5em;
  font-weight: 600;
}

.markdown-content :deep(h1) {
  font-size: 1.25rem;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.5rem;
}

.markdown-content :deep(p) {
  margin-bottom: 0.875em;
  line-height: 1.7;
  color: var(--text-secondary);
}

.markdown-content :deep(ul) {
  padding-left: 1.25rem;
  margin-bottom: 1em;
}

.markdown-content :deep(li) {
  margin-bottom: 0.35rem;
  color: var(--text-secondary);
}

.markdown-content :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.backdrop-enter-active,
.backdrop-leave-active {
  transition: opacity 0.3s;
}
.backdrop-enter-from,
.backdrop-leave-to {
  opacity: 0;
}

@media (max-width: 480px) {
  .sidebar-panel {
    width: 100%;
    max-width: 100%;
  }
}
</style>
