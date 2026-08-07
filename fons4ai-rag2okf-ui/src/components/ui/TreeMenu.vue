<script lang="ts">
/** 树状菜单节点数据结构 */
export interface TreeNode {
  /** 节点显示名称 */
  name: string
  /** 节点完整路径（如 /合规材料/2024年报） */
  path: string
  /** 子节点列表 */
  children?: TreeNode[]
  /** 该目录下的文档数量 */
  documentCount?: number
}
</script>

<script setup lang="ts">
/**
 * 树状菜单组件，递归渲染目录层级。
 *
 * <p>支持展开/收起、选中高亮、层级缩进。
 * 用于文档工作台目录树展示。
 * 遵循 AC-011：文档列表树状层级，可展开收起。
 */
import { ref } from 'vue'

defineOptions({ name: 'TreeMenu' })

const props = withDefaults(defineProps<{
  /** 顶层节点列表 */
  nodes: TreeNode[]
  /** 当前选中节点路径 */
  selectedPath?: string
  /** 当前层级深度，递归时自动递增（内部使用） */
  depth?: number
}>(), {
  depth: 0,
})

const emit = defineEmits<{
  select: [path: string]
}>()

/** 收起的节点路径集合（默认全部展开） */
const collapsed = ref<Set<string>>(new Set())

/** 判断节点是否有子节点 */
function hasChildren(node: TreeNode): boolean {
  return !!(node.children && node.children.length > 0)
}

/** 判断节点是否展开 */
function isExpanded(path: string): boolean {
  return !collapsed.value.has(path)
}

/** 切换节点展开/收起状态 */
function toggle(path: string): void {
  if (collapsed.value.has(path)) {
    collapsed.value.delete(path)
  } else {
    collapsed.value.add(path)
  }
  // 触发响应式更新
  collapsed.value = new Set(collapsed.value)
}

/** 点击节点标签：触发 select 事件 */
function handleSelect(node: TreeNode): void {
  emit('select', node.path)
}

/** 子组件 select 事件转发 */
function handleChildSelect(path: string): void {
  emit('select', path)
}
</script>

<template>
  <div class="tree-menu">
    <div v-if="nodes.length === 0" class="tree-menu-empty">暂无目录</div>
    <template v-for="node in nodes" :key="node.path">
      <div
        class="tree-node"
        :data-depth="depth"
        :style="{ paddingLeft: 10 + depth * 16 + 'px' }"
        :class="{ active: selectedPath === node.path }"
      >
        <span
          v-if="hasChildren(node)"
          class="toggle-icon"
          @click="toggle(node.path)"
        >{{ isExpanded(node.path) ? '▾' : '▸' }}</span>
        <span
          class="tree-node-label"
          @click="handleSelect(node)"
        >{{ node.name }}</span>
        <span
          v-if="node.documentCount !== undefined && node.documentCount > 0"
          class="doc-count"
        >{{ node.documentCount }}</span>
      </div>
      <TreeMenu
        v-if="hasChildren(node) && isExpanded(node.path)"
        :nodes="node.children ?? []"
        :selected-path="selectedPath"
        :depth="depth + 1"
        @select="handleChildSelect"
      />
    </template>
  </div>
</template>

<style scoped>
.tree-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tree-menu-empty {
  padding: 16px;
  color: var(--ink-faint);
  font-size: 13px;
  text-align: center;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  /* padding-left 由内联 style 按 depth 动态设置 */
}

.tree-node:hover {
  background: var(--surface-muted);
}

.tree-node.active {
  color: var(--violet);
  background: var(--violet-soft);
  font-weight: 700;
}

.toggle-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 12px;
  color: var(--ink-faint);
  user-select: none;
}

.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-count {
  padding: 1px 7px;
  border-radius: 99px;
  color: var(--ink-faint);
  font-size: 11px;
  background: var(--surface-strong);
}
</style>
