<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import {
  getChunkPreview,
  getDocument,
  getParsePreview,
  getSourceContent,
  rechunkDocument,
  retryTask,
  triggerParse,
  triggerPublish,
  updateDocumentFile,
  type ChunkPreview,
  type DocumentDetail,
  type ParsePreview,
  type SourceContent,
  type TaskSummary,
} from '../../api/documents'
import AppDialog from '../../components/ui/AppDialog.vue'
import ChunkTreeList from '../../components/document/ChunkTreeList.vue'
import DocumentStatusRail from '../../components/document/DocumentStatusRail.vue'
import SourceFilePreview from '../../components/document/SourceFilePreview.vue'
import { useTaskPolling } from '../../composables/useTaskPolling'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatBytes } from '../../utils/formatters'

/** 每动作独立的操作反馈状态。 */
interface ActionState {
  loading: boolean
  error: string
  success: string
}

function createActionState(): ActionState {
  return { loading: false, error: '', success: '' }
}

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()

const document = ref<DocumentDetail>()
const chunk = ref<ChunkPreview>()
const parse = ref<ParsePreview>()
const source = ref<SourceContent>()
const loading = ref(true)
const errorMessage = ref('')
/** 分块列表加载状态（独立于整页 loading，翻页时使用） */
const chunkLoading = ref(false)
/** 当前分块页码（从 0 开始） */
const chunkPage = ref(0)

// 独立 ActionState：解析、发布、替换文件、重新分块、重试任务
const parseAction = ref<ActionState>(createActionState())
const publishAction = ref<ActionState>(createActionState())
const replaceAction = ref<ActionState>(createActionState())
const rechunkAction = ref<ActionState>(createActionState())
const retryAction = ref<ActionState>(createActionState())

const showReplace = ref(false)
const replacement = ref<File | null>(null)
const showRechunk = ref(false)

const documentKey = String(route.params.documentKey)

/** 解析是否处于活跃状态（已入队或执行中），用于禁用解析按钮和驱动轮询。 */
const isParseActive = computed(() => {
  const status = document.value?.parseStatus
  return status === 'QUEUED' || status === 'RUNNING'
})

/** 解析是否失败，用于显示失败原因并允许重试。 */
const isParseFailed = computed(() => document.value?.parseStatus === 'FAILED')

/** 任务是否处于重试等待（解析中失败但还会重试），用于进度条区分展示。 */
const isRetryWait = computed(() => document.value?.latestTask?.status === 'RETRY_WAIT')

/** 任务是否处于活跃状态，用于驱动轮询。 */
function isTaskActive(): boolean {
  return isParseActive.value
}

/** 执行单个操作，更新对应的 ActionState。 */
async function runAction(
  action: ActionState,
  fn: () => Promise<unknown>,
  successMsg?: string,
): Promise<boolean> {
  action.loading = true
  action.error = ''
  action.success = ''
  try {
    await fn()
    action.success = successMsg ?? ''
    return true
  } catch (error) {
    action.error = error instanceof ApiRequestError ? error.message : '操作未能完成，请稍后重试。'
    return false
  } finally {
    action.loading = false
  }
}

/** 加载全部数据：文档详情、分块预览、解析预览、源文件内容。 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [detail, chunkPreview, parsePreview, sourceContent] = await Promise.all([
      getDocument(documentKey),
      getChunkPreview(documentKey, 0),
      getParsePreview(documentKey),
      getSourceContent(documentKey).catch(() => null),
    ])
    document.value = detail
    chunk.value = chunkPreview
    chunkPage.value = 0
    parse.value = parsePreview
    if (sourceContent) source.value = sourceContent
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取文档详情。'
  } finally {
    loading.value = false
  }
}

/**
 * 轮询时局部刷新：只调文档详情接口获取进度和状态，避免每次轮询重复拉取分块/解析预览。
 *
 * <p>当 parseStatus 从活跃态（QUEUED/RUNNING）流转到 SUCCEEDED 时，补拉一次
 * getChunkPreview 和 getParsePreview 以填充结果预览，之后轮询因 isParseActive=false 自然停止。
 */
async function refreshStatus(): Promise<TaskSummary | undefined> {
  try {
    const detail = await getDocument(documentKey)
    const prevStatus = document.value?.parseStatus
    document.value = detail
    // 解析刚完成：补拉分块和解析预览结果（轮询期间未拉取）
    if (detail.parseStatus === 'SUCCEEDED' && prevStatus !== 'SUCCEEDED') {
      const [chunkPreview, parsePreview] = await Promise.all([
        getChunkPreview(documentKey, chunkPage.value),
        getParsePreview(documentKey),
      ])
      chunk.value = chunkPreview
      parse.value = parsePreview
    }
    return detail.latestTask
  } catch {
    return undefined
  }
}

/**
 * 翻页加载分块列表。
 *
 * <p>独立调用 getChunkPreview(page)，不重置整页状态，仅刷新 chunk 数据和页码。
 * 翻页后滚动到分块区域顶部。
 */
async function loadChunkPage(page: number): Promise<void> {
  chunkLoading.value = true
  try {
    const chunkPreview = await getChunkPreview(documentKey, page)
    chunk.value = chunkPreview
    chunkPage.value = page
  } catch (error) {
    // 翻页失败时记录到 rechunkAction.error 复用展示位
    rechunkAction.value.error = error instanceof ApiRequestError ? error.message : '加载分块失败，请稍后重试。'
  } finally {
    chunkLoading.value = false
  }
}

/** useTaskPolling 的 onUpdate 回调：更新文档数据。 */
function onTaskUpdate(task: TaskSummary): void {
  if (document.value) {
    document.value = { ...document.value, latestTask: task }
  }
}

const { start: startPolling, stop: stopPolling } = useTaskPolling({
  fetcher: refreshStatus,
  isActive: isTaskActive,
  onUpdate: onTaskUpdate,
})

// ---- 操作 ----

function chooseReplacement(event: Event): void {
  replacement.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function replaceFile(): Promise<void> {
  if (!replacement.value || !document.value) return
  const ok = await runAction(
    replaceAction.value,
    () => updateDocumentFile(documentKey, replacement.value!, 'DEFAULT', document.value!.currentFileToken),
    '文件已更新',
  )
  if (ok) {
    showReplace.value = false
    replacement.value = null
    await load()
  }
}

async function confirmRechunk(): Promise<void> {
  if (!chunk.value?.currentChunkRevisionKey) return
  const ok = await runAction(
    rechunkAction.value,
    () => rechunkDocument(documentKey, chunk.value!.currentChunkRevisionKey!, chunk.value!.chunkProfile ?? {}),
    '重新分块已完成',
  )
  if (ok) {
    showRechunk.value = false
    await load()
  }
}

async function doParse(): Promise<void> {
  const ok = await runAction(
    parseAction.value,
    () => triggerParse(documentKey),
    '解析任务已提交',
  )
  if (ok) {
    await refreshStatus()
    startPolling()
  }
}

async function doPublish(): Promise<void> {
  const ok = await runAction(
    publishAction.value,
    () => triggerPublish(documentKey),
    '发布任务已提交',
  )
  if (ok) {
    await refreshStatus()
  }
}

async function retryLatestTask(): Promise<void> {
  const taskKey = document.value?.latestTask?.taskKey
  if (!taskKey) return
  const ok = await runAction(
    retryAction.value,
    () => retryTask(taskKey),
    '任务已重试',
  )
  if (ok) {
    await refreshStatus()
    startPolling()
  }
}

// ---- 生命周期 ----

watch(isParseActive, (active) => {
  if (active) startPolling()
  else stopPolling()
})

onMounted(async () => {
  await load()
  if (isParseActive.value) startPolling()
})

// keep-alive 场景：离开页面停止轮询，返回时按状态恢复
onDeactivated(() => {
  stopPolling()
})

onActivated(() => {
  if (isParseActive.value) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
  // 释放 blobUrl
  if (source.value) URL.revokeObjectURL(source.value.blobUrl)
})
</script>

<template>
  <section class="document-page detail-page">
    <button class="back-link" type="button" @click="router.push({ name: 'documents', params: { knowledgeBaseKey: route.params.knowledgeBaseKey } })">← 返回文档列表</button>

    <p v-if="errorMessage" class="inline-error" role="alert">
      {{ errorMessage }}
      <a-button type="link" size="small" @click="load">重试</a-button>
    </p>

    <div v-else-if="loading" class="state-panel">正在读取文档…</div>

    <template v-else-if="document">
      <header class="document-hero">
        <div>
          <p class="eyebrow">CURRENT SOURCE FILE</p>
          <h1>{{ document.displayName }}</h1>
          <p>{{ document.currentFile.contentType || 'unknown' }} · {{ formatBytes(document.currentFile.size) }}。页面只展示当前文件，不提供版本历史或回退。</p>
        </div>
        <div v-if="workspaceStore.canManage" class="hero-actions">
          <a-button :loading="replaceAction.loading" @click="showReplace = true">上传新文件</a-button>
          <a-button
            type="primary"
            :loading="parseAction.loading"
            :disabled="isParseActive"
            data-test="parse-button"
            @click="doParse"
          >解析文档</a-button>
        </div>
      </header>

      <!-- 解析按钮反馈归属 -->
      <p v-if="parseAction.error" class="inline-error" role="alert" data-test="parse-error">{{ parseAction.error }}</p>
      <p v-if="parseAction.success" class="inline-success" data-test="parse-success">{{ parseAction.success }}</p>

      <!-- 解析进度条 -->
      <div v-if="isParseActive && document.latestTask" class="parse-progress" data-test="parse-progress">
        <p class="eyebrow">PARSING</p>
        <a-progress
          :percent="document.latestTask.progress"
          :status="document.latestTask.status === 'FAILED' ? 'exception' : 'active'"
        />
        <!-- RETRY_WAIT：展示上次失败原因，避免"报错+处理中"同时出现造成困惑 -->
        <template v-if="isRetryWait && document.latestTask.errorMessage">
          <p class="progress-stage">重试中 · {{ document.latestTask.errorCode || '任务失败' }}：{{ document.latestTask.errorMessage }}</p>
        </template>
        <template v-else>
          <p class="progress-stage">{{ document.latestTask.stage || '处理中' }} · {{ document.latestTask.progress }}%</p>
        </template>
      </div>

      <!-- 解析失败 -->
      <div v-if="isParseFailed" class="parse-failed" data-test="parse-failed">
        <p class="eyebrow">PARSE FAILED</p>
        <p v-if="document.latestTask?.errorMessage" class="task-error">{{ document.latestTask.errorCode || '任务失败' }}：{{ document.latestTask.errorMessage }}</p>
        <p v-else class="task-error">解析失败，请重试。</p>
        <a-button
          v-if="workspaceStore.canManage && document.latestTask?.taskKey"
          :loading="retryAction.loading"
          data-test="reparse-btn"
          @click="retryLatestTask"
        >再次解析</a-button>
      </div>

      <DocumentStatusRail class="detail-rail" :document="document" />

      <!-- 源文件预览 + 分块详情并排 -->
      <section class="content-grid">
        <!-- 左侧：源文件预览 -->
        <article class="source-panel" data-test="source-panel">
          <p class="eyebrow">SOURCE FILE</p>
          <h2>{{ document.displayName }}</h2>
          <SourceFilePreview
            v-if="source"
            :content-type="source.contentType"
            :blob-url="source.blobUrl"
            :filename="source.filename"
          />
          <p v-else class="muted-text">源文件内容不可用。</p>
        </article>

        <!-- 右侧：分块列表 + 解析预览 + 发布 -->
        <div class="chunk-panel">
          <article class="chunk-card" data-test="chunk-card">
            <div class="chunk-card-header">
              <div>
                <p class="eyebrow">CHUNKS</p>
                <h2>{{ chunk?.hasChunk ? `${chunk.total} 个分块 · 父块 ${chunk.parentCount} · 子块 ${chunk.childCount}` : '尚无可用分块' }}</h2>
              </div>
              <a-button
                v-if="workspaceStore.canManage && chunk?.hasChunk"
                type="link"
                danger
                :loading="rechunkAction.loading"
                data-test="rechunk-button"
                @click="showRechunk = true"
              >重新分块</a-button>
            </div>
            <p v-if="!chunk?.hasChunk" class="muted-text">未解析或尚未生成分块时，不展示伪造的结构化结果。</p>
            <ChunkTreeList
              v-else
              :chunks="chunk.chunks"
              :page="chunk.page"
              :size="chunk.size"
              :total="chunk.total"
              :loading="chunkLoading"
              data-test="chunk-tree-list"
              @page-change="loadChunkPage"
            />
            <p v-if="rechunkAction.error" class="inline-error" role="alert">{{ rechunkAction.error }}</p>
            <p v-if="rechunkAction.success" class="inline-success">{{ rechunkAction.success }}</p>
          </article>

          <article class="parse-card" data-test="parse-card">
            <p class="eyebrow">PARSE PREVIEW</p>
            <h2>{{ parse?.hasParse ? `${parse.blockCount} 个解析块` : '尚无解析结果' }}</h2>
            <p>{{ parse?.hasParse ? `解析策略：${parse.parserProfile || '默认策略'}` : '仅保留原文件时，不展示虚构的解析预览。' }}</p>
          </article>

          <article class="publish-card" data-test="publish-card">
            <p class="eyebrow">PUBLICATION</p>
            <h2>{{ document.publishStatus === 'PUBLISHED' ? '已进入检索' : '尚未发布' }}</h2>
            <p v-if="document.publishStatus === 'PUBLISH_FAILED' && document.hasActivePublication">最新发布失败，但此前已发布内容继续对检索可用。</p>
            <p v-else>发布仅使用当前成功的分块，不会因处理失败静默替换可用内容。</p>
            <a-button
              v-if="workspaceStore.canManage"
              type="primary"
              :loading="publishAction.loading"
              :disabled="!chunk?.hasChunk"
              data-test="publish-button"
              @click="doPublish"
            >发布到检索</a-button>
            <p v-if="publishAction.error" class="inline-error" role="alert">{{ publishAction.error }}</p>
            <p v-if="publishAction.success" class="inline-success">{{ publishAction.success }}</p>
          </article>
        </div>
      </section>
    </template>

    <AppDialog v-model="showReplace" title="上传新文件" size="md">
      <p class="dialog-description">这会仅更新此文档的当前文件。版本能力在后端保留，但不会在界面展示。</p>
      <a-form class="replace-form" layout="vertical" @submit.prevent="replaceFile">
        <input type="file" @change="chooseReplacement" />
        <footer class="dialog-actions">
          <a-button @click="showReplace = false">取消</a-button>
          <a-button
            type="primary"
            html-type="submit"
            :disabled="!replacement"
            :loading="replaceAction.loading"
          >确认更新</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <AppDialog v-model="showRechunk" title="确认重新分块？" size="md" danger>
      <p class="dialog-description">将删除当前解析结果所对应的既有分块，并按当前分块策略重新处理。原文件和已发布内容不会被删除。</p>
      <footer class="dialog-actions">
        <a-button data-test="cancel-rechunk" @click="showRechunk = false">取消</a-button>
        <a-button
          danger
          type="primary"
          data-test="confirm-rechunk"
          :loading="rechunkAction.loading"
          @click="confirmRechunk"
        >删除旧分块并继续</a-button>
      </footer>
    </AppDialog>
  </section>
</template>

<style scoped>
.document-hero {
  display: flex;
  justify-content: space-between;
  gap: 1.25rem;
  align-items: flex-start;
  margin: 1.25rem 0;
}

.document-hero h1 {
  margin: 0.35rem 0;
}

.document-hero p {
  color: var(--muted-foreground);
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  gap: 0.7rem;
  flex-wrap: wrap;
}

.eyebrow {
  color: #7766e8;
  font-size: 0.65rem;
  letter-spacing: 0.08em;
  margin: 0 0 0.3rem;
}

.detail-rail {
  border: 1px solid var(--border-color);
  border-radius: 13px;
  padding: 0.75rem 1rem;
  background: var(--surface);
}

.parse-progress {
  border: 1px solid var(--border-color);
  border-radius: 13px;
  padding: 0.75rem 1rem;
  background: var(--surface);
  margin: 0.5rem 0;
}

.progress-stage {
  color: var(--muted-foreground);
  font-size: 0.82rem;
  margin: 0.4rem 0 0;
}

.parse-failed {
  border: 1px solid #dd654b;
  border-radius: 13px;
  padding: 0.75rem 1rem;
  background: color-mix(in srgb, #dd654b 8%, var(--surface));
  margin: 0.5rem 0;
}

.task-card,
.content-grid article {
  border: 1px solid var(--border-color);
  border-radius: 17px;
  background: var(--surface);
  padding: 1.2rem;
}

.task-card {
  margin-top: 1rem;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.task-card p,
.content-grid p {
  color: var(--muted-foreground);
  line-height: 1.55;
}

.task-error {
  color: #dd654b !important;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-top: 1rem;
}

.source-panel {
  min-height: 300px;
}

.source-panel h2,
.chunk-panel h2 {
  margin: 0.35rem 0;
}

.chunk-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.chunk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.chunk-card-header h2 {
  font-size: 1rem;
  font-weight: 600;
}

.muted-text {
  color: var(--muted-foreground);
  font-size: 0.85rem;
}

.inline-error {
  color: #dd654b;
  font-size: 0.85rem;
  margin: 0.4rem 0;
}

.inline-success {
  color: #52a677;
  font-size: 0.85rem;
  margin: 0.4rem 0;
}

.back-link {
  border: 0;
  background: none;
  color: var(--muted-foreground);
  font: inherit;
  cursor: pointer;
  padding: 0;
  margin-bottom: 0.5rem;
}

.back-link:hover {
  color: var(--ink, #333);
}

.dialog-description {
  margin: 0;
  color: var(--muted-foreground);
  font-size: 13px;
  line-height: 1.6;
}

.replace-form input {
  margin: 1rem 0;
  width: 100%;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.7rem;
  margin-top: 1.4rem;
}

@media (max-width: 720px) {
  .document-hero,
  .task-card {
    display: grid;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
