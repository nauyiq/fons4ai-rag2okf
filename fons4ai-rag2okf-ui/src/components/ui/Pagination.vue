<script setup lang="ts">
/**
 * 分页组件。
 *
 * <p>使用后端返回的 total 进行真实分页，不伪造总数（CR-015 AC-043）。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 当前页码（从 0 开始） */
  page: number
  /** 每页条数 */
  size: number
  /** 总记录数 */
  total: number
}>(), {
  page: 0,
  size: 20,
  total: 0,
})

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const hasNext = computed(() => props.page < totalPages.value - 1)
const hasPrev = computed(() => props.page > 0)

function goNext() {
  if (hasNext.value) emit('update:page', props.page + 1)
}
function goPrev() {
  if (hasPrev.value) emit('update:page', props.page - 1)
}
</script>

<template>
  <nav v-if="total > 0" class="pagination" aria-label="分页">
    <span class="pagination-total">共 {{ total }} 条</span>
    <div class="pagination-controls">
      <button
        class="pagination-btn"
        :disabled="!hasPrev"
        aria-label="上一页"
        @click="goPrev"
      >
        上一页
      </button>
      <span class="pagination-info">
        第 {{ page + 1 }} / {{ totalPages }} 页
      </span>
      <button
        class="pagination-btn"
        :disabled="!hasNext"
        aria-label="下一页"
        @click="goNext"
      >
        下一页
      </button>
    </div>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.pagination-total {
  color: var(--ink-soft);
  font-size: 13px;
}
.pagination-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pagination-btn {
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink);
  background: var(--surface);
  font-size: 13px;
}
.pagination-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}
.pagination-info {
  color: var(--ink-soft);
  font-size: 13px;
}
</style>
