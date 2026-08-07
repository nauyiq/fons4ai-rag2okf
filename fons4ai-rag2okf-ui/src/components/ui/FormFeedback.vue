<script setup lang="ts">
/**
 * 统一的表单反馈组件。
 *
 * <p>支持 page/action/field/success/conflict 五种反馈层级（CR-015 AC-042）。
 * 状态不只靠颜色表达，同时包含图标和文字。
 */
withDefaults(defineProps<{
  /** 反馈类型 */
  type?: 'error' | 'success' | 'conflict' | 'warning'
  /** 反馈层级 */
  level?: 'page' | 'action' | 'field'
}>(), {
  type: 'error',
  level: 'action',
})
</script>

<template>
  <div
    class="form-feedback"
    :class="[`feedback-${type}`, `feedback-${level}`]"
    role="alert"
    :aria-live="level === 'field' ? 'polite' : 'assertive'"
  >
    <span class="feedback-icon" aria-hidden="true">
      {{ type === 'success' ? '\u2713' : type === 'conflict' ? '\u26A0' : type === 'warning' ? '\u26A0' : '\u2717' }}
    </span>
    <span class="feedback-text"><slot /></span>
  </div>
</template>

<style scoped>
.form-feedback {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}
.feedback-icon { flex-shrink: 0; font-size: 15px; }
.feedback-error { color: var(--danger); background: var(--danger-soft); }
.feedback-success { color: var(--teal); background: var(--teal-soft); }
.feedback-conflict { color: var(--amber); background: var(--amber-soft); }
.feedback-warning { color: var(--amber); background: var(--amber-soft); }
.feedback-page { padding: 12px 14px; }
.feedback-action { padding: 8px 10px; }
.feedback-field { padding: 4px 6px; font-size: 12px; }
</style>
