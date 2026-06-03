import { ref, computed } from 'vue'
import { useApi } from './useApi'
import { useToast } from './useToast'
import { getTaskTitle } from '../utils/format.js'

const list = ref([])
const uploading = ref(false)
const uploadMessage = ref('')
const uploadProgress = ref(createIdleProgress())
const pollingTimers = ref({})
const loadingActions = ref({})

function createIdleProgress() {
  return {
    active: false,
    mode: 'file',
    indeterminate: false,
    percent: 0,
    loaded: 0,
    total: 0,
    speed: 0,
    fileName: '',
    message: '',
    elapsed: 0,
  }
}

let elapsedTimer = null

function clearElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function startElapsedTimer() {
  clearElapsedTimer()
  const startTime = Date.now()
  elapsedTimer = setInterval(() => {
    uploadProgress.value.elapsed = Math.floor((Date.now() - startTime) / 1000)
  }, 1000)
}

function resetUploadProgress() {
  clearElapsedTimer()
  uploadProgress.value = createIdleProgress()
}

const sidebar = ref({
  visible: false,
  type: 'ai',
  title: '',
  content: '',
  loading: false,
  mediaId: null,
})

export function useMediaTasks(getUserId) {
  const { fetchText, fetchJson, fetchBlob, uploadFormData } = useApi()
  const { success, error, warning, info } = useToast()

  const activeJobCount = computed(() => {
    let count = uploading.value ? 1 : 0
    count += Object.keys(pollingTimers.value).length
    return count
  })

  const fetchList = async () => {
    const userId = getUserId()
    if (!userId) {
      list.value = []
      return
    }

    try {
      const timestamp = Date.now()
      const { ok, data } = await fetchJson(`/media/list?userId=${userId}&_t=${timestamp}`)
      if (ok && Array.isArray(data)) {
        list.value = data.reverse()
      }
    } catch (e) {
      console.error(e)
    }
  }

  const uploadFile = async (file) => {
    if (!file) return

    uploading.value = true
    uploadMessage.value = '正在上传视频文件...'

    let lastLoaded = 0
    let lastTime = Date.now()

    uploadProgress.value = {
      active: true,
      mode: 'file',
      indeterminate: false,
      percent: 0,
      loaded: 0,
      total: file.size,
      speed: 0,
      fileName: file.name,
      message: '正在上传...',
      elapsed: 0,
    }

    const formData = new FormData()
    formData.append('file', file)
    const userId = getUserId()
    if (userId) formData.append('userId', userId)

    try {
      const { ok, text } = await uploadFormData('/media/upload', formData, ({ loaded, total, percent }) => {
        const now = Date.now()
        const deltaTime = (now - lastTime) / 1000
        let speed = uploadProgress.value.speed

        if (deltaTime >= 0.25) {
          speed = Math.max(0, (loaded - lastLoaded) / deltaTime)
          lastLoaded = loaded
          lastTime = now
        }

        uploadProgress.value = {
          ...uploadProgress.value,
          loaded,
          total,
          percent,
          speed,
        }
      })

      uploadProgress.value = {
        ...uploadProgress.value,
        percent: 100,
        loaded: file.size,
        total: file.size,
        speed: 0,
      }

      if (!ok) throw new Error(text || 'Upload failed')
      success('本地上传完成')
      await fetchList()
    } catch (e) {
      error('上传失败: ' + e.message)
    } finally {
      uploading.value = false
      uploadMessage.value = ''
      resetUploadProgress()
    }
  }

  const uploadUrl = async (url) => {
    if (!url) return false

    if (!url.startsWith('http')) {
      warning('请输入合法的 http/https 链接')
      return false
    }

    uploading.value = true
    uploadMessage.value = '正在解析链接并下载...'

    uploadProgress.value = {
      active: true,
      mode: 'url',
      indeterminate: true,
      percent: 0,
      loaded: 0,
      total: 0,
      speed: 0,
      fileName: url.length > 48 ? url.slice(0, 48) + '...' : url,
      message: '服务端正在拉取资源...',
      elapsed: 0,
    }
    startElapsedTimer()

    const formData = new FormData()
    formData.append('url', url)
    const userId = getUserId()
    if (userId) formData.append('userId', userId)

    try {
      const { ok, text } = await fetchText('/media/upload-url', { method: 'POST', body: formData })
      if (!ok) throw new Error(text)
      success('链接资源已入库')
      await fetchList()
      return true
    } catch (e) {
      let errMsg = e.message
      if (errMsg.includes('Unsupported URL')) errMsg = '不支持该平台链接'
      error('解析失败: ' + errMsg)
      return false
    } finally {
      uploading.value = false
      uploadMessage.value = ''
      resetUploadProgress()
    }
  }

  const renameItem = async (item, displayName) => {
    const trimmed = (displayName || '').trim()
    if (trimmed.length > 128) {
      warning('名称不能超过 128 个字符')
      return false
    }

    try {
      const userId = getUserId()
      const formData = new FormData()
      formData.append('displayName', trimmed)
      if (userId) formData.append('userId', userId)

      const { ok, text } = await fetchText(`/media/rename?id=${item.id}`, {
        method: 'POST',
        body: formData,
      })

      if (!ok || text !== '重命名成功') {
        error(text || '重命名失败')
        return false
      }

      const idx = list.value.findIndex((i) => i.id === item.id)
      if (idx >= 0) {
        list.value[idx] = {
          ...list.value[idx],
          displayName: trimmed || null,
        }
      }

      success(trimmed ? '任务已重命名' : '已恢复为原始文件名')
      return true
    } catch {
      error('重命名请求失败')
      return false
    }
  }

  const deleteItem = async (item) => {
    try {
      const userId = getUserId()
      let path = `/media/delete?id=${item.id}`
      if (userId) path += `&userId=${userId}`

      const { ok, text } = await fetchText(path, { method: 'DELETE' })
      if (ok && text === '删除成功') {
        success('文件已删除')
        list.value = list.value.filter((i) => i.id !== item.id)
        return true
      }
      error(text || '删除失败')
      return false
    } catch {
      error('删除请求失败')
      return false
    }
  }

  const downloadAudio = async (item) => {
    const fileName = getTaskTitle(item).replace(/\.[^/.]+$/, '') + '.mp3'

    try {
      info('正在转码并下载...')
      const { ok, blob } = await fetchBlob(`/debug/download?id=${item.id}`)
      if (!ok) throw new Error('Fail')

      const downloadUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(downloadUrl)
      success('下载完成')
    } catch {
      error('下载失败，请稍后重试')
    }
  }

  const isAiSummarySuccess = (summary) => summary && summary.includes('##')
  const isAiSummaryError = (summary) => {
    if (!summary) return false
    return (
      summary.startsWith('❌') ||
      summary.includes('失败') ||
      summary.includes('Error') ||
      summary.includes('请求失败') ||
      summary.includes('Model disabled') ||
      summary.includes('FFmpeg')
    )
  }
  const TRANSCRIPT_STATUS = {
    NONE: 'NONE',
    PROCESSING: 'PROCESSING',
    OK: 'OK',
    FAILED: 'FAILED',
  }

  const inferTranscriptStatus = (item) => {
    const text = item?.transcriptText
    if (text?.startsWith('❌') || text?.startsWith('处理异常:')) return TRANSCRIPT_STATUS.FAILED
    if (text?.includes('正在提取语音') || text?.includes('正在识别')) {
      return TRANSCRIPT_STATUS.PROCESSING
    }
    if (text && text.length > 20) return TRANSCRIPT_STATUS.OK
    return TRANSCRIPT_STATUS.NONE
  }

  const getTranscriptStatus = (item) => {
    if (!item) return TRANSCRIPT_STATUS.NONE
    if (item.transcriptStatus) return item.transcriptStatus
    return inferTranscriptStatus(item)
  }

  const isTranscriptInProgress = (itemOrText) => {
    if (itemOrText && typeof itemOrText === 'object') {
      if (itemOrText.transcribing) return true
      return getTranscriptStatus(itemOrText) === TRANSCRIPT_STATUS.PROCESSING
    }
    const text = itemOrText
    return text && (text.includes('正在提取语音') || text.includes('正在识别'))
  }
  const isTranscriptError = (itemOrText) => {
    if (itemOrText && typeof itemOrText === 'object') {
      if (isTranscriptInProgress(itemOrText)) return false
      return getTranscriptStatus(itemOrText) === TRANSCRIPT_STATUS.FAILED
    }
    const text = itemOrText
    if (!text) return false
    if (isTranscriptInProgress(text)) return false
    return text.startsWith('❌') || text.startsWith('处理异常:')
  }
  const isTranscriptSuccess = (itemOrText) => {
    if (itemOrText && typeof itemOrText === 'object') {
      return getTranscriptStatus(itemOrText) === TRANSCRIPT_STATUS.OK
    }
    const text = itemOrText
    return text && text.length > 20 && !isTranscriptError(text) && !isTranscriptInProgress(text)
  }
  const isAiSummaryInProgress = (summary) => {
    if (!summary) return false
    return summary.includes('[MQ]') || summary.includes('正在分析') || summary.includes('任务已')
  }

  const setLoadingAction = (id, action, value) => {
    if (!loadingActions.value[id]) loadingActions.value[id] = {}
    loadingActions.value[id][action] = value
  }

  const isActionLoading = (id, action) => loadingActions.value[id]?.[action] ?? false

  const openSidebar = (type, title) => {
    sidebar.value.visible = true
    sidebar.value.type = type
    sidebar.value.title = title
    sidebar.value.loading = true
    sidebar.value.content = ''
  }

  const closeSidebar = () => {
    sidebar.value.visible = false
  }

  const startPolling = (id, type) => {
    if (pollingTimers.value[id]) clearInterval(pollingTimers.value[id].timer)

    const timer = setInterval(async () => {
      await fetchList()
      const item = list.value.find((i) => i.id === id)
      if (!item) return

      let isFinished = false
      let result = ''

      if (type === 'ai') {
        const text = item.aiSummary || ''
        const isSuccess = text.includes('##')
        const isErr =
          text.includes('失败') ||
          text.includes('Error') ||
          text.includes('超时') ||
          text.includes('500') ||
          text.includes('请求失败') ||
          text.includes('Model disabled')

        if (isSuccess || isErr) {
          isFinished = true
          result = text
        }
      } else if (type === 'text') {
        if (item.transcribing || getTranscriptStatus(item) === TRANSCRIPT_STATUS.PROCESSING) {
          return
        }
        const text = item.transcriptText || ''
        if (isTranscriptSuccess(item) || isTranscriptError(item)) {
          isFinished = true
          result = text
        }
      }

      if (isFinished) {
        if (
          sidebar.value.visible &&
          sidebar.value.mediaId === id &&
          sidebar.value.type === type
        ) {
          sidebar.value.content = result
          sidebar.value.loading = false
        }

        if (result.includes('失败') || result.includes('Error')) {
          warning('任务结束，但存在错误')
        } else {
          success('任务完成')
        }

        clearInterval(timer)
        delete pollingTimers.value[id]
        setLoadingAction(id, type, false)
      }
    }, 3000)

    pollingTimers.value[id] = { timer, type }

    const maxWaitMs = type === 'text' ? 120 * 60 * 1000 : 30 * 60 * 1000
    setTimeout(() => {
      if (pollingTimers.value[id]) {
        clearInterval(pollingTimers.value[id].timer)
        delete pollingTimers.value[id]
        setLoadingAction(id, type, false)
        if (
          sidebar.value.visible &&
          sidebar.value.mediaId === id &&
          sidebar.value.type === type
        ) {
          sidebar.value.loading = false
          info('轮询已超时，任务可能仍在后台运行，请稍后刷新列表或点击重新提取')
        }
      }
    }, maxWaitMs)
  }

  const transcribe = async (id, forceRetry = false) => {
    const item = list.value.find((i) => i.id === id)
    sidebar.value.mediaId = id

    if (
      !forceRetry &&
      item &&
      isTranscriptSuccess(item)
    ) {
      openSidebar('text', '全量文字提取')
      sidebar.value.content = item.transcriptText
      sidebar.value.loading = false
      return
    }

    if (!forceRetry && item && isTranscriptInProgress(item)) {
      openSidebar('text', '全量文字提取')
      sidebar.value.loading = true
      sidebar.value.content =
        (isTranscriptSuccess(item) ? item.transcriptText + '\n\n' : '') +
        '后台正在转写中，请稍候…（长视频约 30–90 分钟）'
      if (pollingTimers.value[id]?.type !== 'text') {
        startPolling(id, 'text')
      }
      return
    }

    if (!forceRetry && pollingTimers.value[id]?.type === 'text') {
      openSidebar('text', '全量文字提取')
      sidebar.value.loading = true
      sidebar.value.content = '文字提取正在后台进行中...'
      return
    }

    if (forceRetry && item && isTranscriptError(item)) {
      info('上次提取失败，正在重新提交...')
    }

    openSidebar('text', '全量文字提取')
    sidebar.value.loading = true
    sidebar.value.content = forceRetry
      ? '正在重新提取语音并识别...'
      : '提取任务已提交，正在识别语音...'
    setLoadingAction(id, 'text', true)

    try {
      const path = forceRetry
        ? `/debug/transcribe?id=${id}&force=true`
        : `/debug/transcribe?id=${id}`
      const { ok, text } = await fetchText(path)

      if (text.includes('已有完整转写')) {
        await fetchList()
        const updated = list.value.find((i) => i.id === id)
        openSidebar('text', '全量文字提取')
        sidebar.value.content = updated?.transcriptText || text
        sidebar.value.loading = false
        setLoadingAction(id, 'text', false)
        info(text)
        return
      }

      if (
        text.includes('限流') ||
        text.includes('请勿重复') ||
        text.includes('❌') ||
        !ok
      ) {
        if (!text.includes('请勿重复')) {
          warning(text || '提取任务提交失败')
        }
        sidebar.value.content = text || '提交失败'
        sidebar.value.loading = false
        setLoadingAction(id, 'text', false)
        return
      }

      startPolling(id, 'text')
      sidebar.value.content = text + '\n\n(后台处理中，长视频可能需要 30–90 分钟...)'
    } catch (e) {
      sidebar.value.content = 'Error: ' + e.message
      sidebar.value.loading = false
      setLoadingAction(id, 'text', false)
    }
  }

  const aiAnalyze = async (id, forceRetry = false) => {
    const item = list.value.find((i) => i.id === id)
    sidebar.value.mediaId = id

    if (!forceRetry && item && isAiSummarySuccess(item.aiSummary)) {
      openSidebar('ai', 'AI 智能总结')
      sidebar.value.content = item.aiSummary
      sidebar.value.loading = false
      return
    }

    if (!forceRetry && item && isAiSummaryError(item.aiSummary)) {
      info('上次分析失败，正在重新提交...')
    }

    if (
      !forceRetry &&
      item &&
      isAiSummaryInProgress(item.aiSummary) &&
      !isAiSummaryError(item.aiSummary)
    ) {
      openSidebar('ai', 'AI 智能总结')
      sidebar.value.loading = true
      sidebar.value.content = item.aiSummary + '\n\n(任务进行中，请稍候...)'
      return
    }

    if (pollingTimers.value[id]?.type === 'ai') {
      openSidebar('ai', 'AI 智能总结')
      sidebar.value.loading = true
      sidebar.value.content = '系统正在后台计算中...\n\n(任务正在进行，无需重复提交)'
      return
    }

    openSidebar('ai', 'AI 智能总结')
    sidebar.value.loading = true
    sidebar.value.content =
      forceRetry || (item && isAiSummaryError(item.aiSummary))
        ? '正在重新提交 AI 分析...'
        : '正在向集群请求计算资源...'
    setLoadingAction(id, 'ai', true)

    try {
      const { ok, text } = await fetchText(`/debug/ai?id=${id}`)

      if (
        text.includes('限流') ||
        text.includes('失败') ||
        text.includes('⚠️') ||
        text.includes('❌') ||
        !ok
      ) {
        warning(text || 'AI 分析提交失败')
        sidebar.value.visible = false
        sidebar.value.loading = false
        setLoadingAction(id, 'ai', false)
        return
      }

      startPolling(id, 'ai')
      sidebar.value.content = text + '\n\n等待消费者处理...'
    } catch (e) {
      sidebar.value.content = 'Error: ' + e.message
      sidebar.value.loading = false
      setLoadingAction(id, 'ai', false)
    }
  }

  return {
    list,
    uploading,
    uploadMessage,
    uploadProgress,
    sidebar,
    activeJobCount,
    pollingTimers,
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
    isTranscriptInProgress,
  }
}
