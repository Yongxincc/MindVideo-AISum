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
          <p>{{ loadingHint }}</p>
          <PipelineProgress v-if="pipeline" :status="pipeline" />
        </div>
        <div v-else-if="type === 'ai'" class="markdown-content" v-html="renderedMarkdown" />
        <div v-else class="text-content">
          <pre>{{ content }}</pre>
        </div>

        <div v-if="showQa && !loading" class="qa-panel">
          <h4 class="qa-title">向视频提问（RAG）</h4>
          <div v-if="qaHistory?.length" class="qa-history">
            <div class="qa-history-header">
              <div class="qa-history-label">历史记录（{{ qaHistory.length }}）</div>
              <button
                type="button"
                class="qa-history-clear"
                @click="$emit('clear-qa-history')"
              >
                清空
              </button>
            </div>
            <div
              v-for="msg in qaHistory"
              :key="msg.id"
              class="qa-history-item"
              :class="{ 'is-failed': msg.status === 'FAILED' }"
            >
              <div class="qa-history-q">
                <span class="qa-role">问</span>
                <span class="qa-history-q-text">{{ msg.question }}</span>
                <button
                  type="button"
                  class="qa-history-delete"
                  title="删除此条"
                  @click="$emit('delete-qa-item', msg.id)"
                >
                  ×
                </button>
              </div>
              <div
                class="qa-history-a markdown-content"
                v-html="renderQaMarkdown(msg.answer)"
              />
              <details v-if="msg.citations?.length" class="qa-citations compact">
                <summary>引用（{{ msg.citations.length }}）</summary>
                <div
                  v-for="(c, idx) in msg.citations"
                  :key="idx"
                  class="citation-item"
                >
                  <span class="citation-meta">#{{ c.chunkIndex }} · {{ c.score?.toFixed(2) }}</span>
                  <p>{{ c.excerpt }}</p>
                </div>
              </details>
              <time v-if="msg.createdAt" class="qa-history-time">{{ formatQaTime(msg.createdAt) }}</time>
            </div>
          </div>
          <p v-else class="qa-history-empty">暂无问答记录，在下方输入问题开始提问。</p>
          <textarea
            v-model="qaQuestionModel"
            class="qa-input"
            rows="3"
            placeholder="例如：作者的核心论点是什么？"
            @keydown.enter.exact.prevent="$emit('ask')"
          />
          <button class="retry-btn primary" :disabled="qaLoading" @click="$emit('ask')">
            {{ qaLoading ? '检索生成中（长视频可能需数分钟）...' : '提问' }}
          </button>
          <div v-if="qaAnswer" class="qa-answer markdown-content" v-html="renderedQaAnswer" />
          <details v-if="qaCitations?.length" class="qa-citations">
            <summary>引用片段（{{ qaCitations.length }}）</summary>
            <div
              v-for="(c, idx) in qaCitations"
              :key="idx"
              class="citation-item"
            >
              <span class="citation-meta">#{{ c.chunkIndex }} · 相关度 {{ c.score?.toFixed(2) }}</span>
              <p>{{ c.excerpt }}</p>
            </div>
          </details>
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
import PipelineProgress from './PipelineProgress.vue'

const props = defineProps({
  visible: Boolean,
  type: { type: String, default: 'ai' },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  loading: Boolean,
  pipeline: { type: Object, default: null },
  showRetry: Boolean,
  retryLabel: { type: String, default: '重试' },
  showSecondaryRetry: Boolean,
  showQa: Boolean,
  qaQuestion: { type: String, default: '' },
  qaAnswer: { type: String, default: '' },
  qaCitations: { type: Array, default: () => [] },
  qaHistory: { type: Array, default: () => [] },
  qaLoading: Boolean,
})

const emit = defineEmits([
  'close',
  'retry',
  'retry-transcribe',
  'ask',
  'update:qaQuestion',
  'delete-qa-item',
  'clear-qa-history',
])

const qaQuestionModel = computed({
  get: () => props.qaQuestion,
  set: (v) => emit('update:qaQuestion', v),
})

const loadingHint = computed(() =>
  props.type === 'ai'
    ? 'AI 正在分析中，长视频约需 1–5 分钟…'
    : '正在处理中…'
)

const renderQaMarkdown = (text) => {
  if (!text) return ''
  const html = marked.parse(text)
  return DOMPurify.sanitize(html)
}

const renderedQaAnswer = computed(() => renderQaMarkdown(props.qaAnswer))

const formatQaTime = (iso) => {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return ''
    return d.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return ''
  }
}

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

.qa-panel {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--border-subtle);
}

.qa-history {
  max-height: 280px;
  overflow-y: auto;
  margin-bottom: 1rem;
  padding-right: 0.25rem;
}

.qa-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.qa-history-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.qa-history-clear {
  padding: 0.15rem 0.45rem;
  font-size: 0.7rem;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.qa-history-clear:hover {
  color: #e55;
  border-color: rgba(220, 80, 80, 0.45);
}

.qa-history-q-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.qa-history-delete {
  flex-shrink: 0;
  width: 1.35rem;
  height: 1.35rem;
  padding: 0;
  line-height: 1;
  font-size: 1rem;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.qa-history-delete:hover {
  color: #e55;
  background: rgba(220, 80, 80, 0.08);
}

.qa-history-empty {
  font-size: 0.8125rem;
  color: var(--text-muted);
  margin: 0 0 0.75rem;
}

.qa-history-item {
  margin-bottom: 0.85rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
}

.qa-history-item.is-failed {
  border-color: rgba(220, 80, 80, 0.35);
}

.qa-history-q {
  display: flex;
  gap: 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.qa-role {
  flex-shrink: 0;
  color: var(--accent-primary);
}

.qa-history-a {
  font-size: 0.8125rem;
}

.qa-history-time {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.7rem;
  color: var(--text-muted);
}

.qa-citations.compact {
  margin-top: 0.5rem;
}

.qa-title {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--text-primary);
}

.qa-input {
  width: 100%;
  margin-bottom: 0.75rem;
  padding: 0.65rem 0.75rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-default);
  background: var(--bg-base);
  color: var(--text-primary);
  font-size: 0.875rem;
  resize: vertical;
}

.qa-answer {
  margin-top: 1rem;
}

.qa-citations {
  margin-top: 1rem;
  font-size: 0.8125rem;
  color: var(--text-muted);
}

.citation-item {
  margin-top: 0.5rem;
  padding: 0.5rem;
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
}

.citation-meta {
  display: block;
  font-weight: 600;
  color: var(--accent-primary);
  margin-bottom: 0.25rem;
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
