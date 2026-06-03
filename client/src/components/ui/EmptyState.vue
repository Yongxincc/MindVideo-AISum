<template>
  <div class="empty-state">
    <div class="empty-icon">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <polygon points="23 7 16 12 23 17 23 7" />
        <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
      </svg>
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p class="empty-desc">{{ description }}</p>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无视频任务' },
  description: {
    type: String,
    default: '拖拽视频文件到上传区，或粘贴 B站 / YouTube / 抖音链接开始',
  },
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-lg);
  background: rgba(24, 24, 27, 0.5);
}

.empty-icon {
  color: var(--text-muted);
  margin-bottom: 1.25rem;
  opacity: 0.6;
}

.empty-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.empty-desc {
  font-size: 0.875rem;
  color: var(--text-secondary);
  max-width: 360px;
  line-height: 1.6;
}
</style>
