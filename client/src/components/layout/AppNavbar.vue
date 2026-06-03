<template>
  <header class="navbar">
    <div class="nav-content">
      <div class="brand">
        <span class="brand-name">Mind</span><span class="brand-accent">Video</span>
      </div>

      <div class="nav-controls">
        <button v-if="!currentUser" class="auth-btn" @click="$emit('open-auth')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
            <circle cx="12" cy="7" r="4" />
          </svg>
          登录 / 注册
        </button>

        <div v-else class="user-profile">
          <span class="user-name">{{ currentUser.nickname }}</span>
          <button class="logout-btn" aria-label="退出登录" @click="$emit('logout')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
        </div>

        <div class="status-pill" :class="{ 'is-active': activeJobCount > 0 }">
          <div class="status-dot" />
          <span class="status-text">
            {{ statusText }}
          </span>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentUser: { type: Object, default: null },
  activeJobCount: { type: Number, default: 0 },
  uploading: { type: Boolean, default: false },
})

defineEmits(['open-auth', 'logout'])

const statusText = computed(() => {
  if (props.uploading) return '上传中...'
  if (props.activeJobCount > 0) return `${props.activeJobCount} 个任务进行中`
  return '系统就绪'
})
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  padding: 1rem 0;
  background: var(--bg-overlay);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border-subtle);
}

.nav-content {
  max-width: var(--max-width-nav);
  margin: 0 auto;
  padding: 0 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 0;
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.brand-name {
  color: var(--text-primary);
}

.brand-accent {
  color: var(--accent-primary);
}

.nav-controls {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.auth-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: transparent;
  border: 1px solid var(--border-default);
  color: var(--text-primary);
  padding: 0.4rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s;
}

.auth-btn:hover {
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-name {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.logout-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  padding: 0.25rem;
  display: flex;
  transition: color 0.2s;
}

.logout-btn:hover {
  color: var(--accent-danger);
}

.status-pill {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--bg-surface);
  padding: 0.35rem 0.75rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--accent-success);
  border-radius: 50%;
}

.status-pill.is-active .status-dot {
  background: var(--accent-primary);
  animation: pulseGlow 1.5s infinite alternate;
}

@media (max-width: 480px) {
  .nav-content {
    padding: 0 1rem;
  }

  .status-text {
    display: none;
  }

  .status-pill {
    padding: 0.35rem;
  }
}
</style>
