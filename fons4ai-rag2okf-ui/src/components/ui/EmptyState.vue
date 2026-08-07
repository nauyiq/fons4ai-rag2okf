<script setup lang="ts">
/**
 * 空状态展示组件。
 *
 * <p>统一空数据、筛选无结果、错误和加载骨架的空态呈现（CR-015 AC-043）。
 */
withDefaults(defineProps<{
  /** 空态类型 */
  variant?: 'empty' | 'no-results' | 'error'
  /** 图标符号 */
  icon?: string
  /** 标题 */
  title?: string
  /** 描述 */
  description?: string
}>(), {
  variant: 'empty',
  icon: '\u2500',
  title: '',
  description: '',
})
</script>

<template>
  <div class="empty-panel" :class="`empty-${variant}`">
    <span aria-hidden="true">{{ icon }}</span>
    <strong>{{ title || (variant === 'no-results' ? '当前筛选无结果' : variant === 'error' ? '加载失败' : '暂无数据') }}</strong>
    <p v-if="description">{{ description }}</p>
    <p v-else-if="variant === 'no-results'">尝试调整筛选条件或清除搜索</p>
    <p v-else-if="variant === 'error'">请稍后重试或刷新页面</p>
    <slot />
  </div>
</template>
