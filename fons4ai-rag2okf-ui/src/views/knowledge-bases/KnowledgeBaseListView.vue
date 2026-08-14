<script setup lang="ts">
/**
 * 知识库列表视图。
 *
 * <p>展示当前工作空间的知识库卡片，支持：
 * - 创建知识库（管理员，右侧抽屉表单）
 * - 重命名知识库（AppDialog size sm，仅提交 name）
 * - 删除知识库（AppDialog danger + persistent，需输入名称确认防止误删）
 * - 搜索与刷新
 *
 * <p>权限判断：前端根据 canDelete 控制删除入口可见性，服务端二次校验创建者。
 * 遵循 AC-003/004/005/014 与技术设计 §5.7。
 */
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
  type KnowledgeBaseSummary,
} from '../../api/knowledge-bases'
import { ApiRequestError } from '../../api/http'
import { useWorkspaceStore } from '../../stores/workspace'
import { formatTime } from '../../utils/formatters'
import AppDialog from '../../components/ui/AppDialog.vue'

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

// 卡片操作菜单：记录当前展开菜单的 knowledgeBaseKey，同一时间仅一张卡片展开菜单
const openMenuKey = ref<string | null>(null)

// 重命名状态：renameTarget 持有当前重命名的知识库，反馈归属到该弹窗
const renameTarget = ref<KnowledgeBaseSummary | null>(null)
const renameName = ref('')
const renameError = ref('')
const renaming = ref(false)

// 删除状态：deleteTarget 持有当前删除的知识库，反馈归属到该弹窗
const deleteTarget = ref<KnowledgeBaseSummary | null>(null)
const deleteConfirmName = ref('')
const deleteError = ref('')
const deleting = ref(false)

/** a-input-number 桥接：分块大小为非空 number，组件值可能为 string，需转换 */
const chunkSizeModel = computed<string | number>({
  get: () => form.chunkSize,
  set: (v) => {
    const n = typeof v === 'number' ? v : Number(v)
    form.chunkSize = Number.isNaN(n) ? 800 : n
  },
})

const filteredKnowledgeBases = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  if (!keyword) return knowledgeBases.value
  return knowledgeBases.value.filter((item) => `${item.name}${item.description}`.toLocaleLowerCase().includes(keyword))
})

/** 是否有重命名弹窗打开 */
const showRenameDialog = computed(() => renameTarget.value !== null)

/** 是否有删除确认弹窗打开 */
const showDeleteDialog = computed(() => deleteTarget.value !== null)

/** 删除确认按钮是否可用：正在删除时禁用，名称匹配校验在点击时执行并提示错误 */

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
      ownerUserKey: created.ownerUserKey,
      canDelete: created.canDelete,
    }, ...knowledgeBases.value]
    showCreatePanel.value = false
  } catch (error) {
    formError.value = error instanceof ApiRequestError ? error.message : '创建失败，请稍后重试。'
  } finally {
    creating.value = false
  }
}

/** 切换卡片操作菜单展开/关闭 */
function toggleMenu(knowledgeBaseKey: string, event: Event): void {
  event.stopPropagation()
  openMenuKey.value = openMenuKey.value === knowledgeBaseKey ? null : knowledgeBaseKey
}

/** 打开重命名弹窗，预填当前名称 */
function openRename(target: KnowledgeBaseSummary): void {
  renameTarget.value = target
  renameName.value = target.name
  renameError.value = ''
  openMenuKey.value = null
}

/** 提交重命名：仅提交 name，调用 updateKnowledgeBase */
async function submitRename(): Promise<void> {
  if (!renameTarget.value) return
  const name = renameName.value.trim()
  if (!name) {
    renameError.value = '请输入知识库名称。'
    return
  }
  if (name === renameTarget.value.name) {
    renameError.value = '新名称与当前名称相同，无需修改。'
    return
  }
  renaming.value = true
  renameError.value = ''
  try {
    // 重命名仅提交 name；revision 传 0 表示不校验版本（服务端按 name 更新）
    await updateKnowledgeBase(renameTarget.value.knowledgeBaseKey, { name, revision: 0 })
    const idx = knowledgeBases.value.findIndex((kb) => kb.knowledgeBaseKey === renameTarget.value!.knowledgeBaseKey)
    if (idx !== -1) {
      knowledgeBases.value[idx] = { ...knowledgeBases.value[idx], name }
    }
    renameTarget.value = null
  } catch (error) {
    renameError.value = error instanceof ApiRequestError ? error.message : '重命名失败，请稍后重试。'
  } finally {
    renaming.value = false
  }
}

/** 打开删除确认弹窗 */
function openDelete(target: KnowledgeBaseSummary): void {
  deleteTarget.value = target
  deleteConfirmName.value = ''
  deleteError.value = ''
  openMenuKey.value = null
}

/** 提交删除：名称匹配后调用 deleteKnowledgeBase，成功后从列表移除 */
async function submitDelete(): Promise<void> {
  if (!deleteTarget.value) return
  if (deleteConfirmName.value.trim() !== deleteTarget.value.name) {
    deleteError.value = '输入的名称与知识库名称不匹配，请重新输入。'
    return
  }
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteKnowledgeBase(deleteTarget.value.knowledgeBaseKey)
    const targetKey = deleteTarget.value.knowledgeBaseKey
    knowledgeBases.value = knowledgeBases.value.filter((kb) => kb.knowledgeBaseKey !== targetKey)
    deleteTarget.value = null
  } catch (error) {
    deleteError.value = error instanceof ApiRequestError ? error.message : '删除失败，请稍后重试。'
  } finally {
    deleting.value = false
  }
}

function openSettings(knowledgeBaseKey: string): void {
  router.push({ name: 'knowledge-base-settings', params: { knowledgeBaseKey } })
}

/** 点击卡片外部关闭操作菜单 */
function handleDocumentClick(event: MouseEvent): void {
  if (!openMenuKey.value) return
  const target = event.target as HTMLElement
  if (!target.closest('.card-menu-wrap')) {
    openMenuKey.value = null
  }
}

onMounted(() => {
  loadKnowledgeBases()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
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
        <div class="card-top">
          <span class="knowledge-glyph">◇</span>
          <a-tag :color="knowledgeBase.autoPublish ? 'green' : knowledgeBase.autoParse ? 'blue' : 'default'">{{ knowledgeBase.autoPublish ? '自动发布' : knowledgeBase.autoParse ? '自动解析' : '仅保留原文件' }}</a-tag>
          <div v-if="workspaceStore.canManage" class="card-menu-wrap">
            <button class="card-menu-trigger" type="button" :data-test="`card-menu-trigger-${knowledgeBase.knowledgeBaseKey}`" :aria-label="`${knowledgeBase.name} 操作菜单`" @click="toggleMenu(knowledgeBase.knowledgeBaseKey, $event)">⋯</button>
            <div v-if="openMenuKey === knowledgeBase.knowledgeBaseKey" class="card-menu" :data-test="`card-menu-${knowledgeBase.knowledgeBaseKey}`" role="menu">
              <button type="button" role="menuitem" :data-test="`rename-action-${knowledgeBase.knowledgeBaseKey}`" @click.stop="openRename(knowledgeBase)">重命名</button>
              <button v-if="knowledgeBase.canDelete" type="button" role="menuitem" class="danger-item" :data-test="`delete-action-${knowledgeBase.knowledgeBaseKey}`" @click.stop="openDelete(knowledgeBase)">删除</button>
            </div>
          </div>
        </div>
        <h2>{{ knowledgeBase.name }}</h2><p>{{ knowledgeBase.description || '尚未填写描述。' }}</p>
        <div class="card-settings"><span>解析：{{ knowledgeBase.autoParse ? '自动' : '手动' }}</span><span>发布：{{ knowledgeBase.autoPublish ? '自动' : '手动' }}</span></div>
        <footer><time>{{ formatTime(knowledgeBase.updated) }} 更新</time><a-button v-if="workspaceStore.canManage" type="link" size="small" @click.stop="openSettings(knowledgeBase.knowledgeBaseKey)">管理设置 -&gt;</a-button><span v-else>点击查看文档</span></footer>
      </article>
    </div>

    <!-- 创建知识库弹窗（居中模态，替代原右侧抽屉） -->
    <AppDialog v-model="showCreatePanel" title="创建知识库" size="md">
      <p class="dialog-description">默认设置仅对之后上传的文件生效，不会追溯处理已有文档。</p>
      <a-form layout="vertical" @submit.prevent="submitCreate">
        <a-form-item label="名称">
          <a-input v-model:value="form.name" data-test="knowledge-base-name" :maxlength="80" autocomplete="off" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" data-test="knowledge-base-description" :maxlength="500" :rows="3" />
        </a-form-item>
        <div class="toggle-row"><div><strong>自动解析</strong><span>上传后创建解析任务</span></div><a-switch v-model:checked="form.autoParse" data-test="create-auto-parse-input" /></div>
        <div class="toggle-row"><div><strong>自动发布</strong><span>自动或手动解析成功后发布用于检索</span></div><a-switch v-model:checked="form.autoPublish" data-test="create-auto-publish-input" /></div>
        <div class="form-columns">
          <a-form-item label="解析策略">
            <a-select v-model:value="form.parserProfile">
              <a-select-option value="standard">标准解析</a-select-option>
              <a-select-option value="structure-first">结构优先</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="分块大小">
            <a-input-number v-model:value="chunkSizeModel" :min="100" style="width: 100%" />
          </a-form-item>
        </div>
        <a-alert v-if="formError" type="error" :message="formError" show-icon class="form-error" />
        <footer class="dialog-actions">
          <a-button @click="showCreatePanel = false">取消</a-button>
          <a-button type="primary" html-type="submit" data-test="submit-knowledge-base" :loading="creating">创建知识库</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <!-- 重命名弹窗 -->
    <AppDialog v-model="showRenameDialog" title="重命名知识库" size="sm">
      <p class="dialog-description">为「{{ renameTarget?.name }}」输入新的名称。</p>
      <a-form layout="vertical" @submit.prevent="submitRename">
        <a-form-item label="名称" :validate-status="renameError ? 'error' : ''" :help="renameError">
          <a-input v-model:value="renameName" data-test="rename-input" :maxlength="80" autocomplete="off" />
        </a-form-item>
        <footer class="dialog-actions">
          <a-button @click="renameTarget = null">取消</a-button>
          <a-button type="primary" html-type="submit" data-test="rename-submit" :loading="renaming">保存</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <!-- 删除确认弹窗（danger + persistent，需输入名称匹配确认） -->
    <AppDialog v-model="showDeleteDialog" title="删除知识库" size="sm" danger persistent>
      <p class="dialog-description danger-text">此操作不可撤销。知识库「{{ deleteTarget?.name }}」下的所有来源文件、已发布知识和检索索引将被永久删除。</p>
      <p class="dialog-description">请输入知识库名称 <strong>{{ deleteTarget?.name }}</strong> 以确认删除：</p>
      <a-form layout="vertical">
        <a-form-item label="知识库名称" :validate-status="deleteError ? 'error' : ''" :help="deleteError">
          <a-input v-model:value="deleteConfirmName" data-test="delete-confirm-input" :maxlength="80" autocomplete="off" />
        </a-form-item>
        <footer class="dialog-actions">
          <a-button data-test="delete-cancel" @click="deleteTarget = null">取消</a-button>
          <a-button type="primary" danger data-test="delete-confirm-submit" :loading="deleting" @click="submitDelete">确认删除</a-button>
        </footer>
      </a-form>
    </AppDialog>
  </section>
</template>

<style scoped>
/* 卡片操作菜单容器：定位在下拉触发按钮旁 */
.card-menu-wrap {
  position: relative;
  margin-left: auto;
}

/* 三点按钮 */
.card-menu-trigger {
  background: none;
  border: none;
  color: var(--ink-soft);
  font-size: 18px;
  line-height: 1;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.card-menu-trigger:hover {
  background: var(--line-soft);
  color: var(--ink);
}

/* 下拉操作菜单 */
.card-menu {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 20;
  min-width: 120px;
  margin-top: 4px;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow);
}

.card-menu button {
  display: block;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  color: var(--ink);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}

.card-menu button:hover {
  background: var(--line-soft);
}

.card-menu .danger-item {
  color: var(--danger);
}

.card-menu .danger-item:hover {
  background: var(--danger-soft, rgba(213, 72, 72, 0.1));
}

/* 弹窗内表单与操作区 */
.rename-field {
  display: block;
  margin: 4px 0;
}

.rename-field input {
  width: 100%;
  padding: 8px 10px;
  margin-top: 4px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
}

.dialog-description {
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.6;
}

.danger-text {
  color: var(--danger);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

/* 删除确认按钮使用红色背景强调破坏性 */
.danger-btn {
  background: var(--danger);
  box-shadow: 0 8px 20px rgb(213 72 72 / 25%);
}

.danger-btn:disabled {
  opacity: 0.5;
  box-shadow: none;
}
</style>
