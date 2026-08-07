<script setup lang="ts">
/**
 * 源文件预览组件。
 *
 * <p>根据 contentType 选择渲染策略：
 * - image/*: <img> 直接渲染
 * - application/pdf: <iframe> 嵌入浏览器 PDF Viewer
 * - text/markdown: markdown-it 转 HTML 后 v-html 展示
 * - text/plain: <pre> 渲染，限制前 100KB 避免卡顿
 * - DOCX: mammoth.js 转 HTML 后 v-html 展示
 * - 不支持的格式: 显示下载链接
 *
 * <p>markdown-it 和 mammoth.js 按需动态 import，避免首屏加载。
 * 组件卸载时释放内部创建的 blobUrl。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  contentType: string
  blobUrl: string
  filename?: string
}>(), {
  filename: 'unknown',
})

/** TXT 预览最大字节数，超过截断避免卡顿。 */
const TXT_MAX_BYTES = 100 * 1024

/** 渲染策略类型。 */
type RenderStrategy = 'image' | 'pdf' | 'markdown' | 'text' | 'docx' | 'unsupported'

/** 根据 contentType 选择渲染策略（纯展示逻辑，不含业务规则）。 */
function resolveStrategy(contentType: string): RenderStrategy {
  if (contentType.startsWith('image/')) return 'image'
  if (contentType === 'application/pdf') return 'pdf'
  if (contentType === 'text/markdown' || contentType === 'text/x-markdown') return 'markdown'
  if (contentType === 'text/plain') return 'text'
  if (contentType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') return 'docx'
  return 'unsupported'
}

const strategy = computed(() => resolveStrategy(props.contentType))

/** 需要异步加载内容的格式。 */
const needsFetch = computed(() => ['markdown', 'text', 'docx'].includes(strategy.value))

/** 异步加载的 HTML 内容（markdown/docx 转换后）或纯文本（text）。 */
const htmlContent = ref('')
const textContent = ref('')
const truncated = ref(false)
const loading = ref(false)
const loadError = ref('')

/** 内部创建的 blobUrl（需要 fetch 后创建新的 blobUrl 用于下载），卸载时释放。 */
const internalBlobUrls: string[] = []

/** 从 blobUrl fetch 文本内容。 */
async function fetchText(url: string): Promise<string> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.text()
}

/** 从 blobUrl fetch ArrayBuffer 内容。 */
async function fetchArrayBuffer(url: string): Promise<ArrayBuffer> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.arrayBuffer()
}

/** 加载并渲染内容。 */
async function loadContent(): Promise<void> {
  if (!needsFetch.value) return
  loading.value = true
  loadError.value = ''
  htmlContent.value = ''
  textContent.value = ''
  truncated.value = false

  try {
    if (strategy.value === 'markdown') {
      const text = await fetchText(props.blobUrl)
      // 动态 import markdown-it，避免首屏加载
      const MarkdownIt = (await import('markdown-it')).default
      const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
      htmlContent.value = md.render(text)
    } else if (strategy.value === 'text') {
      const text = await fetchText(props.blobUrl)
      if (text.length > TXT_MAX_BYTES) {
        textContent.value = text.slice(0, TXT_MAX_BYTES)
        truncated.value = true
      } else {
        textContent.value = text
      }
    } else if (strategy.value === 'docx') {
      const arrayBuffer = await fetchArrayBuffer(props.blobUrl)
      // 动态 import mammoth.js，避免首屏加载
      const mammoth = await import('mammoth')
      const result = await mammoth.convertToHtml({ arrayBuffer })
      htmlContent.value = result.value
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '内容加载失败'
  } finally {
    loading.value = false
  }
}

/** 监听 props 变化重新加载。 */
watch(() => [props.blobUrl, props.contentType], loadContent, { immediate: true })

/** 组件卸载时释放内部创建的 blobUrl。 */
onBeforeUnmount(() => {
  for (const url of internalBlobUrls) {
    URL.revokeObjectURL(url)
  }
})
</script>

<template>
  <div class="source-file-preview" data-test="source-file-preview">
    <!-- 图片：直接渲染 -->
    <div v-if="strategy === 'image'" class="preview-image">
      <img :src="blobUrl" :alt="filename" data-test="preview-image" />
    </div>

    <!-- PDF：iframe 嵌入 -->
    <div v-else-if="strategy === 'pdf'" class="preview-pdf">
      <iframe :src="blobUrl" frameborder="0" width="100%" height="600px" :title="filename" data-test="preview-pdf" />
    </div>

    <!-- Markdown：转 HTML 渲染 -->
    <div v-else-if="strategy === 'markdown'" class="preview-markdown">
      <p v-if="loading" class="preview-loading" data-test="preview-loading">正在加载内容…</p>
      <div v-else-if="loadError" class="preview-error">
        <p class="error-text" role="alert">内容加载失败：{{ loadError }}</p>
        <a :href="blobUrl" :download="filename" data-test="download-link">下载文件</a>
      </div>
      <div v-else class="markdown-body" data-test="preview-html" v-html="htmlContent" />
    </div>

    <!-- 纯文本：<pre> 渲染，限制 100KB -->
    <div v-else-if="strategy === 'text'" class="preview-text">
      <p v-if="loading" class="preview-loading" data-test="preview-loading">正在加载内容…</p>
      <div v-else-if="loadError" class="preview-error">
        <p class="error-text" role="alert">内容加载失败：{{ loadError }}</p>
        <a :href="blobUrl" :download="filename" data-test="download-link">下载文件</a>
      </div>
      <div v-else>
        <pre data-test="preview-text">{{ textContent }}</pre>
        <p v-if="truncated" class="truncate-notice">（内容超过 100KB，已截断显示前 100KB。完整内容请下载文件。）</p>
      </div>
    </div>

    <!-- DOCX：mammoth.js 转 HTML 渲染 -->
    <div v-else-if="strategy === 'docx'" class="preview-docx">
      <p v-if="loading" class="preview-loading" data-test="preview-loading">正在转换文档…</p>
      <div v-else-if="loadError" class="preview-error">
        <p class="error-text" role="alert">文档转换失败：{{ loadError }}</p>
        <a :href="blobUrl" :download="filename" data-test="download-link">下载文件</a>
      </div>
      <div v-else class="docx-body" data-test="preview-html" v-html="htmlContent" />
    </div>

    <!-- 不支持的格式：下载链接 -->
    <div v-else class="preview-unsupported">
      <p class="unsupported-text">该格式（{{ contentType }}）不支持在线预览。</p>
      <a :href="blobUrl" :download="filename" data-test="download-link">下载 {{ filename }}</a>
    </div>
  </div>
</template>

<style scoped>
.source-file-preview {
  width: 100%;
  min-height: 200px;
}

.preview-image {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1rem;
}

.preview-image img {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
}

.preview-pdf {
  width: 100%;
}

.preview-pdf iframe {
  border: 1px solid var(--line, var(--border-color, #ddd));
  border-radius: 8px;
}

.preview-markdown,
.preview-text,
.preview-docx {
  padding: 1rem;
}

.markdown-body,
.docx-body {
  line-height: 1.7;
  color: var(--ink, #333);
}

.markdown-body :deep(h1),
.docx-body :deep(h1) {
  font-size: 1.5rem;
  margin: 1rem 0;
}

.markdown-body :deep(h2),
.docx-body :deep(h2) {
  font-size: 1.25rem;
  margin: 0.8rem 0;
}

.markdown-body :deep(p),
.docx-body :deep(p) {
  margin: 0.5rem 0;
}

.markdown-body :deep(code),
.docx-body :deep(code) {
  background: var(--line-soft, #f5f5f5);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: "Roboto Mono", monospace;
  font-size: 0.9em;
}

.markdown-body :deep(pre),
.docx-body :deep(pre) {
  background: var(--line-soft, #f5f5f5);
  padding: 0.75rem;
  border-radius: 6px;
  overflow-x: auto;
}

.preview-text pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: "Roboto Mono", monospace;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--ink, #333);
  background: var(--line-soft, #f9f9f9);
  padding: 1rem;
  border-radius: 8px;
  max-height: 600px;
  overflow-y: auto;
}

.truncate-notice {
  margin-top: 0.5rem;
  color: var(--ink-soft, #888);
  font-size: 0.8rem;
}

.preview-loading {
  color: var(--ink-soft, #888);
  text-align: center;
  padding: 2rem;
}

.preview-error {
  text-align: center;
  padding: 2rem;
}

.error-text {
  color: var(--danger, #d54848);
  margin-bottom: 0.5rem;
}

.preview-unsupported {
  text-align: center;
  padding: 2rem;
}

.unsupported-text {
  color: var(--ink-soft, #888);
  margin-bottom: 0.5rem;
}

.preview-unsupported a,
.preview-error a {
  color: var(--violet, #7565e8);
  text-decoration: underline;
  cursor: pointer;
}
</style>
