<script setup lang="ts">
/**
 * 基于 ant-design-vue a-modal 封装的可访问模态弹窗组件。
 *
 * <p>保留原有 props/emits：modelValue、title、danger、size、persistent。
 * 焦点管理与 Esc 处理交由 a-modal 内置实现；
 * persistent 控制遮罩点击与 Esc 是否可关闭。
 * 关闭（遮罩/Esc）时 emit update:modelValue:false 和 cancel。
 * 调用方通过默认 slot 自带操作按钮，因此隐藏 a-modal 默认 footer。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 是否显示 */
  modelValue: boolean
  /** 标题，传给 a-modal 的 title */
  title?: string
  /** 是否危险操作（追加 danger 样式：左侧红色强调边） */
  danger?: boolean
  /** 弹窗宽度尺寸：sm/md/lg */
  size?: 'sm' | 'md' | 'lg'
  /** 是否持久化（不允许遮罩/Esc 关闭），默认 false */
  persistent?: boolean
}>(), {
  danger: false,
  size: 'md',
  persistent: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  cancel: []
}>()

const sizeWidthMap: Record<'sm' | 'md' | 'lg', number> = {
  sm: 420,
  md: 560,
  lg: 820,
}

const modalWidth = computed(() => sizeWidthMap[props.size])

const wrapClassName = computed(() => {
  const classes = ['app-dialog-wrap']
  if (props.danger) classes.push('app-dialog-danger')
  return classes.join(' ')
})

/**
 * a-modal 取消回调：遮罩点击 / Esc 触发。
 * maskClosable 与 keyboard 已按 persistent 屏蔽，此处保留 guard 作为安全网。
 */
function handleCancel(): void {
  if (props.persistent) return
  emit('update:modelValue', false)
  emit('cancel')
}
</script>

<template>
  <a-modal
    :open="modelValue"
    :title="title"
    :width="modalWidth"
    :centered="true"
    :closable="false"
    :mask-closable="!persistent"
    :keyboard="!persistent"
    :footer="null"
    :wrap-class-name="wrapClassName"
    :destroy-on-close="true"
    @cancel="handleCancel"
  >
    <slot />
  </a-modal>
</template>

<style>
/* 危险操作样式：a-modal 通过 teleport 挂载到 body，scoped 样式无法命中内部 DOM，
   故用全局规则配合 wrapClassName 定位 modal 内容盒，追加左侧红色强调边。 */
.app-dialog-danger .ant-modal-content {
  border-left: 4px solid var(--danger);
}
</style>
