<script setup lang="ts">
/**
 * 确认弹窗组件，用于破坏性操作的二次确认。
 *
 * <p>基于 AppDialog 实现，默认启用 danger 样式和 persistent（不允许误点遮罩关闭）。
 * 确认按钮触发 confirm 事件并关闭；取消按钮触发 cancel 事件并关闭。
 * 遵循 AC-007：删除等破坏性操作必须二次确认。
 */
import { computed } from 'vue'
import AppDialog from './AppDialog.vue'

const props = withDefaults(defineProps<{
  /** 是否显示 */
  modelValue: boolean
  /** 标题 */
  title: string
  /** 描述说明 */
  description?: string
  /** 确认按钮文案，默认"确认" */
  confirmText?: string
  /** 取消按钮文案，默认"取消" */
  cancelText?: string
  /** 是否危险操作，默认 true（确认弹窗默认用于破坏性操作） */
  danger?: boolean
  /** 是否持久化（不允许遮罩/Esc 关闭），默认 true（防止误操作丢失） */
  persistent?: boolean
}>(), {
  confirmText: '确认',
  cancelText: '取消',
  danger: true,
  persistent: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

/** 确认操作：触发 confirm 并关闭弹窗 */
function handleConfirm(): void {
  emit('confirm')
  emit('update:modelValue', false)
}

/** 取消操作：触发 cancel 并关闭弹窗 */
function handleCancel(): void {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>

<template>
  <AppDialog
    v-model="visible"
    :title="title"
    :danger="danger"
    :persistent="persistent"
  >
    <header>
      <h2 id="dialog-title">{{ title }}</h2>
      <p v-if="description" class="confirm-description">{{ description }}</p>
    </header>
    <footer class="confirm-actions">
      <button
        type="button"
        class="secondary-action"
        data-testid="cancel-btn"
        @click="handleCancel"
      >
        {{ cancelText }}
      </button>
      <button
        type="button"
        class="primary-action danger-btn"
        data-testid="confirm-btn"
        @click="handleConfirm"
      >
        {{ confirmText }}
      </button>
    </footer>
  </AppDialog>
</template>

<style scoped>
.confirm-description {
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.6;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

/* 确认按钮在 danger 模式下使用红色背景强调破坏性 */
.danger-btn {
  background: var(--danger);
  box-shadow: 0 8px 20px rgb(213 72 72 / 25%);
}
</style>
