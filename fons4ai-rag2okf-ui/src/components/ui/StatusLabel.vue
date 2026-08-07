<script setup lang="ts">
/**
 * 状态标签组件，将后端状态枚举转换为安全的中文标签。
 *
 * <p>状态不只靠颜色表达，同时包含文字（CR-015 AC-043）。
 */
import { computed } from 'vue'
import { parseStatusLabel, publishStatusLabel, taskStatusLabel } from '../../utils/formatters'

const props = withDefaults(defineProps<{
  /** 状态值 */
  status: string
  /** 状态类别 */
  category?: 'parse' | 'publish' | 'task'
}>(), {
  category: 'parse',
})

const label = computed(() => {
  switch (props.category) {
    case 'publish': return publishStatusLabel(props.status)
    case 'task': return taskStatusLabel(props.status)
    default: return parseStatusLabel(props.status)
  }
})

const tone = computed(() => {
  const s = props.status
  if (['SUCCEEDED', 'PUBLISHED'].includes(s)) return 'success'
  if (['FAILED', 'PUBLISH_FAILED'].includes(s)) return 'danger'
  if (['RUNNING', 'PUBLISHING', 'QUEUED', 'PENDING'].includes(s)) return 'active'
  return 'neutral'
})
</script>

<template>
  <span class="status-chip" :class="`tone-${tone}`">{{ label }}</span>
</template>

<style scoped>
.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 99px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.tone-success { color: var(--teal); background: var(--teal-soft); }
.tone-danger { color: var(--danger); background: var(--danger-soft); }
.tone-active { color: var(--violet); background: var(--violet-soft); }
.tone-neutral { color: var(--ink-soft); background: var(--surface-muted); }
</style>
