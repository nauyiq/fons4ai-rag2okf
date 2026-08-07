<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { batchUploadDocuments, listDocuments, uploadDocument, type DocumentSummary } from '../../api/documents'
import DocumentStatusRail from '../../components/document/DocumentStatusRail.vue'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatBytes, formatTime } from '../../utils/formatters'

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

// 上传状态
const selectedFiles = ref<File[]>([])
const targetFolderPath = ref<string>('/')
const uploadError = ref('')
const uploadResults = ref<{ name: string; success: boolean; error?: string }[]>([])

const fileInput = ref<HTMLInputElement>()
const folderInput = ref<HTMLInputElement>()

const STORAGE_KEY_PREFIX = 'rag2okf_folders_'

/** 文件夹树：从文档 folderPath 去重聚合 ∪ localStorage 暂存。 */
const folderTree = computed(() => {
  const folders = new Set<string>()
  for (const doc of documents.value) {
    if (doc.folderPath && doc.folderPath !== '/') {
      folders.add(doc.folderPath)
    }
  }
  for (const f of storedFolders.value) {
    folders.add(f)
  }
  return Array.from(folders).sort()
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

const totalSelectedSize = computed(() =>
  selectedFiles.value.reduce((sum, f) => sum + f.size, 0)
)

const exceedsLimit = computed(() =>
  selectedFiles.value.length > 50 || totalSelectedSize.value > 200 * 1024 * 1024
)

// 文件夹计数
function folderCount(folderPath: string): number {
  if (folderPath === '/') return documents.value.length
  return documents.value.filter((d) => d.folderPath === folderPath).length
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
  newFolderName.value = ''
  showNewFolderInput.value = false
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
}

// 上传相关
function pickFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  selectedFiles.value = Array.from(input.files ?? [])
  uploadError.value = ''
  uploadResults.value = []
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

watch(currentFolderPath, loadDocuments)

onMounted(() => {
  loadStoredFolders()
  loadDocuments()
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
      <button v-if="workspaceStore.canManage" class="primary-action" type="button" @click="openUpload">＋ 上传</button>
    </header>

    <div class="document-layout">
      <!-- 文件夹侧栏 -->
      <aside class="folder-sidebar" aria-label="文件夹导航">
        <p class="sidebar-heading">文件夹</p>
        <button
          class="folder-item"
          :class="{ active: currentFolderPath === '/' }"
          type="button"
          @click="selectFolder('/')"
        >
          <span aria-hidden="true">▣</span>
          <span class="folder-name">全部文件</span>
          <span class="folder-count">{{ folderCount('/') }}</span>
        </button>
        <button
          v-for="folder in folderTree"
          :key="folder"
          class="folder-item"
          :class="{ active: currentFolderPath === folder }"
          type="button"
          @click="selectFolder(folder)"
        >
          <span aria-hidden="true">▢</span>
          <span class="folder-name">{{ folder }}</span>
          <span class="folder-count">{{ folderCount(folder) }}</span>
        </button>
        <template v-if="workspaceStore.canManage">
          <button v-if="!showNewFolderInput" class="folder-add" type="button" @click="showNewFolderInput = true">
            ＋ 新建文件夹
          </button>
          <div v-else class="folder-input-group">
            <input
              v-model="newFolderName"
              type="text"
              placeholder="如：合规材料/2024年报"
              maxlength="512"
              @keyup.enter="addFolder"
              @keyup.esc="showNewFolderInput = false"
            />
            <button class="secondary-action" type="button" @click="addFolder">确定</button>
            <button class="text-button" type="button" @click="showNewFolderInput = false">取消</button>
          </div>
        </template>
      </aside>

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
          <label class="list-search">
            <span>⌕</span>
            <input v-model="query" placeholder="按文件名筛选" />
          </label>
          <span>{{ filteredDocuments.length }} 个文档</span>
          <button class="text-button" :disabled="loading" type="button" @click="loadDocuments">↻ 刷新</button>
        </section>

        <p v-if="errorMessage" class="inline-error" role="alert">
          {{ errorMessage }}
          <button type="button" @click="loadDocuments">重试</button>
        </p>
        <div v-else-if="loading" class="state-panel">正在读取文档…</div>
        <div v-else-if="filteredDocuments.length === 0" class="empty-panel">
          <span>▣</span>
          <strong>当前文件夹暂无文档</strong>
          <p>上传后可选择立即解析，或先仅保留原文件供查看。</p>
          <button v-if="workspaceStore.canManage" class="primary-action" type="button" @click="openUpload">上传文件</button>
        </div>
        <div v-else class="document-list">
          <button
            v-for="item in filteredDocuments"
            :key="item.documentKey"
            class="document-row"
            type="button"
            @click="openDocument(item.documentKey)"
          >
            <span class="file-glyph">⌑</span>
            <span class="document-primary">
              <strong>{{ item.displayName }}</strong>
              <small>{{ item.currentFile.contentType || 'unknown' }} · {{ formatBytes(item.currentFile.size) }} · {{ formatTime(item.updated) }}</small>
            </span>
            <DocumentStatusRail :document="item" />
            <span class="row-arrow">→</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 合并上传抽屉 -->
    <div v-if="showUpload" class="drawer-backdrop" @click.self="showUpload = false">
      <form class="upload-panel" @submit.prevent="submitUpload">
        <p class="eyebrow">UPLOAD</p>
        <h2>上传文档</h2>
        <p>选择文件上传单个文档，或选择文件夹批量上传并保留目录结构。单次最多 50 文件 / 200MB。</p>

        <label class="upload-target">目标文件夹
          <select v-model="targetFolderPath">
            <option value="/">全部文件（根级）</option>
            <option v-for="f in folderTree" :key="f" :value="f">{{ f }}</option>
          </select>
        </label>

        <div class="upload-choices">
          <button class="secondary-action" type="button" @click="fileInput?.click()">选择文件</button>
          <button class="secondary-action" type="button" @click="folderInput?.click()">选择文件夹</button>
        </div>
        <input
          ref="fileInput"
          type="file"
          data-test="document-file"
          class="hidden-input"
          @change="pickFiles"
        />
        <input
          ref="folderInput"
          type="file"
          data-test="document-folder"
          class="hidden-input"
          multiple
          webkitdirectory
          @change="pickFiles"
        />

        <div v-if="selectedFiles.length > 0" class="file-list-section">
          <p class="file-list-heading">
            待上传文件（{{ selectedFiles.length }} 个，共 {{ formatBytes(totalSelectedSize) }}）
          </p>
          <ul class="file-list">
            <li v-for="(f, i) in selectedFiles" :key="i" class="file-list-item">
              <span class="file-list-name">{{ f.name }}</span>
              <span class="file-list-meta">{{ formatBytes(f.size) }}</span>
              <button class="text-button" type="button" @click="removeFile(i)">移除</button>
            </li>
          </ul>
          <p v-if="exceedsLimit" class="inline-error" role="alert">
            超过 50 文件 / 200MB 限制，请分批上传。
          </p>
        </div>

        <fieldset>
          <legend>上传后的处理</legend>
          <label><input v-model="parseMode" value="DEFAULT" type="radio" />使用知识库默认策略</label>
          <label><input v-model="parseMode" value="PARSE" type="radio" />立即解析</label>
          <label><input v-model="parseMode" value="SKIP" type="radio" />仅保留原文件</label>
        </fieldset>

        <p v-if="uploadError" class="inline-error" role="alert">{{ uploadError }}</p>

        <footer>
          <button class="secondary-action" type="button" @click="showUpload = false">取消</button>
          <button
            class="primary-action"
            data-test="submit-upload"
            :disabled="selectedFiles.length === 0 || exceedsLimit || uploading"
            type="submit"
          >{{ uploading ? '上传中…' : '确认上传' }}</button>
        </footer>
      </form>
    </div>
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
.document-list { display: grid; gap: .65rem; }
.document-row {
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--surface);
  padding: 1rem 1.1rem;
  display: grid;
  grid-template-columns: auto minmax(12rem, 1fr) minmax(20rem, .9fr) auto;
  gap: 1rem;
  align-items: center;
  text-align: left;
  color: inherit;
  transition: .18s ease;
}
.document-row:hover {
  transform: translateY(-1px);
  border-color: #8b7bff;
  box-shadow: 0 12px 30px color-mix(in srgb, #8b7bff 9%, transparent);
}
.file-glyph {
  width: 2.3rem;
  height: 2.3rem;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: #8b7bff18;
  color: #7565e8;
  font-size: 1.25rem;
}
.document-primary { display: grid; gap: .28rem; }
.document-primary strong { font-size: .95rem; }
.document-primary small { color: var(--muted-foreground); }
.row-arrow { color: #8b7bff; font-size: 1.1rem; }

.upload-panel {
  width: min(34rem, calc(100vw - 2rem));
  max-height: 85vh;
  overflow-y: auto;
  border: 1px solid var(--border-color);
  border-radius: 22px;
  background: var(--surface);
  padding: 1.75rem;
  box-shadow: 0 24px 70px #0003;
}
.upload-panel h2 { margin: .35rem 0; }
.upload-panel p { color: var(--muted-foreground); line-height: 1.6; }
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

.upload-panel fieldset {
  display: grid;
  gap: .65rem;
  border: 0;
  padding: 0;
  margin: 1rem 0;
}
.upload-panel label { display: flex; gap: .55rem; align-items: center; }
.upload-panel footer {
  display: flex;
  justify-content: flex-end;
  gap: .75rem;
  margin-top: 1.5rem;
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
  .document-row { grid-template-columns: auto 1fr; }
  .document-row .status-rail { grid-column: 2; }
  .row-arrow { display: none; }
}
</style>
