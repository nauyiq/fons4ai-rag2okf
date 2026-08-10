<script setup lang="ts">
/**
 * 文档工作台视图。
 *
 * <p>左侧目录树（buildFolderTree 从文档 folderPath 聚合 ∪ localStorage 暂存），
 * 支持右键创建目录、展开收起、按目录过滤文档列表。
 *
 * <p>遵循 AC-010/011/014 与技术设计 §5.6。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import {
  batchDeleteDocuments,
  batchUploadDocuments,
  deleteDocument,
  listDocuments,
  uploadDocument,
  type DocumentSummary,
} from '../../api/documents'
import AppDialog from '../../components/ui/AppDialog.vue'
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue'
import DocumentStatusRail from '../../components/document/DocumentStatusRail.vue'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatBytes, formatTime } from '../../utils/formatters'
import { annotateDocumentCounts, buildFolderTree, type FolderNode } from '../../utils/folderTree'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const documents = ref<DocumentSummary[]>([])
const loading = ref(false)
const errorMessage = ref('')
const showUpload = ref(false)
const parseMode = ref<'DEFAULT' | 'PARSE' | 'SKIP'>('DEFAULT')
const uploading = ref(false)
const query = ref('')
const knowledgeBaseKey = computed(() => String(route.params.knowledgeBaseKey))

// 文件夹状态
const currentFolderPath = ref<string>('/')
const storedFolders = ref<string[]>([])
const showNewFolderInput = ref(false)
const newFolderName = ref('')

// 树状目录展开状态：记录已展开的目录路径
const expandedPaths = ref<Set<string>>(new Set())

// 右键上下文菜单状态
const showContextMenu = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

// 上传状态
const selectedFiles = ref<File[]>([])
const targetFolderPath = ref<string>('/')
const uploadError = ref('')
const uploadResults = ref<{ name: string; success: boolean; error?: string }[]>([])

const fileInput = ref<HTMLInputElement>()
const folderInput = ref<HTMLInputElement>()

// === 文档勾选与删除（T024） ===
// 勾选状态为纯视图状态，删除的业务语义（软删除+ES 清理）在后端
const selectedKeys = ref<Set<string>>(new Set())
/** 单行操作菜单：当前打开的 documentKey，空字符串表示无 */
const openMenuKey = ref('')
/** 删除确认弹窗状态：single 单个 / batch 批量 / null 关闭 */
const deleteConfirm = ref<null | { mode: 'single' | 'batch'; key?: string }>(null)
/** 删除操作独立 ActionState（不使用全局锁），归属到触发位置 */
const deleteState = ref<{ loading: boolean; error: string; success: string }>({
  loading: false,
  error: '',
  success: '',
})

const STORAGE_KEY_PREFIX = 'rag2okf_folders_'

/** 从文档 folderPath 去重聚合 ∪ localStorage 暂存，构建嵌套目录树。 */
const folderTreeData = computed(() => {
  const folders = new Set<string>()
  for (const doc of documents.value) {
    if (doc.folderPath && doc.folderPath !== '/') {
      folders.add(doc.folderPath)
    }
  }
  for (const f of storedFolders.value) {
    folders.add(f)
  }
  const tree = buildFolderTree(Array.from(folders))
  // 标注各目录直接子文档数
  const counts = new Map<string, number>()
  for (const doc of documents.value) {
    counts.set(doc.folderPath, (counts.get(doc.folderPath) ?? 0) + 1)
  }
  annotateDocumentCounts(tree, counts)
  return tree
})

/** 展平目录树为带缩进层级的列表，便于 v-for 渲染。 */
interface FlatFolderItem {
  name: string
  path: string
  depth: number
  hasChildren: boolean
  expanded: boolean
  documentCount: number
}

const flatFolderList = computed<FlatFolderItem[]>(() => {
  const result: FlatFolderItem[] = []
  function walk(nodes: FolderNode[], depth: number): void {
    for (const node of nodes) {
      result.push({
        name: node.name,
        path: node.path,
        depth,
        hasChildren: node.children.length > 0,
        expanded: expandedPaths.value.has(node.path),
        documentCount: node.documentCount,
      })
      if (node.children.length > 0 && expandedPaths.value.has(node.path)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(folderTreeData.value, 0)
  return result
})

/** 上传抽屉中可选的目标文件夹列表（展平路径）。 */
const folderOptions = computed(() => {
  const paths: string[] = []
  function collect(nodes: FolderNode[]): void {
    for (const node of nodes) {
      paths.push(node.path)
      collect(node.children)
    }
  }
  collect(folderTreeData.value)
  return paths
})

/** 面包屑路径段。 */
const breadcrumbSegments = computed(() => {
  if (currentFolderPath.value === '/' || !currentFolderPath.value) {
    return [{ label: '全部文件', path: '/' }]
  }
  const segments = currentFolderPath.value.split('/').filter(Boolean)
  const result = [{ label: '全部文件', path: '/' }]
  let acc = ''
  for (const seg of segments) {
    acc += '/' + seg
    result.push({ label: seg, path: acc })
  }
  return result
})

const filteredDocuments = computed(() =>
  documents.value.filter((item) =>
    item.displayName.toLowerCase().includes(query.value.trim().toLowerCase())
  )
)

/** 当前页全选状态（基于 filteredDocuments）。 */
const allSelected = computed({
  get: () =>
    filteredDocuments.value.length > 0 &&
    filteredDocuments.value.every((d) => selectedKeys.value.has(d.documentKey)),
  set: (value: boolean) => {
    if (value) {
      for (const d of filteredDocuments.value) selectedKeys.value.add(d.documentKey)
    } else {
      for (const d of filteredDocuments.value) selectedKeys.value.delete(d.documentKey)
    }
    selectedKeys.value = new Set(selectedKeys.value)
  },
})

/** 是否半选（部分选中）。 */
const someSelected = computed(
  () =>
    selectedKeys.value.size > 0 &&
    filteredDocuments.value.some((d) => !selectedKeys.value.has(d.documentKey))
)

const selectedCount = computed(() => selectedKeys.value.size)

/** 删除确认弹窗的描述文案。 */
const deleteConfirmDescription = computed(() => {
  if (!deleteConfirm.value) return ''
  if (deleteConfirm.value.mode === 'single') {
    const doc = documents.value.find((d) => d.documentKey === deleteConfirm.value?.key)
    return `确定要删除文档「${doc?.displayName ?? ''}」吗？此操作不可撤销，相关解析与发布内容将一并清理。`
  }
  return `确定要删除选中的 ${selectedCount.value} 个文档吗？此操作不可撤销，相关解析与发布内容将一并清理。`
})

const totalSelectedSize = computed(() =>
  selectedFiles.value.reduce((sum, f) => sum + f.size, 0)
)

const exceedsLimit = computed(() =>
  selectedFiles.value.length > 50 || totalSelectedSize.value > 200 * 1024 * 1024
)

// 根目录文档计数
const rootDocumentCount = computed(() => documents.value.filter((d) => d.folderPath === '/' || !d.folderPath).length)

/** 切换目录展开/收起。 */
function toggleFolder(path: string, event: Event): void {
  event.stopPropagation()
  if (expandedPaths.value.has(path)) {
    expandedPaths.value.delete(path)
  } else {
    expandedPaths.value.add(path)
  }
  // 触发响应式更新（Set 的 add/delete 不触发 ref 更新）
  expandedPaths.value = new Set(expandedPaths.value)
}

// localStorage 暂存文件夹
function loadStoredFolders(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_PREFIX + knowledgeBaseKey.value)
    storedFolders.value = raw ? JSON.parse(raw) : []
  } catch {
    storedFolders.value = []
  }
}

function saveStoredFolder(path: string): void {
  if (!storedFolders.value.includes(path)) {
    storedFolders.value.push(path)
    localStorage.setItem(
      STORAGE_KEY_PREFIX + knowledgeBaseKey.value,
      JSON.stringify(storedFolders.value)
    )
  }
}

function addFolder(): void {
  const name = newFolderName.value.trim()
  if (!name) return
  const path = name.startsWith('/') ? name : '/' + name
  saveStoredFolder(path)
  // 展开父目录以便用户看到新建的子目录
  if (path.includes('/')) {
    const parentPath = path.substring(0, path.lastIndexOf('/'))
    if (parentPath) expandedPaths.value.add(parentPath)
  }
  newFolderName.value = ''
  showNewFolderInput.value = false
  closeContextMenu()
}

async function loadDocuments(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const folderParam = currentFolderPath.value !== '/' ? currentFolderPath.value : undefined
    documents.value = (await listDocuments(knowledgeBaseKey.value, 0, 100, folderParam)).records
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '文档列表加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function selectFolder(folderPath: string): void {
  currentFolderPath.value = folderPath
  closeContextMenu()
}

// 右键上下文菜单
function onContextMenu(event: MouseEvent): void {
  event.preventDefault()
  showContextMenu.value = true
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
}

function closeContextMenu(): void {
  showContextMenu.value = false
}

function contextNewFolder(): void {
  showNewFolderInput.value = true
  newFolderName.value = ''
  closeContextMenu()
}

/** 点击页面其他位置关闭上下文菜单和单行操作菜单。 */
function handleDocumentClick(event: MouseEvent): void {
  const target = event.target as HTMLElement
  if (showContextMenu.value && !target.closest('.context-menu')) {
    closeContextMenu()
  }
  if (openMenuKey.value && !target.closest('.row-menu') && !target.closest('.row-menu-trigger')) {
    closeRowMenu()
  }
}

// 上传相关
function pickFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  selectedFiles.value = Array.from(input.files ?? [])
  uploadError.value = ''
  uploadResults.value = []
}

function onUploadMenuClick({ key }: { key: string }): void {
  if (key === 'folder') folderInput.value?.click()
  else fileInput.value?.click()
}

function removeFile(index: number): void {
  selectedFiles.value.splice(index, 1)
}

function deriveRelativePath(file: File): string | undefined {
  const wf = file as File & { webkitRelativePath?: string }
  if (wf.webkitRelativePath) return wf.webkitRelativePath
  return undefined
}

async function submitUpload(): Promise<void> {
  if (selectedFiles.value.length === 0 || exceedsLimit.value) return
  uploading.value = true
  uploadError.value = ''
  uploadResults.value = []

  try {
    const folderPath = targetFolderPath.value !== '/' ? targetFolderPath.value : undefined

    if (selectedFiles.value.length === 1 && !deriveRelativePath(selectedFiles.value[0])) {
      await uploadDocument(knowledgeBaseKey.value, selectedFiles.value[0], parseMode.value, folderPath)
      uploadResults.value = [{ name: selectedFiles.value[0].name, success: true }]
    } else {
      const relativePaths = selectedFiles.value
        .map((f) => deriveRelativePath(f))
        .filter((p): p is string => p != null)
      await batchUploadDocuments(knowledgeBaseKey.value, selectedFiles.value, parseMode.value, relativePaths.length > 0 ? relativePaths : undefined)
      uploadResults.value = selectedFiles.value.map((f) => ({ name: f.name, success: true }))
    }

    showUpload.value = false
    selectedFiles.value = []
    if (fileInput.value) fileInput.value.value = ''
    if (folderInput.value) folderInput.value.value = ''
    await loadDocuments()
  } catch (error) {
    uploadError.value = error instanceof ApiRequestError ? error.message : '上传未能完成，请稍后重试。'
  } finally {
    uploading.value = false
  }
}

function openUpload(): void {
  showUpload.value = true
  selectedFiles.value = []
  uploadError.value = ''
  uploadResults.value = []
  targetFolderPath.value = currentFolderPath.value
}

function openDocument(documentKey: string): void {
  router.push({ name: 'document-detail', params: { knowledgeBaseKey: knowledgeBaseKey.value, documentKey } })
}

// === 勾选与删除操作（T024） ===
/** 切换单个文档勾选状态，阻止冒泡以免触发 openDocument。 */
function toggleSelect(documentKey: string, event: Event): void {
  event.stopPropagation()
  if (selectedKeys.value.has(documentKey)) {
    selectedKeys.value.delete(documentKey)
  } else {
    selectedKeys.value.add(documentKey)
  }
  selectedKeys.value = new Set(selectedKeys.value)
}

/** 切换单行操作菜单显隐。 */
function toggleRowMenu(documentKey: string, event: Event): void {
  event.stopPropagation()
  openMenuKey.value = openMenuKey.value === documentKey ? '' : documentKey
}

/** 关闭单行操作菜单。 */
function closeRowMenu(): void {
  openMenuKey.value = ''
}

/** 打开单个删除确认弹窗。 */
function confirmDeleteSingle(documentKey: string, event: Event): void {
  event.stopPropagation()
  closeRowMenu()
  deleteState.value.error = ''
  deleteState.value.success = ''
  deleteConfirm.value = { mode: 'single', key: documentKey }
}

/** 打开批量删除确认弹窗。 */
function confirmDeleteBatch(): void {
  if (selectedCount.value === 0) return
  deleteState.value.error = ''
  deleteState.value.success = ''
  deleteConfirm.value = { mode: 'batch' }
}

/** 取消删除确认。 */
function cancelDelete(): void {
  deleteConfirm.value = null
}

/**
 * 执行删除操作（单个或批量）。
 * 独立 ActionState，不冻结整页；批量删除部分成功时保留失败项可重试。
 */
async function executeDelete(): Promise<void> {
  if (!deleteConfirm.value) return
  deleteState.value.loading = true
  deleteState.value.error = ''
  deleteState.value.success = ''

  try {
    if (deleteConfirm.value.mode === 'single') {
      const key = deleteConfirm.value.key!
      await deleteDocument(key)
      selectedKeys.value.delete(key)
      selectedKeys.value = new Set(selectedKeys.value)
      deleteState.value.success = '文档已删除'
    } else {
      const keys = Array.from(selectedKeys.value)
      const result = await batchDeleteDocuments(keys)
      // 从列表移除成功删除的文档，保留失败项可重试
      for (const key of result.deleted) selectedKeys.value.delete(key)
      selectedKeys.value = new Set(selectedKeys.value)
      if (result.failed.length > 0) {
        deleteState.value.error = `${result.deleted.length} 个文档已删除，${result.failed.length} 个失败：${result.failed.map((f) => f.error).join('；')}`
      } else {
        deleteState.value.success = `已删除 ${result.deleted.length} 个文档`
      }
    }
    deleteConfirm.value = null
    await loadDocuments()
  } catch (error) {
    deleteState.value.error = error instanceof ApiRequestError ? error.message : '删除未能完成，请稍后重试。'
  } finally {
    deleteState.value.loading = false
  }
}

watch(currentFolderPath, loadDocuments)

onMounted(() => {
  loadStoredFolders()
  loadDocuments()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <section class="document-page">
    <header class="page-heading compact">
      <div>
        <button class="back-link" type="button" @click="router.push({ name: 'knowledge-bases' })">← 返回知识库</button>
        <p class="eyebrow">SOURCE DOCUMENTS</p>
        <h1>文档工作台</h1>
        <p>文件名相同也会创建新文档；这里始终只呈现每个文档的当前文件。</p>
      </div>
      <a-button v-if="workspaceStore.canManage" type="primary" data-test="upload-btn" @click="openUpload">＋ 上传</a-button>
    </header>

    <div class="document-layout">
      <!-- 文件夹侧栏（树状目录） -->
      <aside class="folder-sidebar" aria-label="文件夹导航" data-test="folder-sidebar" @contextmenu="onContextMenu">
        <p class="sidebar-heading">文件夹</p>
        <button
          class="folder-item"
          :class="{ active: currentFolderPath === '/' }"
          type="button"
          data-test="folder-item"
          @click="selectFolder('/')"
        >
          <span aria-hidden="true">▣</span>
          <span class="folder-name">全部文件</span>
          <span class="folder-count">{{ rootDocumentCount }}</span>
        </button>
        <button
          v-for="folder in flatFolderList"
          :key="folder.path"
          class="folder-item"
          :class="{ active: currentFolderPath === folder.path }"
          :style="{ paddingLeft: `${10 + folder.depth * 16}px` }"
          type="button"
          data-test="folder-item"
          @click="selectFolder(folder.path)"
        >
          <span
            v-if="folder.hasChildren"
            class="folder-toggle"
            :data-test="`folder-toggle-${folder.path}`"
            aria-hidden="true"
            @click="toggleFolder(folder.path, $event)"
          >{{ folder.expanded ? '▼' : '▶' }}</span>
          <span v-else class="folder-toggle-placeholder" aria-hidden="true" />
          <span class="folder-name">{{ folder.name }}</span>
          <span class="folder-count">{{ folder.documentCount }}</span>
        </button>
        <template v-if="workspaceStore.canManage">
          <button v-if="!showNewFolderInput" class="folder-add" type="button" @click="showNewFolderInput = true">
            ＋ 新建文件夹
          </button>
          <div v-else class="folder-input-group">
            <a-input
              v-model:value="newFolderName"
              placeholder="如：合规材料/2024年报"
              :maxlength="512"
              size="small"
              data-test="new-folder-input"
              @keyup.enter="addFolder"
              @keyup.esc="showNewFolderInput = false"
            />
            <div class="folder-input-actions">
              <a-button size="small" type="primary" data-test="new-folder-confirm" @click="addFolder">确定</a-button>
              <a-button size="small" type="link" @click="showNewFolderInput = false">取消</a-button>
            </div>
          </div>
        </template>
      </aside>

      <!-- 右键上下文菜单 -->
      <div
        v-if="showContextMenu"
        class="context-menu"
        :style="{ left: `${contextMenuX}px`, top: `${contextMenuY}px` }"
      >
        <button type="button" data-test="context-new-folder" @click="contextNewFolder">新建目录</button>
      </div>

      <!-- 文档列表区 -->
      <div class="document-main">
        <!-- 面包屑 -->
        <nav class="folder-breadcrumb" aria-label="文件夹路径">
          <template v-for="(seg, i) in breadcrumbSegments" :key="seg.path">
            <button
              v-if="i < breadcrumbSegments.length - 1"
              class="breadcrumb-link"
              type="button"
              @click="selectFolder(seg.path)"
            >{{ seg.label }}</button>
            <span v-else class="breadcrumb-current">{{ seg.label }}</span>
            <span v-if="i < breadcrumbSegments.length - 1" class="breadcrumb-sep">/</span>
          </template>
        </nav>

        <section class="document-toolbar">
          <label v-if="workspaceStore.canManage && filteredDocuments.length > 0" class="select-all-label">
            <input
              type="checkbox"
              :checked="allSelected"
              :indeterminate.prop="someSelected"
              data-test="select-all"
              @change="allSelected = !allSelected"
            />
            <span>全选</span>
          </label>
          <label class="list-search">
            <span>⌕</span>
            <input v-model="query" placeholder="按文件名筛选" />
          </label>
          <span>{{ filteredDocuments.length }} 个文档</span>
          <template v-if="workspaceStore.canManage && selectedCount > 0">
            <span class="selected-count">已选 {{ selectedCount }} 个</span>
            <a-button danger size="small" data-test="batch-delete-btn" @click="confirmDeleteBatch">批量删除</a-button>
          </template>
          <a-button type="link" class="text-button" :disabled="loading" @click="loadDocuments">↻ 刷新</a-button>
        </section>

        <!-- 删除操作反馈（归属到操作区，不冻结整页） -->
        <p v-if="deleteState.success" class="inline-success" role="status">{{ deleteState.success }}</p>
        <p v-if="deleteState.error" class="inline-error" role="alert">{{ deleteState.error }}</p>

        <p v-if="errorMessage" class="inline-error" role="alert">
          {{ errorMessage }}
          <a-button type="link" size="small" @click="loadDocuments">重试</a-button>
        </p>
        <div v-else-if="loading" class="state-panel">正在读取文档…</div>
        <div v-else-if="filteredDocuments.length === 0" class="empty-panel">
          <span>▣</span>
          <strong>当前文件夹暂无文档</strong>
          <p>上传后可选择立即解析，或先仅保留原文件供查看。</p>
          <a-button v-if="workspaceStore.canManage" type="primary" @click="openUpload">上传文件</a-button>
        </div>
        <div v-else class="document-list">
          <div
            v-for="item in filteredDocuments"
            :key="item.documentKey"
            class="document-row"
            :class="{ selected: selectedKeys.has(item.documentKey) }"
            tabindex="0"
            role="button"
            @click="openDocument(item.documentKey)"
            @keyup.enter="openDocument(item.documentKey)"
          >
            <label
              v-if="workspaceStore.canManage"
              class="row-checkbox"
              @click.stop
            >
              <input
                type="checkbox"
                :checked="selectedKeys.has(item.documentKey)"
                :data-test="`select-${item.documentKey}`"
                @change="toggleSelect(item.documentKey, $event)"
              />
            </label>
            <span class="file-glyph">⌑</span>
            <span class="document-primary">
              <strong>{{ item.displayName }}</strong>
              <small>{{ item.currentFile.contentType || 'unknown' }} · {{ formatBytes(item.currentFile.size) }} · {{ formatTime(item.updated) }}</small>
            </span>
            <DocumentStatusRail :document="item" />
            <span class="row-arrow">→</span>
            <button
              v-if="workspaceStore.canManage"
              class="row-menu-trigger"
              type="button"
              aria-label="文档操作"
              data-test="row-menu-trigger"
              @click="toggleRowMenu(item.documentKey, $event)"
            >⋯</button>
            <div
              v-if="workspaceStore.canManage && openMenuKey === item.documentKey"
              class="row-menu"
              :data-test="`row-menu-${item.documentKey}`"
              @click.stop
            >
              <button type="button" data-test="row-delete" @click="confirmDeleteSingle(item.documentKey, $event)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 合并上传弹窗（居中模态，替代原右侧抽屉） -->
    <AppDialog v-model="showUpload" title="上传文档" size="md">
      <p class="dialog-description">选择文件上传单个文档，或选择文件夹批量上传并保留目录结构。单次最多 50 文件 / 200MB。</p>
      <a-form class="upload-form" layout="vertical" @submit.prevent="submitUpload">
        <a-form-item label="目标文件夹">
          <a-select v-model:value="targetFolderPath" data-test="upload-target-folder">
            <a-select-option value="/">全部文件（根级）</a-select-option>
            <a-select-option v-for="f in folderOptions" :key="f" :value="f">{{ f }}</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="选择文件">
          <a-dropdown-button @click="fileInput?.click()">
            添加文件
            <template #overlay>
              <a-menu @click="onUploadMenuClick">
                <a-menu-item key="folder" data-test="document-folder">从文件夹添加</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown-button>
          <input
            ref="fileInput"
            type="file"
            data-test="document-file"
            style="display: none"
            @change="pickFiles"
          />
          <input
            ref="folderInput"
            type="file"
            data-test="document-folder"
            style="display: none"
            multiple
            webkitdirectory
            @change="pickFiles"
          />
        </a-form-item>

        <div v-if="selectedFiles.length > 0" class="file-list-section">
          <p class="file-list-heading">
            待上传文件（{{ selectedFiles.length }} 个，共 {{ formatBytes(totalSelectedSize) }}）
          </p>
          <ul class="file-list">
            <li v-for="(f, i) in selectedFiles" :key="i" class="file-list-item">
              <span class="file-list-name">{{ f.name }}</span>
              <span class="file-list-meta">{{ formatBytes(f.size) }}</span>
              <a-button type="link" size="small" @click="removeFile(i)">移除</a-button>
            </li>
          </ul>
          <a-alert v-if="exceedsLimit" type="error" message="超过 50 文件 / 200MB 限制，请分批上传。" show-icon />
        </div>

        <a-form-item label="上传后的处理">
          <a-radio-group v-model:value="parseMode" data-test="parse-mode">
            <a-radio value="DEFAULT">使用知识库默认策略</a-radio>
            <a-radio value="PARSE">立即解析</a-radio>
            <a-radio value="SKIP">仅保留原文件</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-alert v-if="uploadError" type="error" :message="uploadError" show-icon />

        <footer class="dialog-actions">
          <a-button @click="showUpload = false">取消</a-button>
          <a-button
            type="primary"
            html-type="submit"
            data-test="submit-upload"
            :disabled="selectedFiles.length === 0 || exceedsLimit"
            :loading="uploading"
          >确认上传</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <!-- 删除二次确认弹窗（danger + persistent，遵循 AC-024） -->
    <ConfirmDialog
      :model-value="deleteConfirm !== null"
      :title="deleteConfirm?.mode === 'single' ? '删除文档' : '批量删除文档'"
      :description="deleteConfirmDescription"
      confirm-text="删除"
      :danger="true"
      :persistent="true"
      @update:model-value="(v: boolean) => { if (!v) cancelDelete() }"
      @confirm="executeDelete"
    />
  </section>
</template>

<style scoped>
.document-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 1rem;
  margin-top: 1.25rem;
}

.folder-sidebar {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 10px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--surface);
  height: fit-content;
}
.sidebar-heading {
  margin: 0 0 8px;
  padding: 0 6px;
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .1em;
  text-transform: uppercase;
}
.folder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--ink);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background .15s ease;
}
.folder-item:hover { background: var(--surface-muted); }
.folder-item.active { background: var(--violet-soft); color: var(--violet); font-weight: 700; }
.folder-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.folder-count { color: var(--muted-foreground); font-size: 11px; font-family: "Roboto Mono", monospace; }
.folder-toggle {
  width: 14px;
  font-size: 10px;
  color: var(--muted-foreground);
  cursor: pointer;
  user-select: none;
}
.folder-toggle-placeholder { width: 14px; }
.folder-add {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px dashed var(--border-color);
  border-radius: 9px;
  background: transparent;
  color: var(--violet);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.folder-input-group { display: grid; gap: 6px; margin-top: 8px; padding: 0 4px; }
.folder-input-group input {
  padding: 7px 9px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--surface-muted);
  color: var(--ink);
  font-size: 12px;
}
.folder-input-actions { display: flex; gap: 6px; align-items: center; }

/* 右键上下文菜单 */
.context-menu {
  position: fixed;
  z-index: 100;
  min-width: 120px;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--shadow, 0 8px 30px rgba(0,0,0,0.12));
}
.context-menu button {
  display: block;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  color: var(--ink);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  border-radius: 4px;
}
.context-menu button:hover { background: var(--surface-muted); }

.folder-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 1rem;
  font-size: 13px;
}
.breadcrumb-link {
  border: 0;
  background: transparent;
  color: var(--violet);
  font-weight: 600;
  cursor: pointer;
}
.breadcrumb-link:hover { text-decoration: underline; }
.breadcrumb-current { color: var(--ink); font-weight: 700; }
.breadcrumb-sep { color: var(--muted-foreground); }

.document-main { min-width: 0; }
.document-toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
  color: var(--muted-foreground);
  font-size: .86rem;
}
.document-toolbar .text-button { margin-left: auto; }
.select-all-label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.selected-count { color: var(--ink); font-weight: 600; }
.danger-action {
  padding: 5px 12px;
  border: 1px solid var(--danger, #d54848);
  border-radius: 7px;
  background: transparent;
  color: var(--danger, #d54848);
  font-size: 12px;
  cursor: pointer;
  transition: background .15s ease;
}
.danger-action:hover { background: color-mix(in srgb, var(--danger, #d54848) 8%, transparent); }
.inline-success {
  margin: 0 0 .75rem;
  padding: 8px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, #2c8a4e 10%, transparent);
  color: #2c8a4e;
  font-size: 13px;
}
.document-list { display: grid; gap: .65rem; }
.document-row {
  position: relative;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--surface);
  padding: 1rem 1.1rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  text-align: left;
  color: inherit;
  transition: .18s ease;
  cursor: pointer;
}
.document-row:hover {
  transform: translateY(-1px);
  border-color: var(--violet);
  box-shadow: 0 12px 30px rgb(0 0 0 / 8%);
}
.document-row.selected { border-color: var(--violet); background: var(--violet-soft); }
.document-row:focus-visible { outline: 2px solid var(--violet); outline-offset: 2px; }
.row-checkbox { display: flex; align-items: center; cursor: pointer; }
.file-glyph {
  flex-shrink: 0;
  width: 2.3rem;
  height: 2.3rem;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: #8b7bff18;
  color: #7565e8;
  font-size: 1.25rem;
}
.document-primary { flex: 1; min-width: 0; display: grid; gap: .28rem; }
.document-primary strong { font-size: .95rem; }
.document-primary small { color: var(--muted-foreground); }
.row-arrow { color: #8b7bff; font-size: 1.1rem; flex-shrink: 0; }
.row-menu-trigger {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted-foreground);
  font-size: 16px;
  cursor: pointer;
  transition: background .15s ease;
}
.row-menu-trigger:hover { background: var(--surface-muted); }
.row-menu {
  position: absolute;
  right: 8px;
  top: calc(100% - 4px);
  z-index: 50;
  min-width: 100px;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--shadow, 0 8px 30px rgba(0,0,0,0.12));
}
.row-menu button {
  display: block;
  width: 100%;
  padding: 7px 10px;
  background: none;
  border: 0;
  border-radius: 4px;
  color: var(--danger, #d54848);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.row-menu button:hover { background: color-mix(in srgb, var(--danger, #d54848) 8%, transparent); }

.upload-form {
  display: grid;
  gap: 0;
}
.upload-form h2 { margin: .35rem 0; }
.upload-form p { color: var(--muted-foreground); line-height: 1.6; }
.upload-target { display: grid; gap: 6px; margin: 1rem 0; color: var(--muted-foreground); font-size: 13px; }
.upload-target select {
  padding: 9px 10px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--surface-muted);
  color: var(--ink);
}
.upload-choices { display: flex; gap: .75rem; margin: 1rem 0; }
.hidden-input { display: none; }

.file-list-section { margin: 1rem 0; }
.file-list-heading { margin: 0 0 .5rem; font-size: .85rem; color: var(--muted-foreground); }
.file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}
.file-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 7px;
  background: var(--surface-muted);
  font-size: 12px;
}
.file-list-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-list-meta { color: var(--muted-foreground); font-family: "Roboto Mono", monospace; }

.upload-form fieldset {
  display: grid;
  gap: .65rem;
  border: 0;
  padding: 0;
  margin: 1rem 0;
}
.upload-form fieldset label { display: flex; gap: .55rem; align-items: center; }
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: .75rem;
  margin-top: 1.5rem;
}
.dialog-header h2 {
  margin: 0 0 4px;
  font-size: 21px;
  letter-spacing: -.02em;
}
.dialog-description {
  margin: 0;
  color: var(--muted-foreground);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .document-layout { grid-template-columns: 1fr; }
  .folder-sidebar {
    flex-direction: row;
    overflow-x: auto;
    gap: 6px;
    padding: 10px;
  }
  .sidebar-heading { display: none; }
  .folder-item { flex-shrink: 0; }
  .folder-add { flex-shrink: 0; margin-top: 0; }
  .folder-input-group { display: none; }
  .document-row { flex-wrap: wrap; gap: .5rem; }
  .document-row .status-rail { flex-basis: 100%; }
  .row-arrow { display: none; }
  .row-menu-trigger { width: 24px; height: 24px; }
}
</style>
