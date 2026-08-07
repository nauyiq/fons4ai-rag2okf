<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { getChunkPreview, getDocument, getParsePreview, rechunkDocument, retryTask, triggerParse, triggerPublish, updateDocumentFile, type ChunkPreview, type DocumentDetail, type ParsePreview } from '../../api/documents'
import AppDialog from '../../components/ui/AppDialog.vue'
import DocumentStatusRail from '../../components/document/DocumentStatusRail.vue'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatBytes, taskStatusLabel } from '../../utils/formatters'

const route = useRoute(); const router = useRouter(); const workspaceStore = useWorkspaceStore()
const document = ref<DocumentDetail>(); const chunk = ref<ChunkPreview>(); const parse = ref<ParsePreview>(); const loading = ref(true); const submitting = ref(false); const errorMessage = ref('')
const showReplace = ref(false); const replacement = ref<File | null>(null); const showRechunk = ref(false)
let pollHandle: number | undefined
let polling = false
const documentKey = String(route.params.documentKey)

async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try {
    const [detail, chunkPreview, parsePreview] = await Promise.all([getDocument(documentKey), getChunkPreview(documentKey), getParsePreview(documentKey)])
    document.value = detail; chunk.value = chunkPreview; parse.value = parsePreview
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取文档详情。'
  } finally {
    loading.value = false
  }
}

/** 后台刷新只更新文档状态和任务摘要，不重置整个页面。 */
async function refreshStatus(): Promise<void> {
  if (polling) return
  polling = true
  try {
    const detail = await getDocument(documentKey)
    document.value = detail
  } catch {
    // 后台刷新失败不影响现有展示
  } finally {
    polling = false
  }
}

async function run(action: () => Promise<unknown>): Promise<void> {
  submitting.value = true; errorMessage.value = ''
  try { await action(); await load() }
  catch (error) { errorMessage.value = error instanceof ApiRequestError ? error.message : '操作未能完成，请稍后重试。' }
  finally { submitting.value = false }
}

function chooseReplacement(event: Event): void { replacement.value = (event.target as HTMLInputElement).files?.[0] ?? null }
async function replaceFile(): Promise<void> { if (!replacement.value || !document.value) return; await run(() => updateDocumentFile(documentKey, replacement.value!, 'DEFAULT', document.value!.currentFileToken)); showReplace.value = false; replacement.value = null }
async function confirmRechunk(): Promise<void> { if (!chunk.value?.currentChunkRevisionKey) return; await run(() => rechunkDocument(documentKey, chunk.value!.currentChunkRevisionKey!, chunk.value!.chunkProfile ?? {})); showRechunk.value = false }
async function retryLatestTask(): Promise<void> { const taskKey = document.value?.latestTask?.taskKey; if (taskKey) await run(() => retryTask(taskKey)) }

/** 判断任务是否处于需要轮询的活跃状态。 */
function isTaskActive(): boolean {
  const status = document.value?.latestTask?.status
  return status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING'
}

function startPolling(): void {
  stopPolling()
  if (isTaskActive()) {
    pollHandle = window.setInterval(refreshStatus, 4000)
  }
}

function stopPolling(): void {
  if (pollHandle) {
    window.clearInterval(pollHandle)
    pollHandle = undefined
  }
}

/** 监听文档状态变化，自动启停轮询。 */
watch(() => document.value?.latestTask?.status, (newStatus) => {
  if (newStatus && isTaskActive()) {
    startPolling()
  } else {
    stopPolling()
  }
})

onMounted(async () => { await load(); startPolling() })
onBeforeUnmount(stopPolling)
</script>

<template>
  <aside v-if="parse && document" class="parse-preview-note" aria-label="解析预览摘要">
    <span>PARSE PREVIEW</span>
    <strong>{{ parse.hasParse ? `${parse.blockCount} 个解析块` : '尚无解析结果' }}</strong>
    <small>{{ parse.hasParse ? `解析策略：${parse.parserProfile || '默认策略'}` : '仅保留原文件时，不展示虚构的解析预览。' }}</small>
  </aside>
  <section class="document-page detail-page">
    <button class="back-link" type="button" @click="router.push({ name: 'documents', params: { knowledgeBaseKey: route.params.knowledgeBaseKey } })">← 返回文档列表</button>
    <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="load">重试</button></p><div v-else-if="loading" class="state-panel">正在读取文档…</div>
    <template v-else-if="document"><header class="document-hero"><div><p class="eyebrow">CURRENT SOURCE FILE</p><h1>{{ document.displayName }}</h1><p>{{ document.currentFile.contentType || 'unknown' }} · {{ formatBytes(document.currentFile.size) }}。页面只展示当前文件，不提供版本历史或回退。</p></div><div v-if="workspaceStore.canManage" class="hero-actions"><button class="secondary-action" type="button" :disabled="submitting" @click="showReplace = true">上传新文件</button><button class="primary-action" type="button" :disabled="submitting || document.parseStatus === 'RUNNING'" @click="run(() => triggerParse(documentKey))">解析文档</button></div></header>
      <DocumentStatusRail class="detail-rail" :document="document" />
      <section v-if="document.latestTask" class="task-card"><div><p class="eyebrow">LATEST TASK</p><strong>{{ document.latestTask.taskType }} · {{ taskStatusLabel(document.latestTask.status) }}</strong><p v-if="document.latestTask.status === 'RUNNING'">{{ document.latestTask.progress }}% · {{ document.latestTask.stage || '处理中' }}</p><p v-else-if="document.latestTask.errorMessage" class="task-error">{{ document.latestTask.errorCode || '任务失败' }}：{{ document.latestTask.errorMessage }}</p></div><button v-if="workspaceStore.canManage && document.latestTask.status === 'FAILED'" class="secondary-action" type="button" :disabled="submitting" @click="retryLatestTask">重试任务</button></section>
      <section class="processing-grid"><article><p class="eyebrow">CHUNKS</p><h2>{{ chunk?.hasChunk ? `${chunk.total} 个分块` : '尚无可用分块' }}</h2><p>{{ chunk?.hasChunk ? `父块 ${chunk.parentCount} · 子块 ${chunk.childCount}` : '未解析或尚未生成分块时，不展示伪造的结构化结果。' }}</p><button v-if="workspaceStore.canManage && chunk?.hasChunk" class="danger-link" type="button" :disabled="submitting" @click="showRechunk = true">重新分块</button></article><article><p class="eyebrow">PUBLICATION</p><h2>{{ document.publishStatus === 'PUBLISHED' ? '已进入检索' : '尚未发布' }}</h2><p v-if="document.publishStatus === 'PUBLISH_FAILED' && document.hasActivePublication">最新发布失败，但此前已发布内容继续对检索可用。</p><p v-else>发布仅使用当前成功的分块，不会因处理失败静默替换可用内容。</p><button v-if="workspaceStore.canManage" class="primary-action" type="button" :disabled="submitting || !chunk?.hasChunk" @click="run(() => triggerPublish(documentKey))">发布到检索</button></article></section>
    </template>
    <AppDialog v-model="showReplace" title="上传新文件" size="md">
      <header class="dialog-header"><p class="eyebrow">REPLACE CURRENT FILE</p><h2 id="dialog-title">上传新文件</h2><p class="dialog-description">这会仅更新此文档的当前文件。版本能力在后端保留，但不会在界面展示。</p></header>
      <form class="replace-form" @submit.prevent="replaceFile"><input type="file" @change="chooseReplacement" /><footer class="dialog-actions"><button class="secondary-action" type="button" @click="showReplace = false">取消</button><button class="primary-action" :disabled="!replacement || submitting" type="submit">确认更新</button></footer></form>
    </AppDialog>
    <AppDialog v-model="showRechunk" title="确认重新分块？" size="md" danger>
      <header class="dialog-header"><p class="eyebrow">DESTRUCTIVE PROCESSING</p><h2 id="dialog-title">确认重新分块？</h2><p class="dialog-description">将删除当前解析结果所对应的既有分块，并按当前分块策略重新处理。原文件和已发布内容不会被删除。</p></header>
      <footer class="dialog-actions"><button class="secondary-action" data-test="cancel-rechunk" type="button" @click="showRechunk = false">取消</button><button class="danger-action" data-test="confirm-rechunk" :disabled="submitting" type="button" @click="confirmRechunk">删除旧分块并继续</button></footer>
    </AppDialog>
  </section>
</template>

<style scoped>
.parse-preview-note{position:fixed;right:1rem;bottom:1rem;z-index:3;display:grid;gap:.15rem;max-width:15rem;padding:.75rem .9rem;border:1px solid var(--border-color);border-radius:12px;background:color-mix(in srgb,var(--surface) 92%,transparent);box-shadow:0 10px 28px #0002;font-size:.76rem}.parse-preview-note span{color:#7766e8;font-size:.65rem;letter-spacing:.08em}.parse-preview-note small{color:var(--muted-foreground)}
.document-hero{display:flex;justify-content:space-between;gap:1.25rem;align-items:flex-start;margin:1.25rem 0}.document-hero h1{margin:.35rem 0}.document-hero p{color:var(--muted-foreground);line-height:1.65}.hero-actions{display:flex;gap:.7rem;flex-wrap:wrap}.detail-rail{border:1px solid var(--border-color);border-radius:13px;padding:.75rem 1rem;background:var(--surface)}.task-card,.processing-grid article{border:1px solid var(--border-color);border-radius:17px;background:var(--surface);padding:1.2rem}.task-card{margin-top:1rem;display:flex;justify-content:space-between;gap:1rem}.task-card p,.processing-grid p{color:var(--muted-foreground);line-height:1.55}.task-error{color:#dd654b!important}.processing-grid{display:grid;grid-template-columns:1fr 1fr;gap:1rem;margin-top:1rem}.processing-grid h2{margin:.35rem 0}.danger-link{border:0;background:none;color:#df654d;padding:0;font:inherit}.danger-action{border:0;border-radius:10px;padding:.7rem 1rem;background:#d95845;color:white;font:inherit}.dialog-header h2{margin:0 0 4px;font-size:21px;letter-spacing:-.02em}.dialog-description{margin:0;color:var(--muted-foreground);font-size:13px;line-height:1.6}.replace-form input{margin:1rem 0;width:100%}.dialog-actions{display:flex;justify-content:flex-end;gap:.7rem;margin-top:1.4rem}@media(max-width:720px){.document-hero,.task-card{display:grid}.processing-grid{grid-template-columns:1fr}}
</style>
