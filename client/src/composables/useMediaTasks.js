import { ref, computed, watch } from 'vue'
import { useApi } from './useApi'
import { useToast } from './useToast'
import { getTaskTitle } from '../utils/format.js'
import { getAuthToken } from '../utils/authSession'

const CHUNK_THRESHOLD_BYTES = 50 * 1024 * 1024
const CHUNK_SIZE_BYTES = 5 * 1024 * 1024

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
  pipeline: null,
})

const qaState = ref({
  question: '',
  loading: false,
  answer: '',
  citations: [],
  history: [],
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
    if (!getUserId() || !getAuthToken()) {
      list.value = []
      return
    }

    try {
      const timestamp = Date.now()
      const { ok, data } = await fetchJson(`/media/list?_t=${timestamp}`)
      if (ok && Array.isArray(data)) {
        list.value = data.reverse()
      }
    } catch (e) {
      console.error(e)
    }
  }

  const uploadFileChunked = async (file) => {
    uploading.value = true
    uploadMessage.value = '正在分片上传...'

    const totalChunks = Math.ceil(file.size / CHUNK_SIZE_BYTES)
    let uploadId = null

    uploadProgress.value = {
      active: true,
      mode: 'file',
      indeterminate: false,
      percent: 0,
      loaded: 0,
      total: file.size,
      speed: 0,
      fileName: file.name,
      message: '初始化分片上传...',
      elapsed: 0,
    }
    startElapsedTimer()

    try {
      const initRes = await fetchText('/media/init-upload', { method: 'POST' })
      if (!initRes.ok) throw new Error(initRes.text || 'init-upload failed')
      uploadId = initRes.text.trim()

      let statusRes = await fetchJson(`/media/upload-status?uploadId=${encodeURIComponent(uploadId)}`)
      const uploadedSet = new Set(statusRes.data?.uploadedChunks || [])

      for (let i = 0; i < totalChunks; i++) {
        if (uploadedSet.has(i)) continue

        const start = i * CHUNK_SIZE_BYTES
        const end = Math.min(file.size, start + CHUNK_SIZE_BYTES)
        const blob = file.slice(start, end)

        const formData = new FormData()
        formData.append('uploadId', uploadId)
        formData.append('chunkIndex', String(i))
        formData.append('totalChunks', String(totalChunks))
        formData.append('file', blob, `${file.name}.part${i}`)

        const { ok, text } = await uploadFormData('/media/upload-chunk', formData)
        if (!ok) throw new Error(text || `chunk ${i} failed`)

        uploadedSet.add(i)
        const loaded = Math.min(file.size, (i + 1) * CHUNK_SIZE_BYTES)
        uploadProgress.value = {
          ...uploadProgress.value,
          loaded,
          percent: Math.min(99, Math.round((loaded / file.size) * 100)),
          message: `分片 ${i + 1}/${totalChunks}`,
        }
      }

      const mergeData = new FormData()
      mergeData.append('uploadId', uploadId)
      mergeData.append('filename', file.name)

      const mergeRes = await fetchJson('/media/merge-chunks', { method: 'POST', body: mergeData })
      if (!mergeRes.ok) throw new Error(mergeRes.data?.message || 'merge failed')

      uploadProgress.value = { ...uploadProgress.value, percent: 100, loaded: file.size }
      success('分片上传完成')
      await fetchList()
    } catch (e) {
      error('分片上传失败: ' + e.message)
    } finally {
      uploading.value = false
      uploadMessage.value = ''
      resetUploadProgress()
    }
  }

  const uploadFile = async (file) => {
    if (!file) return

    if (file.size > CHUNK_THRESHOLD_BYTES) {
      return uploadFileChunked(file)
    }

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
      const formData = new FormData()
      formData.append('displayName', trimmed)

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
      const { ok, text } = await fetchText(`/media/delete?id=${item.id}`, { method: 'DELETE' })
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
    return (
      summary.includes('[MQ]') ||
      summary.includes('正在分析') ||
      summary.includes('等待调度')
    )
  }

  const STALE_PIPELINE_MS = 15 * 60 * 1000

  const isAiSummaryStaleProgress = async (id, summary) => {
    if (!isAiSummaryInProgress(summary)) return false
    const pipeline = await fetchPipelineStatus(id)
    if (!pipeline) return true
    const running =
      !!pipeline.currentStage ||
      pipeline.stages?.some((s) => s.status === 'running')
    if (!running) return true
    if (pipeline.updatedAt && Date.now() - pipeline.updatedAt > STALE_PIPELINE_MS) {
      return true
    }
    return false
  }

  const isTranscriptActuallyRunning = (pipeline) => {
    if (!pipeline) return false
    if (pipeline.updatedAt && Date.now() - pipeline.updatedAt > STALE_PIPELINE_MS) {
      return false
    }
    const codes = ['TRANSCRIPT_ASR', 'AUDIO_EXTRACT']
    if (codes.includes(pipeline.currentStage)) return true
    return pipeline.stages?.some(
      (s) => codes.includes(s.code) && s.status === 'running'
    )
  }

  const isTranscriptStaleProgress = async (id, item) => {
    const inProgress =
      item?.transcribing ||
      getTranscriptStatus(item) === TRANSCRIPT_STATUS.PROCESSING
    if (!inProgress) return false
    const pipeline = await fetchPipelineStatus(id)
    if (!pipeline) return true
    return !isTranscriptActuallyRunning(pipeline)
  }

  const isAiSummaryTerminal = (summary) => {
    if (!summary || summary.length < 8) return false
    if (isAiSummaryInProgress(summary)) return false
    if (isAiSummarySuccess(summary) || isAiSummaryError(summary)) return true
    if (summary.startsWith('⚠️')) return true
    if (summary.includes('核心摘要')) return true
    if (summary.includes('无法提取有效信息')) return true
    return summary.length > 200 && !summary.includes('[MQ]')
  }

  const setLoadingAction = (id, action, value) => {
    if (!loadingActions.value[id]) loadingActions.value[id] = {}
    loadingActions.value[id][action] = value
  }

  const isActionLoading = (id, action) => loadingActions.value[id]?.[action] ?? false

  const fetchPipelineStatus = async (id) => {
    if (!id) return null
    try {
      const { ok, data } = await fetchJson(`/debug/pipeline?id=${id}&_t=${Date.now()}`)
      if (!ok) return null
      if (data?.stages?.length || data?.currentStage) {
        return data
      }
    } catch {
      // 后端未启动时静默，避免控制台刷屏
    }
    return null
  }

  const loadAskHistory = async (mediaId) => {
    if (!mediaId) {
      qaState.value.history = []
      return
    }
    try {
      const { ok, data } = await fetchJson(
        `/media/ask-history?mediaId=${mediaId}&_t=${Date.now()}`
      )
      qaState.value.history = ok && Array.isArray(data) ? data : []
    } catch {
      qaState.value.history = []
    }
  }

  const deleteQaHistoryItem = async (messageId) => {
    const mediaId = sidebar.value.mediaId
    if (!mediaId || !messageId) return
    try {
      const { ok } = await fetchJson(
        `/media/ask-history?mediaId=${mediaId}&messageId=${messageId}`,
        { method: 'DELETE' }
      )
      if (!ok) {
        error('删除问答记录失败')
        return
      }
      qaState.value.history = qaState.value.history.filter((m) => m.id !== messageId)
      success('已删除该条问答')
    } catch {
      error('删除问答记录失败')
    }
  }

  const clearQaHistory = async () => {
    const mediaId = sidebar.value.mediaId
    if (!mediaId || !qaState.value.history?.length) return
    if (!window.confirm('确定清空该视频的全部问答记录吗？此操作不可撤销。')) return
    try {
      const { ok } = await fetchJson(`/media/ask-history?mediaId=${mediaId}`, {
        method: 'DELETE',
      })
      if (!ok) {
        error('清空问答记录失败')
        return
      }
      qaState.value.history = []
      success('问答记录已清空')
    } catch {
      error('清空问答记录失败')
    }
  }

  const openSidebar = (type, title) => {
    sidebar.value.visible = true
    sidebar.value.type = type
    sidebar.value.title = title
    sidebar.value.loading = true
    sidebar.value.content = ''
    sidebar.value.pipeline = null
    qaState.value = {
      question: '',
      loading: false,
      answer: '',
      citations: [],
      history: qaState.value.history,
    }
  }

  const closeSidebar = () => {
    sidebar.value.visible = false
    qaState.value = {
      question: '',
      loading: false,
      answer: '',
      citations: [],
      history: [],
    }
  }

  const pollAskStatus = async (mediaId, maxAttempts = 180) => {
    for (let i = 0; i < maxAttempts; i++) {
      const { ok, data } = await fetchJson(
        `/media/ask-status?mediaId=${mediaId}&_t=${Date.now()}`
      )
      if (!ok) throw new Error(data?.message || '查询问答状态失败')
      const status = data?.status
      if (status === 'DONE') {
        return data
      }
      if (status === 'FAILED') {
        throw new Error(data?.answer || data?.message || '问答失败')
      }
      await new Promise((r) => setTimeout(r, 2000))
    }
    throw new Error('问答超时（首次提问可能需 1–3 分钟建立向量索引），请稍后重试')
  }

  const askVideo = async () => {
    const mediaId = sidebar.value.mediaId
    const question = qaState.value.question?.trim()
    if (!mediaId || !question) {
      warning('请输入问题')
      return
    }

    qaState.value.loading = true
    qaState.value.answer = ''
    qaState.value.citations = []

    try {
      const { ok, status, data } = await fetchJson('/media/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mediaId, question }),
      })
      if (!ok && status !== 202) throw new Error(data?.message || '问答提交失败')
      const result = await pollAskStatus(mediaId)
      qaState.value.question = ''
      qaState.value.answer = ''
      qaState.value.citations = []
      await loadAskHistory(mediaId)
    } catch (e) {
      qaState.value.answer = '❌ ' + e.message
    } finally {
      qaState.value.loading = false
    }
  }

  const canShowQa = (item) => {
    if (!item) return false
    return isTranscriptSuccess(item.transcriptText) || item.transcriptStatus === 'OK'
  }

  watch(
    () =>
      sidebar.value.visible && sidebar.value.mediaId
        ? sidebar.value.mediaId
        : null,
    (mediaId) => {
      if (!mediaId) return
      const item = list.value.find((i) => i.id === mediaId)
      if (canShowQa(item)) {
        loadAskHistory(mediaId)
      }
    }
  )

  const startPolling = (id, type) => {
    if (pollingTimers.value[id]) {
      clearInterval(pollingTimers.value[id].timer)
      if (pollingTimers.value[id].pipelineTimer) {
        clearInterval(pollingTimers.value[id].pipelineTimer)
      }
    }

    const refreshPipeline = async () => {
      const pipeline = await fetchPipelineStatus(id)
      if (
        pipeline &&
        sidebar.value.visible &&
        sidebar.value.mediaId === id
      ) {
        sidebar.value.pipeline = pipeline
      }
    }
    refreshPipeline()
    const pipelineTimer = setInterval(refreshPipeline, 4000)

    const timer = setInterval(async () => {
      await fetchList()
      const item = list.value.find((i) => i.id === id)
      if (!item) return

      let isFinished = false
      let result = ''

      if (type === 'ai') {
        const text = item.aiSummary || ''
        if (isAiSummaryTerminal(text)) {
          isFinished = true
          result = text
        } else if (
          sidebar.value.visible &&
          sidebar.value.mediaId === id &&
          sidebar.value.type === 'ai' &&
          isAiSummaryInProgress(text)
        ) {
          sidebar.value.content =
            text + '\n\n(AI 分析中，长视频约需 5–15 分钟，请勿关闭页面…)'
        }
      } else if (type === 'text') {
        if (item.transcribing || getTranscriptStatus(item) === TRANSCRIPT_STATUS.PROCESSING) {
          const stale = await isTranscriptStaleProgress(id, item)
          if (stale) {
            clearInterval(timer)
            clearInterval(pipelineTimer)
            delete pollingTimers.value[id]
            setLoadingAction(id, type, false)
            if (
              sidebar.value.visible &&
              sidebar.value.mediaId === id &&
              sidebar.value.type === type
            ) {
              sidebar.value.loading = false
              sidebar.value.content =
                '❌ 上次转写已中断，请点击「重新提取」重试'
            }
            warning('转写任务已中断，请重新点击「提取文字」')
            return
          }
          if (
            sidebar.value.visible &&
            sidebar.value.mediaId === id &&
            sidebar.value.type === type
          ) {
            const pipeline = await fetchPipelineStatus(id)
            if (pipeline) sidebar.value.pipeline = pipeline
          }
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
        clearInterval(pipelineTimer)
        delete pollingTimers.value[id]
        setLoadingAction(id, type, false)
      }
    }, 3000)

    pollingTimers.value[id] = { timer, pipelineTimer, type }

    const maxWaitMs = type === 'text' ? 120 * 60 * 1000 : 30 * 60 * 1000
    setTimeout(() => {
      if (pollingTimers.value[id]) {
        clearInterval(pollingTimers.value[id].timer)
        if (pollingTimers.value[id].pipelineTimer) {
          clearInterval(pollingTimers.value[id].pipelineTimer)
        }
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
      const stale = await isTranscriptStaleProgress(id, item)
      if (stale) {
        info('检测到上次转写已中断，正在重新提交...')
        return transcribe(id, true)
      }
      openSidebar('text', '全量文字提取')
      sidebar.value.loading = true
      sidebar.value.content =
        (isTranscriptSuccess(item) ? item.transcriptText + '\n\n' : '') +
        '后台正在转写中，请稍候…（2 小时视频约 5–10 分钟）'
      if (pollingTimers.value[id]?.type !== 'text') {
        startPolling(id, 'text')
      } else {
        fetchPipelineStatus(id).then((p) => {
          if (p) sidebar.value.pipeline = p
        })
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

      if (text.includes('⚠️') && text.includes('已在后台运行')) {
        const stale = await isTranscriptStaleProgress(id, item)
        if (stale) {
          info('检测到僵死转写状态，正在重新提交...')
          return transcribe(id, true)
        }
      }

      if (
        text.includes('限流') ||
        text.includes('请勿重复') ||
        text.includes('⚠️') ||
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
      sidebar.value.content = text + '\n\n(后台处理中，2 小时视频约 5–10 分钟...)'
    } catch (e) {
      sidebar.value.content = 'Error: ' + e.message
      sidebar.value.loading = false
      setLoadingAction(id, 'text', false)
    }
  }

  const aiAnalyze = async (id, forceRetry = false) => {
    let retry = forceRetry
    const item = list.value.find((i) => i.id === id)
    sidebar.value.mediaId = id

    if (!retry && item && isAiSummarySuccess(item.aiSummary)) {
      openSidebar('ai', 'AI 智能总结')
      sidebar.value.content = item.aiSummary
      sidebar.value.loading = false
      return
    }

    if (!retry && item && isAiSummaryError(item.aiSummary)) {
      info('上次分析失败，正在重新提交...')
      retry = true
    }

    if (
      !retry &&
      item &&
      isAiSummaryInProgress(item.aiSummary) &&
      !isAiSummaryError(item.aiSummary)
    ) {
      const stale = await isAiSummaryStaleProgress(id, item.aiSummary)
      if (stale) {
        info('检测到上次分析已中断，正在重新提交...')
        retry = true
      } else {
        openSidebar('ai', 'AI 智能总结')
        sidebar.value.loading = true
        sidebar.value.content =
          item.aiSummary + '\n\n(任务进行中，请稍候…长视频约需 5–15 分钟)'
        if (pollingTimers.value[id]?.type !== 'ai') {
          startPolling(id, 'ai')
        } else {
          fetchPipelineStatus(id).then((p) => {
            if (p) sidebar.value.pipeline = p
          })
        }
        return
      }
    }

    if (!retry && pollingTimers.value[id]?.type === 'ai') {
      openSidebar('ai', 'AI 智能总结')
      sidebar.value.loading = true
      sidebar.value.content = '系统正在后台计算中...\n\n(任务正在进行，无需重复提交)'
      return
    }

    openSidebar('ai', 'AI 智能总结')
    sidebar.value.loading = true
    sidebar.value.content = retry
        ? '正在重新提交 AI 分析...'
        : '正在向集群请求计算资源...'
    setLoadingAction(id, 'ai', true)

    try {
      const aiPath = retry
        ? `/debug/ai?id=${id}&force=true`
        : `/debug/ai?id=${id}`
      const { ok, text } = await fetchText(aiPath)

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
    qaState,
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
    askVideo,
    deleteQaHistoryItem,
    clearQaHistory,
    canShowQa,
    closeSidebar,
    isActionLoading,
    isAiSummaryError,
    isTranscriptError,
    isTranscriptSuccess,
    isTranscriptInProgress,
  }
}
