<template>
  <Teleport to="body">
    <div class="toast-container" aria-live="polite">
      <TransitionGroup name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast"
          :class="toast.type"
          role="alert"
        >
          <span class="toast-icon">{{ icons[toast.type] }}</span>
          <span class="toast-message">{{ toast.message }}</span>
          <button class="toast-close" aria-label="关闭" @click="dismiss(toast.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToast } from '../../composables/useToast'

const { toasts, dismiss } = useToast()

const icons = {
  success: '✓',
  error: '✕',
  warning: '!',
  info: 'i',
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 1.25rem;
  right: 1.25rem;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-width: 380px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  box-shadow: var(--shadow-md);
  pointer-events: auto;
  backdrop-filter: blur(12px);
}

.toast.success {
  border-color: rgba(52, 211, 153, 0.4);
}
.toast.success .toast-icon {
  color: var(--accent-success);
}

.toast.error {
  border-color: rgba(248, 113, 113, 0.4);
}
.toast.error .toast-icon {
  color: var(--accent-danger);
}

.toast.warning {
  border-color: rgba(251, 191, 36, 0.4);
}
.toast.warning .toast-icon {
  color: var(--accent-warning);
}

.toast.info .toast-icon {
  color: var(--accent-primary);
}

.toast-icon {
  font-weight: 700;
  font-size: 0.875rem;
  width: 1.25rem;
  text-align: center;
  flex-shrink: 0;
}

.toast-message {
  flex: 1;
  font-size: 0.875rem;
  color: var(--text-primary);
  line-height: 1.4;
}

.toast-close {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 1.25rem;
  line-height: 1;
  padding: 0 0.25rem;
  transition: color 0.2s;
}

.toast-close:hover {
  color: var(--text-primary);
}

.toast-enter-active {
  animation: toastSlideIn 0.3s ease;
}
.toast-leave-active {
  animation: toastSlideOut 0.25s ease;
}
</style>
