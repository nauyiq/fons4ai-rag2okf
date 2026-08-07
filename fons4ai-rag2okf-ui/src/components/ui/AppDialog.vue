<script setup lang="ts">
/**
 * 可访问的中央模态弹窗组件。
 *
 * <p>提供焦点陷阱、Esc 关闭、焦点返回和 backdrop 点击关闭。
 * 升级点：新增 size（sm/md/lg）控制宽度；新增 persistent 控制是否允许遮罩/Esc 关闭；
 * 弹窗居中显示（替代原右侧抽屉布局）。
 * 遵循 CR-015 AC-045：Dialog/Drawer 焦点、Esc、焦点返回。
 */
import { ref, watch, onBeforeUnmount, nextTick, computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 是否显示 */
  modelValue: boolean
  /** 标题，用于 aria-labelledby */
  title?: string
  /** 是否危险操作（添加 danger 样式） */
  danger?: boolean
  /** 弹窗宽度尺寸：sm/md/lg */
  size?: 'sm' | 'md' | 'lg'
  /** 是否持久化（不允许遮罩点击和 Esc 关闭），默认 false */
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

const dialogRef = ref<HTMLElement | null>(null)
let previouslyFocused: HTMLElement | null = null

const panelClass = computed(() => ({
  'modal-panel': true,
  'danger-modal': props.danger,
  [`size-${props.size}`]: true,
}))

/** 是否允许通过遮罩/Esc 关闭。persistent 为 true 时禁止关闭。 */
function canDismiss(): boolean {
  return !props.persistent
}

function close() {
  if (!canDismiss()) return
  emit('update:modelValue', false)
  emit('cancel')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && canDismiss()) {
    event.stopPropagation()
    close()
    return
  }
  if (event.key === 'Tab' && dialogRef.value) {
    trapFocus(event)
  }
}

function trapFocus(event: KeyboardEvent) {
  if (!dialogRef.value) return
  const focusable = dialogRef.value.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  )
  if (focusable.length === 0) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (visible) {
      previouslyFocused = document.activeElement as HTMLElement
      await nextTick()
      if (dialogRef.value) {
        const first = dialogRef.value.querySelector<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        first?.focus()
      }
      document.addEventListener('keydown', onKeydown, true)
    } else {
      document.removeEventListener('keydown', onKeydown, true)
      previouslyFocused?.focus()
      previouslyFocused = null
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
})
</script>

<template>
  <div
    v-if="modelValue"
    class="drawer-backdrop"
    role="presentation"
    @click.self="close"
  >
    <section
      ref="dialogRef"
      :class="panelClass"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="title ? 'dialog-title' : undefined"
    >
      <slot />
    </section>
  </div>
</template>
