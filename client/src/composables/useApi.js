const API_BASE = import.meta.env.VITE_API_BASE || '/api'

export function useApi() {
  const buildUrl = (path) => {
    const normalized = path.startsWith('/') ? path : `/${path}`
    return `${API_BASE}${normalized}`
  }

  const fetchJson = async (path, options = {}) => {
    const res = await fetch(buildUrl(path), options)
    const data = await res.json()
    return { ok: res.ok, status: res.status, data }
  }

  const fetchText = async (path, options = {}) => {
    const res = await fetch(buildUrl(path), options)
    const text = await res.text()
    return { ok: res.ok, status: res.status, text }
  }

  const fetchBlob = async (path, options = {}) => {
    const res = await fetch(buildUrl(path), options)
    const blob = await res.blob()
    return { ok: res.ok, status: res.status, blob }
  }

  const uploadFormData = (path, formData, onProgress) =>
    new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', buildUrl(path))

      xhr.upload.addEventListener('progress', (event) => {
        if (!event.lengthComputable) return
        onProgress({
          loaded: event.loaded,
          total: event.total,
          percent: Math.min(99, Math.round((event.loaded / event.total) * 100)),
        })
      })

      xhr.addEventListener('load', () => {
        resolve({
          ok: xhr.status >= 200 && xhr.status < 300,
          status: xhr.status,
          text: xhr.responseText,
        })
      })

      xhr.addEventListener('error', () => reject(new Error('网络错误')))
      xhr.addEventListener('abort', () => reject(new Error('上传已取消')))
      xhr.send(formData)
    })

  return { API_BASE, buildUrl, fetchJson, fetchText, fetchBlob, uploadFormData }
}
