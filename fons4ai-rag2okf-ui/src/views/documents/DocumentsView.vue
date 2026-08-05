<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { listDocuments, uploadDocument, type DocumentSummary } from '../../api/documents'
import DocumentStatusRail from '../../components/document/DocumentStatusRail.vue'
import { useWorkspaceStore } from '../../stores/workspace'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const documents = ref<DocumentSummary[]>([])
const loading = ref(false)
const errorMessage = ref('')
const showUpload = ref(false)
const selectedFile = ref<File | null>(null)
const parseMode = ref<'DEFAULT' | 'PARSE' | 'SKIP'>('DEFAULT')
const uploading = ref(false)
const query = ref('')
const fileInput = ref<HTMLInputElement>()
const knowledgeBaseKey = computed(() => String(route.params.knowledgeBaseKey))
const filteredDocuments = computed(() => documents.value.filter((item) => item.displayName.toLowerCase().includes(query.value.trim().toLowerCase())))

function formatBytes(value: number): string { return value < 1024 * 1024 ? `${Math.ceil(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function formatTime(value: string): string { const date = new Date(value); return Number.isNaN(date.valueOf()) ? '刚刚更新' : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }

async function loadDocuments(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try { documents.value = (await listDocuments(knowledgeBaseKey.value)).records }
  catch (error) { errorMessage.value = error instanceof ApiRequestError ? error.message : '文档列表加载失败，请稍后重试。' }
  finally { loading.value = false }
}
function pickFile(event: Event): void { selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null }
async function submitUpload(): Promise<void> {
  if (!selectedFile.value) return
  uploading.value = true; errorMessage.value = ''
  try { await uploadDocument(knowledgeBaseKey.value, selectedFile.value, parseMode.value); showUpload.value = false; selectedFile.value = null; await loadDocuments() }
  catch (error) { errorMessage.value = error instanceof ApiRequestError ? error.message : '上传未能完成，请稍后重试。' }
  finally { uploading.value = false }
}
function openDocument(documentKey: string): void { router.push({ name: 'document-detail', params: { knowledgeBaseKey: knowledgeBaseKey.value, documentKey } }) }
onMounted(loadDocuments)
</script>

<template>
  <section class="document-page">
    <header class="page-heading compact"><div><button class="back-link" type="button" @click="router.push({ name: 'knowledge-bases' })">← 返回知识库</button><p class="eyebrow">SOURCE DOCUMENTS</p><h1>文档工作台</h1><p>文件名相同也会创建新文档；这里始终只呈现每个文档的当前文件。</p></div><button v-if="workspaceStore.canManage" class="primary-action" type="button" @click="showUpload = true">＋ 上传文件</button></header>
    <section class="document-toolbar"><label class="list-search"><span>⌕</span><input v-model="query" placeholder="按文件名筛选" /></label><span>{{ documents.length }} 个文档</span><button class="text-button" :disabled="loading" type="button" @click="loadDocuments">↻ 刷新</button></section>
    <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="loadDocuments">重试</button></p>
    <div v-else-if="loading" class="state-panel">正在读取文档…</div>
    <div v-else-if="filteredDocuments.length === 0" class="empty-panel"><span>▣</span><strong>还没有可显示的文档</strong><p>上传后可选择立即解析，或先仅保留原文件供查看。</p><button v-if="workspaceStore.canManage" class="primary-action" type="button" @click="showUpload = true">上传第一个文件</button></div>
    <div v-else class="document-list">
      <button v-for="item in filteredDocuments" :key="item.documentKey" class="document-row" type="button" @click="openDocument(item.documentKey)">
        <span class="file-glyph">⌑</span><span class="document-primary"><strong>{{ item.displayName }}</strong><small>{{ item.currentFile.contentType || 'unknown' }} · {{ formatBytes(item.currentFile.size) }} · {{ formatTime(item.updated) }}</small></span><DocumentStatusRail :document="item" /><span class="row-arrow">→</span>
      </button>
    </div>
    <div v-if="showUpload" class="drawer-backdrop" @click.self="showUpload = false"><form class="upload-panel" @submit.prevent="submitUpload"><p class="eyebrow">NEW SOURCE FILE</p><h2>上传新文档</h2><p>普通上传不会与同名文件合并。是否解析由本次选择和知识库默认策略决定。</p><input ref="fileInput" type="file" data-test="document-file" @change="pickFile" /><p v-if="selectedFile" class="selected-file">{{ selectedFile.name }} · {{ formatBytes(selectedFile.size) }}</p><fieldset><legend>上传后的处理</legend><label><input v-model="parseMode" value="DEFAULT" type="radio" />使用知识库默认策略</label><label><input v-model="parseMode" value="PARSE" type="radio" />立即解析</label><label><input v-model="parseMode" value="SKIP" type="radio" />仅保留原文件</label></fieldset><footer><button class="secondary-action" type="button" @click="showUpload = false">取消</button><button class="primary-action" data-test="submit-upload" :disabled="!selectedFile || uploading" type="submit">{{ uploading ? '上传中…' : '确认上传' }}</button></footer></form></div>
  </section>
</template>

<style scoped>
.document-toolbar{display:flex;align-items:center;gap:1rem;margin:1.25rem 0;color:var(--muted-foreground);font-size:.86rem}.document-toolbar .text-button{margin-left:auto}.document-list{display:grid;gap:.65rem}.document-row{border:1px solid var(--border-color);border-radius:16px;background:var(--surface);padding:1rem 1.1rem;display:grid;grid-template-columns:auto minmax(12rem,1fr) minmax(20rem,.9fr) auto;gap:1rem;align-items:center;text-align:left;color:inherit;transition:.18s ease}.document-row:hover{transform:translateY(-1px);border-color:#8b7bff;box-shadow:0 12px 30px color-mix(in srgb,#8b7bff 9%,transparent)}.file-glyph{width:2.3rem;height:2.3rem;border-radius:11px;display:grid;place-items:center;background:#8b7bff18;color:#7565e8;font-size:1.25rem}.document-primary{display:grid;gap:.28rem}.document-primary strong{font-size:.95rem}.document-primary small{color:var(--muted-foreground)}.row-arrow{color:#8b7bff;font-size:1.1rem}.upload-panel{width:min(30rem,calc(100vw - 2rem));border:1px solid var(--border-color);border-radius:22px;background:var(--surface);padding:1.5rem;box-shadow:0 24px 70px #0003}.upload-panel h2{margin:.35rem 0}.upload-panel p{color:var(--muted-foreground);line-height:1.6}.upload-panel input[type=file]{width:100%;margin:1rem 0}.upload-panel fieldset{display:grid;gap:.65rem;border:0;padding:0;margin:1rem 0}.upload-panel label{display:flex;gap:.55rem;align-items:center}.selected-file{font-size:.85rem;color:#7766e8!important}.upload-panel footer{display:flex;justify-content:flex-end;gap:.75rem;margin-top:1.5rem}@media(max-width:760px){.document-row{grid-template-columns:auto 1fr}.document-row .status-rail{grid-column:2}.row-arrow{display:none}}
</style>
