<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  createKnowledgeBase,
  listKnowledgeBases,
  type KnowledgeBaseSummary,
} from '../../api/knowledge-bases'
import { ApiRequestError } from '../../api/http'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatTime } from '../../utils/formatters'

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const knowledgeBases = ref<KnowledgeBaseSummary[]>([])
const loading = ref(false)
const loadError = ref('')
const creating = ref(false)
const showCreatePanel = ref(false)
const searchText = ref('')
const formError = ref('')
const form = reactive({
  name: '',
  description: '',
  autoParse: true,
  autoPublish: false,
  parserProfile: 'standard',
  chunkSize: 800,
  overlap: 120,
})

const filteredKnowledgeBases = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  if (!keyword) return knowledgeBases.value
  return knowledgeBases.value.filter((item) => `${item.name}${item.description}`.toLocaleLowerCase().includes(keyword))
})

function resetForm(): void {
  Object.assign(form, { name: '', description: '', autoParse: true, autoPublish: false, parserProfile: 'standard', chunkSize: 800, overlap: 120 })
  formError.value = ''
}

async function loadKnowledgeBases(): Promise<void> {
  if (!workspaceStore.currentWorkspace.key) {
    loadError.value = '尚未提供工作空间标识，暂时无法读取知识库。'
    knowledgeBases.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const page = await listKnowledgeBases(workspaceStore.currentWorkspace.key)
    knowledgeBases.value = page.records
  } catch (error) {
    loadError.value = error instanceof ApiRequestError ? error.message : '知识库加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function openCreatePanel(): void {
  resetForm()
  showCreatePanel.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.name.trim()) {
    formError.value = '请输入知识库名称。'
    return
  }
  if (form.autoPublish && !form.autoParse) {
    formError.value = '自动发布依赖自动解析，请先开启自动解析。'
    return
  }
  creating.value = true
  formError.value = ''
  try {
    const created = await createKnowledgeBase(workspaceStore.currentWorkspace.key, {
      name: form.name.trim(),
      description: form.description.trim(),
      autoParse: form.autoParse,
      autoPublish: form.autoPublish,
      parserProfile: form.parserProfile,
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: form.chunkSize, overlap: form.overlap, titleLevel: null },
      modelBindings: [],
      revision: 0,
    })
    knowledgeBases.value = [{
      knowledgeBaseKey: created.knowledgeBaseKey,
      name: created.name,
      description: created.description,
      autoParse: created.autoParse,
      autoPublish: created.autoPublish,
      updated: created.updated,
    }, ...knowledgeBases.value]
    showCreatePanel.value = false
  } catch (error) {
    formError.value = error instanceof ApiRequestError ? error.message : '创建失败，请稍后重试。'
  } finally {
    creating.value = false
  }
}

function openSettings(knowledgeBaseKey: string): void {
  router.push({ name: 'knowledge-base-settings', params: { knowledgeBaseKey } })
}

onMounted(loadKnowledgeBases)
</script>

<template>
  <section class="knowledge-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">KNOWLEDGE SPACES</p>
        <h1>我的知识库</h1>
        <p>在独立空间中组织来源文件、处理策略和已发布知识。</p>
      </div>
      <button v-if="workspaceStore.canManage" class="primary-action" type="button" data-test="create-knowledge-base" @click="openCreatePanel">＋ 创建知识库</button>
    </header>

    <section class="workspace-pulse" aria-label="工作空间状态">
      <div><p class="eyebrow">WORKSPACE PULSE</p><strong>{{ workspaceStore.currentWorkspace.name }}</strong><span>{{ workspaceStore.canManage ? '管理员可创建知识库并维护默认处理策略。' : '知识用户可查看已有知识库；管理操作由服务端再次校验。' }}</span></div>
      <div class="pulse-metrics"><span><b>{{ knowledgeBases.length.toString().padStart(2, '0') }}</b> 知识库</span><span><b>{{ knowledgeBases.filter(item => item.autoPublish).length.toString().padStart(2, '0') }}</b> 自动发布</span></div>
    </section>

    <section class="library-toolbar" aria-label="知识库筛选">
      <label class="list-search"><span>⌕</span><input v-model="searchText" placeholder="搜索知识库" /></label>
      <span class="filter-pill">全部 {{ knowledgeBases.length }}</span>
      <span class="filter-pill success">自动解析 {{ knowledgeBases.filter(item => item.autoParse).length }}</span>
      <span class="toolbar-spacer"></span>
      <button class="text-button" type="button" :disabled="loading" @click="loadKnowledgeBases">↻ 刷新</button>
    </section>

    <p v-if="loadError" class="inline-error" role="alert">{{ loadError }} <button type="button" @click="loadKnowledgeBases">重试</button></p>
    <div v-else-if="loading" class="state-panel" aria-live="polite">正在读取知识库…</div>
    <div v-else-if="filteredKnowledgeBases.length === 0" class="empty-panel">
      <span>◇</span><strong>{{ searchText ? '没有匹配的知识库' : '还没有知识库' }}</strong>
      <p>{{ searchText ? '换一个关键词试试。' : '创建一个独立空间，开始组织来源文件和知识策略。' }}</p>
      <button v-if="workspaceStore.canManage && !searchText" class="primary-action" type="button" @click="openCreatePanel">创建新的知识空间</button>
    </div>
    <div v-else class="knowledge-grid">
      <article v-for="knowledgeBase in filteredKnowledgeBases" :key="knowledgeBase.knowledgeBaseKey" class="knowledge-card" @click="router.push({ name: 'documents', params: { knowledgeBaseKey: knowledgeBase.knowledgeBaseKey } })">
        <div class="card-top"><span class="knowledge-glyph">◇</span><span class="status-chip" :class="knowledgeBase.autoPublish ? 'published' : 'pending'">{{ knowledgeBase.autoPublish ? '自动发布' : knowledgeBase.autoParse ? '自动解析' : '仅保留原文件' }}</span></div>
        <h2>{{ knowledgeBase.name }}</h2><p>{{ knowledgeBase.description || '尚未填写描述。' }}</p>
        <div class="card-settings"><span>解析：{{ knowledgeBase.autoParse ? '自动' : '手动' }}</span><span>发布：{{ knowledgeBase.autoPublish ? '自动' : '手动' }}</span></div>
        <footer><time>{{ formatTime(knowledgeBase.updated) }} 更新</time><button v-if="workspaceStore.canManage" type="button" @click.stop="openSettings(knowledgeBase.knowledgeBaseKey)">管理设置 -></button><span v-else>点击查看文档</span></footer>
      </article>
    </div>

    <div v-if="showCreatePanel" class="drawer-backdrop" role="presentation" @click.self="showCreatePanel = false">
      <form class="settings-drawer" @submit.prevent="submitCreate">
        <p class="eyebrow">NEW KNOWLEDGE SPACE</p><h2>创建知识库</h2><p class="drawer-description">默认设置仅对之后上传的文件生效，不会追溯处理已有文档。</p>
        <label>名称<input v-model="form.name" data-test="knowledge-base-name" maxlength="80" autocomplete="off" /></label>
        <label>描述<textarea v-model="form.description" data-test="knowledge-base-description" maxlength="500" rows="3" /></label>
        <div class="toggle-row"><div><strong>自动解析</strong><span>上传后创建解析任务</span></div><input v-model="form.autoParse" type="checkbox" role="switch" /></div>
        <div class="toggle-row"><div><strong>自动发布</strong><span>解析成功后直接发布用于检索</span></div><input v-model="form.autoPublish" type="checkbox" role="switch" :disabled="!form.autoParse" /></div>
        <div class="form-columns"><label>解析策略<select v-model="form.parserProfile"><option value="standard">标准解析</option><option value="structure-first">结构优先</option></select></label><label>分块大小<input v-model.number="form.chunkSize" type="number" min="100" /></label></div>
        <p v-if="formError" class="inline-error" role="alert">{{ formError }}</p>
        <footer><button class="secondary-action" type="button" @click="showCreatePanel = false">取消</button><button class="primary-action" type="submit" data-test="submit-knowledge-base" :disabled="creating">{{ creating ? '正在创建…' : '创建知识库' }}</button></footer>
      </form>
    </div>
  </section>
</template>
