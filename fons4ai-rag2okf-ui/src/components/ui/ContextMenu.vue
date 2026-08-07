<script setup lang="ts">
/**
 * 右键上下文菜单组件。
 *
 * <p>定位跟随鼠标坐标，点击菜单项触发 select 事件并关闭，点击外部关闭。
 * 用于文档工作台右键创建目录、知识库卡片操作菜单等场景。
 * 遵循 AC-010：右键出现创建目录选项。
 */
export interface ContextMenuItem {
  /** 菜单项唯一标识 */
  key: string
  /** 显示文案 */
  label: string
  /** 是否禁用 */
  disabled?: boolean
  /** 是否危险操作（红色样式） */
  danger?: boolean
}

const props = defineProps<{
  /** 是否显示 */
  visible: boolean
  /** 菜单 x 坐标（屏幕坐标） */
  x: number
  /** 菜单 y 坐标（屏幕坐标） */
  y: number
  /** 菜单项列表 */
  items: ContextMenuItem[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  select: [key: string]
}>()

/** 点击菜单项：触发 select 并关闭 */
function handleSelect(item: ContextMenuItem): void {
  if (item.disabled) return
  emit('select', item.key)
  emit('update:visible', false)
}

/** 点击外部关闭 */
function close(): void {
  emit('update:visible', false)
}
</script>

<template>
  <div
    v-if="visible"
    class="context-menu-backdrop"
    @click="close"
    @contextmenu.prevent="close"
  >
    <div
      class="context-menu"
      :style="{ left: x + 'px', top: y + 'px' }"
      @click.stop
      @contextmenu.prevent.stop
    >
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="context-menu-item"
        :class="{ disabled: item.disabled, danger: item.danger }"
        :disabled="item.disabled"
        @click="handleSelect(item)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.context-menu-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
}

.context-menu {
  position: absolute;
  z-index: 31;
  min-width: 160px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.context-menu-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  border-radius: 7px;
  color: var(--ink);
  font-size: 13px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.context-menu-item:hover:not(.disabled) {
  background: var(--surface-muted);
}

.context-menu-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.context-menu-item.danger {
  color: var(--danger);
}

.context-menu-item.danger:hover:not(.disabled) {
  background: var(--danger-soft);
}
</style>
