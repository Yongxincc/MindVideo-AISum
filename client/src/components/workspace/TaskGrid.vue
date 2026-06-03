<template>
  <section class="workspace">
    <div class="section-header">
      <h2 class="section-title">工作台</h2>
      <span v-if="items.length > 0" class="count-chip">{{ items.length }} 个任务</span>
    </div>

    <EmptyState v-if="isLoggedIn && items.length === 0" />

    <div v-else-if="items.length > 0" class="card-grid">
      <TaskCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        :text-loading="isActionLoading(item.id, 'text')"
        :ai-loading="isActionLoading(item.id, 'ai')"
        :rename="rename"
        @delete="$emit('delete', $event)"
        @download="$emit('download', $event)"
        @transcribe="$emit('transcribe', $event)"
        @ai-analyze="$emit('ai-analyze', $event)"
      />
    </div>
  </section>
</template>

<script setup>
import TaskCard from './TaskCard.vue'
import EmptyState from '../ui/EmptyState.vue'

defineProps({
  items: { type: Array, default: () => [] },
  isLoggedIn: Boolean,
  isActionLoading: { type: Function, default: () => false },
  rename: { type: Function, default: null },
})

defineEmits(['delete', 'download', 'transcribe', 'ai-analyze'])
</script>

<style scoped>
.workspace {
  animation: slideUpFade 0.6s 0.2s forwards;
  opacity: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-subtle);
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.count-chip {
  font-size: 0.75rem;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}
</style>
