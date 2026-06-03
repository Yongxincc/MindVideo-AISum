<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="auth-backdrop" @click.self="$emit('close')">
        <div class="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
          <div class="auth-header">
            <h2 id="auth-title" class="auth-title">
              {{ mode === 'login' ? '用户登录' : '新用户注册' }}
            </h2>
            <button class="close-btn" aria-label="关闭" @click="$emit('close')">×</button>
          </div>

          <div class="auth-body">
            <div class="input-group">
              <label for="auth-username">账号</label>
              <input
                id="auth-username"
                v-model="form.username"
                type="text"
                placeholder="输入账号"
                autocomplete="username"
              />
            </div>

            <div class="input-group">
              <label for="auth-password">密码</label>
              <div class="password-row">
                <input
                  id="auth-password"
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="输入密码"
                  autocomplete="current-password"
                />
                <button
                  type="button"
                  class="toggle-pwd"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <svg v-if="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                    <line x1="1" y1="1" x2="23" y2="23" />
                  </svg>
                  <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                </button>
              </div>
            </div>

            <div v-if="mode === 'register'" class="input-group">
              <label for="auth-nickname">昵称</label>
              <input
                id="auth-nickname"
                v-model="form.nickname"
                type="text"
                placeholder="设置一个好听的名字"
              />
            </div>

            <button class="submit-btn" :disabled="loading" @click="$emit('submit')">
              <LoadingSpinner v-if="loading" size="small" />
              <span v-else>{{ mode === 'login' ? '立即登录' : '提交注册' }}</span>
            </button>

            <div class="auth-toggle">
              <span>{{ mode === 'login' ? '没有账号?' : '已有账号?' }}</span>
              <button type="button" class="toggle-link" @click="$emit('switch-mode')">
                {{ mode === 'login' ? '去注册' : '去登录' }}
              </button>
            </div>

            <p v-if="message" class="auth-msg" :class="{ error: isError }">{{ message }}</p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import LoadingSpinner from '../ui/LoadingSpinner.vue'

const props = defineProps({
  visible: Boolean,
  mode: { type: String, default: 'login' },
  form: { type: Object, required: true },
  loading: Boolean,
  message: { type: String, default: '' },
  isError: Boolean,
})

defineEmits(['close', 'submit', 'switch-mode'])

const showPassword = ref(false)

watch(
  () => props.visible,
  (open) => {
    document.body.classList.toggle('scroll-locked', open)
    if (!open) showPassword.value = false
  }
)
</script>

<style scoped>
.auth-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(6px);
  z-index: 2000;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1rem;
}

.auth-panel {
  width: 100%;
  max-width: 400px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.auth-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.auth-title {
  font-size: 1.125rem;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 1.5rem;
  line-height: 1;
  transition: color 0.2s;
}

.close-btn:hover {
  color: var(--text-primary);
}

.auth-body {
  padding: 1.5rem;
}

.input-group {
  margin-bottom: 1.25rem;
}

.input-group label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.input-group input {
  width: 100%;
  background: var(--bg-base);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 0.625rem 0.875rem;
  color: var(--text-primary);
  font-size: 0.875rem;
  transition: border-color 0.2s;
}

.input-group input:focus {
  border-color: var(--accent-primary);
}

.password-row {
  display: flex;
  align-items: center;
  position: relative;
}

.password-row input {
  padding-right: 2.5rem;
}

.toggle-pwd {
  position: absolute;
  right: 0.5rem;
  background: none;
  border: none;
  color: var(--text-muted);
  padding: 0.25rem;
  display: flex;
}

.toggle-pwd:hover {
  color: var(--text-secondary);
}

.submit-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: var(--accent-primary);
  color: #fff;
  border: none;
  padding: 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 1rem;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: var(--accent-primary-hover);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-toggle {
  text-align: center;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.toggle-link {
  background: none;
  border: none;
  color: var(--accent-primary);
  font-weight: 500;
  margin-left: 0.25rem;
}

.toggle-link:hover {
  text-decoration: underline;
}

.auth-msg {
  margin-top: 1rem;
  text-align: center;
  font-size: 0.8rem;
  color: var(--accent-success);
}

.auth-msg.error {
  color: var(--accent-danger);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
