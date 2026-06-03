<template>
  <div
    class="spinner"
    :class="{ small: size === 'small' }"
    :style="{ width: dimension, height: dimension }"
    role="status"
    aria-label="加载中"
  />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: { type: String, default: 'medium' },
})

const dimension = computed(() => (props.size === 'small' ? '24px' : '40px'))
</script>

<style scoped>
.spinner {
  border: 3px solid var(--border-default);
  border-top-color: var(--accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.spinner.small {
  border-width: 2px;
}
</style>
