<script setup lang="ts">
/**
 * 知识库设置视图（弹窗化）。
 *
 * <p>页面以只读卡片展示当前配置（自动化策略、解析与分块、模型绑定），
 * 每张卡片提供"编辑"按钮，点击后弹出 AppDialog 中央弹窗进行修改，
 * 保存后更新只读展示，操作反馈归属到触发卡片（局部 ref）。
 *
 * <p>模型绑定下拉为空时提供"前往模型设置"直达入口，收敛跨页跳转。
 * 遵循 AC-006 / AC-014：编辑操作统一用 AppDialog，不再使用自实现 drawer。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getKnowledgeBase, updateKnowledgeBase, type KnowledgeBase, type ModelBinding, type SaveKnowledgeBaseInput } from '../../api/knowledge-bases'
import { ApiRequestError } from '../../api/http'
import { listModelConfigs, type ModelConfig } from '../../api/models'
import { useWorkspaceStore } from '../../stores/workspace'
import AppDialog from '../../components/ui/AppDialog.vue'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()

/** 页级读取状态：加载中 / 加载错误 */
const loading = ref(true)
const loadError = ref('')

/** 当前已保存的知识库设置，作为只读展示的事实来源 */
const settings = ref<KnowledgeBase | null>(null)
/** 可用模型配置（仅 ACTIVE），用于模型绑定下拉与名称解析 */
const modelConfigs = ref<ModelConfig[]>([])

/** 三处编辑入口各自独立的弹窗显隐，同一时间最多打开一个 */
const showAutomationDialog = ref(false)
const showProcessingDialog = ref(false)
const showModelBindingDialog = ref(false)

/** 弹窗内编辑草稿：打开弹窗时从 settings 拷贝，保存后写回 */
const draft = reactive({
  autoParse: false,
  autoPublish: false,
  parserProfile: 'standard',
  chunkSize: 800,
  overlap: 120,
  answerProfileKey: '',
  embeddingProfileKey: '',
})

/** 局部反馈：每个区域独立持有 saving/error/saved，反馈归属到触发位置 */
type SectionKind = 'automation' | 'processing' | 'modelBinding'
const feedback = reactive<Record<SectionKind, { saving: boolean; error: string; saved: string }>>({
  automation: { saving: false, error: '', saved: '' },
  processing: { saving: false, error: '', saved: '' },
  modelBinding: { saving: false, error: '', saved: '' },
})

/** 回答生成 / 向量化模型配置候选（按用途过滤） */
const chatModelConfigs = computed(() => modelConfigs.value.filter(item => item.modelType === 'CHAT'))
const embeddingModelConfigs = computed(() => modelConfigs.value.filter(item => item.modelType === 'EMBEDDING'))

/** 当前已绑定的模型 key，用于只读展示解析模型名称 */
const answerBindingKey = computed(() => settings.value?.modelBindings.find(item => item.usageType === 'ANSWER_GENERATION')?.modelConfigKey ?? '')
const embeddingBindingKey = computed(() => settings.value?.modelBindings.find(item => item.usageType === 'EMBEDDING')?.modelConfigKey ?? '')

function parserProfileLabel(profile?: string): string {
  if (profile === 'structure-first') return '结构优先'
  return '标准解析'
}

/** 按 key 解析模型名称，未配置或未绑定显示"未绑定" */
function modelNameByKey(key: string): string {
  if (!key) return '未绑定'
  return modelConfigs.value.find(item => item.modelConfigKey === key)?.modelName ?? '未绑定'
}

async function loadSettings(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [knowledgeBase, configs] = await Promise.all([getKnowledgeBase(String(route.params.knowledgeBaseKey)), listModelConfigs()])
    if (!knowledgeBase) {
      loadError.value = '知识库不存在或已被删除。'
      return
    }
    settings.value = knowledgeBase
    modelConfigs.value = configs.filter(item => item.status === 'ACTIVE')
  } catch (error) {
    loadError.value = error instanceof ApiRequestError ? error.message : '无法读取知识库设置。'
  } finally {
    loading.value = false
  }
}

/** 基于当前 settings 构造保存输入，允许覆盖部分字段（仅提交编辑区域的变更） */
function buildSaveInput(overrides: Partial<SaveKnowledgeBaseInput>): SaveKnowledgeBaseInput | null {
  const kb = settings.value
  if (!kb) return null
  return {
    name: kb.name,
    description: kb.description,
    autoParse: kb.autoParse,
    autoPublish: kb.autoPublish,
    parserProfile: kb.parserProfile,
    chunkProfile: { ...kb.chunkProfile },
    modelBindings: kb.modelBindings.filter(item => item.modelConfigKey),
    revision: kb.revision,
    ...overrides,
  }
}

/** 持久化保存：调用 API 后更新 settings 并把反馈写到触发区域，成功后关闭对应弹窗 */
async function persist(kind: SectionKind, input: SaveKnowledgeBaseInput): Promise<void> {
  feedback[kind].saving = true
  feedback[kind].error = ''
  feedback[kind].saved = ''
  try {
    const saved = await updateKnowledgeBase(String(route.params.knowledgeBaseKey), input)
    if (saved) {
      settings.value = saved
      feedback[kind].saved = '设置已保存，仅应用于之后发起的上传和处理操作。'
      if (kind === 'automation') showAutomationDialog.value = false
      else if (kind === 'processing') showProcessingDialog.value = false
      else showModelBindingDialog.value = false
    }
  } catch (error) {
    feedback[kind].error = error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。'
  } finally {
    feedback[kind].saving = false
  }
}

function openAutomation(): void {
  const kb = settings.value
  if (!kb) return
  draft.autoParse = kb.autoParse
  draft.autoPublish = kb.autoPublish
  feedback.automation.error = ''
  feedback.automation.saved = ''
  showAutomationDialog.value = true
}

function openProcessing(): void {
  const kb = settings.value
  if (!kb) return
  draft.parserProfile = kb.parserProfile
  draft.chunkSize = kb.chunkProfile.chunkSize
  draft.overlap = kb.chunkProfile.overlap
  feedback.processing.error = ''
  feedback.processing.saved = ''
  showProcessingDialog.value = true
}

function openModelBinding(): void {
  const kb = settings.value
  if (!kb) return
  draft.answerProfileKey = answerBindingKey.value
  draft.embeddingProfileKey = embeddingBindingKey.value
  feedback.modelBinding.error = ''
  feedback.modelBinding.saved = ''
  showModelBindingDialog.value = true
}

async function saveAutomation(): Promise<void> {
  // 自动发布依赖自动解析，在提交前拦截
  if (draft.autoPublish && !draft.autoParse) {
    feedback.automation.error = '自动发布依赖自动解析，请先开启自动解析。'
    return
  }
  const input = buildSaveInput({ autoParse: draft.autoParse, autoPublish: draft.autoPublish })
  if (input) await persist('automation', input)
}

async function saveProcessing(): Promise<void> {
  const kb = settings.value
  if (!kb) return
  const input = buildSaveInput({
    parserProfile: draft.parserProfile,
    chunkProfile: { strategy: kb.chunkProfile.strategy, chunkSize: draft.chunkSize, overlap: draft.overlap, titleLevel: kb.chunkProfile.titleLevel },
  })
  if (input) await persist('processing', input)
}

async function saveModelBinding(): Promise<void> {
  // 仅提交已绑定模型的项，过滤空 key
  const bindings: ModelBinding[] = [
    { usageType: 'ANSWER_GENERATION', modelConfigKey: draft.answerProfileKey },
    { usageType: 'EMBEDDING', modelConfigKey: draft.embeddingProfileKey },
  ].filter(item => item.modelConfigKey)
  const input = buildSaveInput({ modelBindings: bindings })
  if (input) await persist('modelBinding', input)
}

/** 跨页直达：模型绑定为空时引导用户前往模型设置 */
function goToModelSettings(): void {
  void router.push({ name: 'model-settings' })
}

onMounted(loadSettings)
</script>

<template>
  <section class="knowledge-page settings-page">
    <header class="page-heading compact">
      <div>
        <button class="back-link" type="button" @click="router.push({ name: 'knowledge-bases' })">← 返回知识库</button>
        <p class="eyebrow">KNOWLEDGE BASE SETTINGS</p>
        <h1>{{ settings?.name || '知识库设置' }}</h1>
        <p>定义之后上传文件的默认加工路径。</p>
      </div>
    </header>

    <p class="notice-panel">
      <b>只影响后续操作</b>
      <span>修改自动解析、自动发布或分块配置，不会重新处理已有文档，也不会隐式批量执行。</span>
    </p>

    <div v-if="loading" class="state-panel">正在读取设置…</div>
    <p v-else-if="loadError" class="inline-error" role="alert">{{ loadError }} <button type="button" @click="loadSettings">重试</button></p>

    <div v-else class="settings-cards">
      <!-- 自动化策略：只读摘要 + 编辑弹窗 -->
      <section class="settings-section" data-test="automation-card">
        <header class="settings-card-head">
          <div>
            <p class="eyebrow">AUTOMATION</p>
            <h2>自动化策略</h2>
          </div>
          <button v-if="workspaceStore.canManage" class="secondary-action" type="button" data-test="edit-automation" @click="openAutomation">编辑</button>
        </header>
        <dl class="settings-summary">
          <div><dt>自动解析</dt><dd>{{ settings?.autoParse ? '已开启' : '已关闭' }}</dd></div>
          <div><dt>自动发布</dt><dd>{{ settings?.autoPublish ? '已开启' : '已关闭' }}</dd></div>
        </dl>
        <p v-if="feedback.automation.saved" class="success-message" role="status">{{ feedback.automation.saved }}</p>
      </section>

      <!-- 解析与分块：只读摘要 + 编辑弹窗 -->
      <section class="settings-section" data-test="processing-card">
        <header class="settings-card-head">
          <div>
            <p class="eyebrow">PROCESSING PROFILE</p>
            <h2>解析与分块</h2>
          </div>
          <button v-if="workspaceStore.canManage" class="secondary-action" type="button" data-test="edit-processing" @click="openProcessing">编辑</button>
        </header>
        <dl class="settings-summary">
          <div><dt>解析策略</dt><dd>{{ parserProfileLabel(settings?.parserProfile) }}</dd></div>
          <div><dt>分块大小</dt><dd>{{ settings?.chunkProfile.chunkSize }}</dd></div>
          <div><dt>重叠量</dt><dd>{{ settings?.chunkProfile.overlap }}</dd></div>
        </dl>
        <p v-if="feedback.processing.saved" class="success-message" role="status">{{ feedback.processing.saved }}</p>
      </section>

      <!-- 模型绑定：只读摘要 + 编辑弹窗，下拉为空时提供直达模型设置入口 -->
      <section class="settings-section" data-test="model-binding-card">
        <header class="settings-card-head">
          <div>
            <p class="eyebrow">MODEL BINDINGS</p>
            <h2>知识库模型用途</h2>
          </div>
          <button v-if="workspaceStore.canManage" class="secondary-action" type="button" data-test="edit-model-binding" @click="openModelBinding">编辑</button>
        </header>
        <p v-if="!modelConfigs.length" class="notice-panel">
          <b>未配置模型</b>
          <span>尚未配置可用模型，系统不会自动使用全局默认模型。<button type="button" class="inline-link" data-test="goto-model-settings" @click="goToModelSettings">前往模型设置</button></span>
        </p>
        <dl v-else class="settings-summary">
          <div><dt>回答生成</dt><dd>{{ modelNameByKey(answerBindingKey) }}</dd></div>
          <div><dt>向量化</dt><dd>{{ modelNameByKey(embeddingBindingKey) }}</dd></div>
        </dl>
        <p v-if="feedback.modelBinding.saved" class="success-message" role="status">{{ feedback.modelBinding.saved }}</p>
      </section>

      <p v-if="!workspaceStore.canManage" class="read-only-note">你当前以知识用户身份查看；服务端也会校验所有修改请求。</p>
    </div>

    <!-- 自动化策略编辑弹窗 -->
    <AppDialog v-model="showAutomationDialog" title="编辑自动化策略" size="md">
      <header class="dialog-header">
        <h2 id="dialog-title">编辑自动化策略</h2>
        <p class="dialog-description">修改后仅影响后续上传与处理，不会回溯已有文档。</p>
      </header>
      <form class="settings-form" @submit.prevent="saveAutomation">
        <div class="toggle-row">
          <div><strong>自动解析</strong><span>文件上传成功后自动进入解析流程。</span></div>
          <input v-model="draft.autoParse" type="checkbox" role="switch" data-test="auto-parse-input" />
        </div>
        <div class="toggle-row">
          <div><strong>自动发布</strong><span>仅当解析成功时，自动发布为可检索知识。</span></div>
          <input v-model="draft.autoPublish" type="checkbox" role="switch" :disabled="!draft.autoParse" data-test="auto-publish-input" />
        </div>
        <p v-if="feedback.automation.error" class="inline-error" role="alert">{{ feedback.automation.error }}</p>
        <footer class="dialog-actions">
          <button class="secondary-action" type="button" @click="showAutomationDialog = false">取消</button>
          <button class="primary-action" type="submit" data-test="save-automation" :disabled="feedback.automation.saving">{{ feedback.automation.saving ? '正在保存…' : '保存' }}</button>
        </footer>
      </form>
    </AppDialog>

    <!-- 解析与分块编辑弹窗 -->
    <AppDialog v-model="showProcessingDialog" title="编辑解析与分块" size="md">
      <header class="dialog-header">
        <h2 id="dialog-title">编辑解析与分块</h2>
        <p class="dialog-description">调整解析策略与分块参数，仅作用于之后发起的解析任务。</p>
      </header>
      <form class="settings-form" @submit.prevent="saveProcessing">
        <div class="form-columns">
          <label>Parser Profile
            <select v-model="draft.parserProfile" data-test="parser-profile-input">
              <option value="standard">标准解析</option>
              <option value="structure-first">结构优先</option>
            </select>
          </label>
          <label>分块大小<input v-model.number="draft.chunkSize" type="number" min="100" data-test="chunk-size-input" /></label>
          <label>重叠量<input v-model.number="draft.overlap" type="number" min="0" data-test="overlap-input" /></label>
        </div>
        <p v-if="feedback.processing.error" class="inline-error" role="alert">{{ feedback.processing.error }}</p>
        <footer class="dialog-actions">
          <button class="secondary-action" type="button" @click="showProcessingDialog = false">取消</button>
          <button class="primary-action" type="submit" data-test="save-processing" :disabled="feedback.processing.saving">{{ feedback.processing.saving ? '正在保存…' : '保存' }}</button>
        </footer>
      </form>
    </AppDialog>

    <!-- 模型绑定编辑弹窗 -->
    <AppDialog v-model="showModelBindingDialog" title="编辑知识库模型用途" size="md">
      <header class="dialog-header">
        <h2 id="dialog-title">编辑知识库模型用途</h2>
        <p class="dialog-description">为回答生成与向量化选择模型配置；系统不会自动回退到全局默认。</p>
      </header>
      <form class="settings-form" @submit.prevent="saveModelBinding">
        <p v-if="!modelConfigs.length" class="notice-panel">
          <b>未配置模型</b>
          <span>尚未配置可用模型。<button type="button" class="inline-link" data-test="goto-model-settings-dialog" @click="goToModelSettings">前往模型设置</button></span>
        </p>
        <div v-else class="form-columns">
          <label>回答生成
            <select v-model="draft.answerProfileKey" data-test="answer-profile-input">
              <option value="">未绑定</option>
              <option v-for="config in chatModelConfigs" :key="config.modelConfigKey" :value="config.modelConfigKey">{{ config.modelName }} · {{ config.lastTestStatus }}</option>
            </select>
          </label>
          <label>向量化
            <select v-model="draft.embeddingProfileKey" data-test="embedding-profile-input">
              <option value="">未绑定</option>
              <option v-for="config in embeddingModelConfigs" :key="config.modelConfigKey" :value="config.modelConfigKey">{{ config.modelName }} · {{ config.lastTestStatus }}</option>
            </select>
          </label>
        </div>
        <p v-if="feedback.modelBinding.error" class="inline-error" role="alert">{{ feedback.modelBinding.error }}</p>
        <footer class="dialog-actions">
          <button class="secondary-action" type="button" @click="showModelBindingDialog = false">取消</button>
          <button class="primary-action" type="submit" data-test="save-model-binding" :disabled="feedback.modelBinding.saving">{{ feedback.modelBinding.saving ? '正在保存…' : '保存' }}</button>
        </footer>
      </form>
    </AppDialog>
  </section>
</template>

<style scoped>
/* 设置卡片网格：只读摘要 + 编辑入口，复用全局 .settings-section 卡片样式 */
.settings-cards {
  display: grid;
  gap: 16px;
}

/* 卡片头部：标题区与编辑按钮左右分布 */
.settings-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

/* 只读摘要：键值对网格，三列自适应 */
.settings-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}

.settings-summary > div {
  display: grid;
  gap: 4px;
}

.settings-summary dt {
  color: var(--ink-soft);
  font-size: 12px;
}

.settings-summary dd {
  margin: 0;
  color: var(--ink);
  font-weight: 600;
}

/* 行内直达链接：模型设置等跨页入口 */
.inline-link {
  padding: 0;
  border: 0;
  margin-left: 6px;
  color: var(--violet);
  background: transparent;
  font-weight: 700;
  text-decoration: underline;
}

/* 弹窗头部：标题与说明 */
.dialog-header h2 {
  margin: 0 0 4px;
  font-size: 21px;
  letter-spacing: -.02em;
}

.dialog-description {
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

/* 弹窗操作区：右对齐按钮组 */
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
}

@media (max-width: 760px) {
  .settings-summary {
    grid-template-columns: 1fr;
  }
}
</style>
