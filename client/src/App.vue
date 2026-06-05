<template>
  <div class="app-stage">
    <div class="ambient-glow" />
    <div class="ambient-noise" />

    <AppNavbar
      :current-user="currentUser"
      :active-job-count="activeJobCount"
      :uploading="uploading"
      @open-auth="openAuthModal"
      @logout="onLogout"
    />

    <main class="main-container">
      <section class="hero-section">
        <p class="hero-eyebrow">MindVideo Studio</p>
        <h1 class="hero-title">
          读懂<span class="hero-accent">每一支</span>视频
        </h1>
        <p class="hero-subtitle">
          上传或粘贴链接，自动转写并生成 AI 摘要<br class="hero-break" />
          让长篇影像，几分钟内变得可搜索、可复用
        </p>

        <UploadZone
          :uploading="uploading"
          :upload-message="uploadMessage"
          :upload-progress="uploadProgress"
          :is-logged-in="!!currentUser"
          @upload-file="onUploadFile"
          @upload-url="onUploadUrl"
          @require-auth="openAuthModal"
          @invalid-file="onInvalidFile"
        />
      </section>

      <TaskGrid
        v-if="currentUser"
        :items="list"
        :is-logged-in="!!currentUser"
        :is-action-loading="isActionLoading"
        :rename="renameItem"
        @delete="onDeleteRequest"
        @download="downloadAudio"
        @transcribe="transcribe"
        @ai-analyze="aiAnalyze"
      />
    </main>

    <ResultSidebar
      :visible="sidebar.visible"
      :type="sidebar.type"
      :title="sidebar.title"
      :content="sidebar.content"
      :loading="sidebar.loading"
      :pipeline="sidebar.pipeline"
      :show-retry="sidebarRetryVisible"
      :retry-label="sidebarRetryLabel"
      :show-secondary-retry="sidebar.type === 'ai' && sidebarSecondaryRetryVisible"
      :show-qa="sidebarQaVisible"
      v-model:qa-question="qaState.question"
      :qa-answer="qaState.answer"
      :qa-citations="qaState.citations"
      :qa-history="qaState.history"
      :qa-loading="qaState.loading"
      @close="closeSidebar"
      @retry="onSidebarRetry"
      @retry-transcribe="transcribe(sidebar.mediaId, true)"
      @ask="askVideo"
    />

    <AuthModal
      :visible="showAuthModal"
      :mode="authMode"
      :form="authForm"
      :loading="authLoading"
      :message="authMessage"
      :is-error="authError"
      @close="closeAuthModal"
      @submit="onAuthSubmit"
      @switch-mode="switchAuthMode"
    />

    <ConfirmDialog
      :visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      confirm-text="删除"
      @confirm="onConfirmDelete"
      @cancel="closeConfirm"
    />

    <ToastContainer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppNavbar from './components/layout/AppNavbar.vue'
import UploadZone from './components/upload/UploadZone.vue'
import TaskGrid from './components/workspace/TaskGrid.vue'
import ResultSidebar from './components/sidebar/ResultSidebar.vue'
import AuthModal from './components/auth/AuthModal.vue'
import ToastContainer from './components/ui/ToastContainer.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import { useAuth } from './composables/useAuth'
import { useMediaTasks } from './composables/useMediaTasks'
import { useToast } from './composables/useToast'
import { getTaskTitle } from './utils/format.js'

const {
  currentUser,
  showAuthModal,
  authMode,
  authLoading,
  authMessage,
  authError,
  authForm,
  openAuthModal,
  closeAuthModal,
  switchAuthMode,
  handleAuth,
  logout,
  initAuth,
} = useAuth()

const {
  list,
  uploading,
  uploadMessage,
  uploadProgress,
  sidebar,
  qaState,
  activeJobCount,
  fetchList,
  uploadFile,
  uploadUrl,
  deleteItem,
  renameItem,
  downloadAudio,
  transcribe,
  aiAnalyze,
  closeSidebar,
  isActionLoading,
  isAiSummaryError,
  isTranscriptError,
  isTranscriptSuccess,
  askVideo,
  canShowQa,
} = useMediaTasks(() => currentUser.value?.id)

const { warning } = useToast()

const sidebarRetryVisible = computed(() => {
  if (!sidebar.value.visible || sidebar.value.loading) return false
  if (sidebar.value.type === 'text') {
    return (
      isTranscriptError(sidebar.value.content) ||
      isTranscriptSuccess(sidebar.value.content)
    )
  }
  if (sidebar.value.type === 'ai') {
    return isAiSummaryError(sidebar.value.content) || sidebar.value.content?.includes('##')
  }
  return false
})

const sidebarRetryLabel = computed(() =>
  sidebar.value.type === 'text' ? '重新提取' : '重新分析'
)

const sidebarQaVisible = computed(() => {
  if (!sidebar.value.visible || sidebar.value.loading) return false
  const item = list.value.find((i) => i.id === sidebar.value.mediaId)
  return canShowQa(item)
})

const sidebarSecondaryRetryVisible = computed(
  () =>
    sidebar.value.type === 'ai' &&
    !sidebar.value.loading &&
    (isAiSummaryError(sidebar.value.content) ||
      isTranscriptError(list.value.find((i) => i.id === sidebar.value.mediaId)))
)

const onSidebarRetry = () => {
  if (sidebar.value.type === 'text') {
    transcribe(sidebar.value.mediaId, true)
  } else {
    aiAnalyze(sidebar.value.mediaId, true)
  }
}

const confirmState = ref({
  visible: false,
  title: '',
  message: '',
  item: null,
})

const onUploadFile = (file) => uploadFile(file)

const onUploadUrl = async (url) => {
  await uploadUrl(url)
}

const onInvalidFile = () => {
  warning('仅支持上传视频文件')
}

const onDeleteRequest = (item) => {
  confirmState.value = {
    visible: true,
    title: '确认删除',
    message: `确定要永久删除「${getTaskTitle(item)}」吗？此操作不可撤销。`,
    item,
  }
}

const onConfirmDelete = async () => {
  const item = confirmState.value.item
  closeConfirm()
  if (item) await deleteItem(item)
}

const closeConfirm = () => {
  confirmState.value = { visible: false, title: '', message: '', item: null }
}

const onAuthSubmit = () => handleAuth(fetchList)

const onLogout = () => logout(() => {
  list.value = []
})

onMounted(() => {
  initAuth()
  fetchList()
})
</script>

<style scoped>
.app-stage {
  position: relative;
  min-height: 100vh;
  width: 100%;
}

.ambient-glow {
  position: fixed;
  top: -30%;
  left: 50%;
  transform: translateX(-50%);
  width: 80vw;
  height: 60vh;
  background: radial-gradient(ellipse, rgba(129, 140, 248, 0.08) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.ambient-noise {
  position: fixed;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='4'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 0;
}

.main-container {
  position: relative;
  z-index: 1;
  max-width: var(--max-width-content);
  margin: 0 auto;
  padding: 3rem 2rem 4rem;
}

.hero-section {
  text-align: center;
  margin-bottom: var(--space-section);
  animation: slideUpFade 0.6s forwards;
}

.hero-eyebrow {
  display: inline-block;
  font-family: 'Inter', sans-serif;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--accent-primary);
  opacity: 0.85;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-subtle);
}

.hero-title {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: clamp(2rem, 5.5vw, 3.25rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
  color: var(--text-primary);
  margin-bottom: 1rem;
}

.hero-accent {
  background: linear-gradient(135deg, var(--accent-primary) 0%, #a5b4fc 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 1rem;
  font-weight: 400;
  line-height: 1.75;
  color: var(--text-secondary);
  max-width: 28rem;
  margin: 0 auto 2.75rem;
}

.hero-break {
  display: none;
}

@media (min-width: 640px) {
  .hero-break {
    display: block;
  }
}

@media (max-width: 768px) {
  .main-container {
    padding: 2rem 1rem 3rem;
  }

  .hero-eyebrow {
    letter-spacing: 0.16em;
    font-size: 0.625rem;
  }

  .hero-subtitle {
    font-size: 0.9rem;
    padding: 0 0.5rem;
    margin-bottom: 2rem;
  }
}
</style>
