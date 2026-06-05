import { ref } from 'vue'
import { useApi } from './useApi'
import { useToast } from './useToast'
import {
  clearAuthSession,
  getAuthToken,
  setAuthToken,
  setUnauthorizedHandler,
} from '../utils/authSession'

const currentUser = ref(null)
const showAuthModal = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const authMessage = ref('')
const authError = ref(false)
const authForm = ref({ username: '', password: '', nickname: '' })

export function useAuth() {
  const { fetchJson } = useApi()
  const { success, error: toastError } = useToast()

  const openAuthModal = () => {
    showAuthModal.value = true
    authMessage.value = ''
    authForm.value = { username: '', password: '', nickname: '' }
  }

  const closeAuthModal = () => {
    showAuthModal.value = false
  }

  const switchAuthMode = () => {
    authMode.value = authMode.value === 'login' ? 'register' : 'login'
    authMessage.value = ''
  }

  const clearSession = () => {
    currentUser.value = null
    clearAuthSession()
  }

  setUnauthorizedHandler(() => {
    clearSession()
    toastError('登录已过期，请重新登录')
    openAuthModal()
  })

  const handleAuth = async (onLoginSuccess) => {
    if (!authForm.value.username || !authForm.value.password) {
      authMessage.value = '请输入完整的账号和密码'
      authError.value = true
      return
    }

    authLoading.value = true
    authMessage.value = ''

    const endpoint = authMode.value === 'login' ? '/user/login' : '/user/register'

    try {
      const { ok, data } = await fetchJson(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(authForm.value),
      })

      if (ok && data.code === 200) {
        if (authMode.value === 'login') {
          currentUser.value = data.userInfo
          setAuthToken(data.token)
          localStorage.setItem('user', JSON.stringify(data.userInfo))
          closeAuthModal()
          success(`欢迎回来，${data.userInfo.nickname}`)
          onLoginSuccess?.()
        } else {
          authMessage.value = '注册成功，请直接登录'
          authError.value = false
          setTimeout(() => switchAuthMode(), 1000)
        }
      } else {
        authMessage.value = data.msg || '操作失败'
        authError.value = true
      }
    } catch {
      authMessage.value = '网络连接错误'
      authError.value = true
    } finally {
      authLoading.value = false
    }
  }

  const logout = (onLogout) => {
    clearSession()
    onLogout?.()
    success('已退出系统')
  }

  const requireAuth = () => {
    if (!currentUser.value || !getAuthToken()) {
      toastError('请先登录后再操作')
      openAuthModal()
      return false
    }
    return true
  }

  const initAuth = async () => {
    const savedUser = localStorage.getItem('user')
    const token = getAuthToken()
    if (!savedUser || !token) {
      clearSession()
      return
    }

    try {
      currentUser.value = JSON.parse(savedUser)
      const { ok, data } = await fetchJson('/user/me')
      if (ok && data.code === 200) {
        currentUser.value = data.userInfo
        localStorage.setItem('user', JSON.stringify(data.userInfo))
        return
      }
      clearSession()
    } catch {
      clearSession()
    }
  }

  return {
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
    requireAuth,
    initAuth,
  }
}
