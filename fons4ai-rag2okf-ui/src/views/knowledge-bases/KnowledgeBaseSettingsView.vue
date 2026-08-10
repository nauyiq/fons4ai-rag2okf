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
import { getDefaultModels, listProfiles, type DefaultModelSettings, type ModelProfile } from '../../api/models'
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
/** 可用模型档案（仅 ACTIVE），用于模型绑定下拉与名称解析 */
const modelProfiles = ref<ModelProfile[]>([])
/** 当前用户的全局默认模型，仅用于空 binding 打开编辑弹窗时预填。 */
const defaultModels = ref<DefaultModelSettings>({ defaults: {} })

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

/** 回答生成 / 向量化模型档案候选（按用途过滤） */
const chatModelProfiles = computed(() => modelProfiles.value.filter(item => item.modelType === 'LLM'))
const embeddingModelProfiles = computed(() => modelProfiles.value.filter(item => item.modelType === 'EMBEDDING'))

/** 当前已绑定的模型 key，用于只读展示解析模型名称 */
const answerBindingKey = computed(() => settings.value?.modelBindings.find(item => item.usageType === 'ANSWER_GENERATION')?.profileKey ?? '')
const embeddingBindingKey = computed(() => settings.value?.modelBindings.find(item => item.usageType === 'EMBEDDING')?.profileKey ?? '')

/** a-input-number 桥接：分块大小/重叠量为非空 number，组件值可能为 string，需转换 */
const chunkSizeModel = computed<string | number>({
  get: () => draft.chunkSize,
  set: (v) => {
    const n = typeof v === 'number' ? v : Number(v)
    draft.chunkSize = Number.isNaN(n) ? 800 : n
  },
})
const overlapModel = computed<string | number>({
  get: () => draft.overlap,
  set: (v) => {
    const n = typeof v === 'number' ? v : Number(v)
    draft.overlap = Number.isNaN(n) ? 120 : n
  },
})

function parserProfileLabel(profile?: string): string {
  if (profile === 'structure-first') return '结构优先'
  return '标准解析'
}

/** 按 key 解析模型名称，未配置或未绑定显示"未绑定" */
function modelNameByKey(key: string): string {
  if (!key) return '未绑定'
  return modelProfiles.value.find(item => item.profileKey === key)?.modelName ?? '未绑定'
}

async function loadSettings(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [knowledgeBase, profiles, userDefaults] = await Promise.all([
      getKnowledgeBase(String(route.params.knowledgeBaseKey)),
      listProfiles(),
      // 个人默认偏好是预填增强项；读取失败不能阻断知识库自身设置页。
      getDefaultModels().catch(() => ({ defaults: {} })),
    ])
    if (!knowledgeBase) {
      loadError.value = '知识库不存在或已被删除。'
      return
    }
    settings.value = knowledgeBase
    modelProfiles.value = profiles.filter(item => item.status === 'ACTIVE')
    defaultModels.value = userDefaults
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
    modelBindings: kb.modelBindings.filter(item => item.profileKey),
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
  const defaultLlmKey = defaultModels.value.defaults.LLM ?? ''
  const defaultEmbeddingKey = defaultModels.value.defaults.EMBEDDING ?? ''
  // 显式知识库 binding 优先；空槽只预填当前仍可用且类型匹配的个人默认模型。
  draft.answerProfileKey = answerBindingKey.value
    || (chatModelProfiles.value.some(item => item.profileKey === defaultLlmKey) ? defaultLlmKey : '')
  draft.embeddingProfileKey = embeddingBindingKey.value
    || (embeddingModelProfiles.value.some(item => item.profileKey === defaultEmbeddingKey) ? defaultEmbeddingKey : '')
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
    { usageType: 'ANSWER_GENERATION', profileKey: draft.answerProfileKey },
    { usageType: 'EMBEDDING', profileKey: draft.embeddingProfileKey },
  ].filter(item => item.profileKey)
  const input = buildSaveInput({ modelBindings: bindings })
  if (input) await persist('modelBinding', input)
}

/** 跨页直达：模型绑定为空时引导用户前往模型设置 */
function goToModelSettings(): void {
  void router.push({ name: 'settings-models' })
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
          <a-button v-if="workspaceStore.canManage" data-test="edit-automation" @click="openAutomation">编辑</a-button>
        </header>
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="自动解析">{{ settings?.autoParse ? '已开启' : '已关闭' }}</a-descriptions-item>
          <a-descriptions-item label="自动发布">{{ settings?.autoPublish ? '已开启' : '已关闭' }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="feedback.automation.saved" class="success-message" role="status">{{ feedback.automation.saved }}</p>
      </section>

      <!-- 解析与分块：只读摘要 + 编辑弹窗 -->
      <section class="settings-section" data-test="processing-card">
        <header class="settings-card-head">
          <div>
            <p class="eyebrow">PROCESSING PROFILE</p>
            <h2>解析与分块</h2>
          </div>
          <a-button v-if="workspaceStore.canManage" data-test="edit-processing" @click="openProcessing">编辑</a-button>
        </header>
        <a-descriptions :column="3" size="small" bordered>
          <a-descriptions-item label="解析策略">{{ parserProfileLabel(settings?.parserProfile) }}</a-descriptions-item>
          <a-descriptions-item label="分块大小">{{ settings?.chunkProfile.chunkSize }}</a-descriptions-item>
          <a-descriptions-item label="重叠量">{{ settings?.chunkProfile.overlap }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="feedback.processing.saved" class="success-message" role="status">{{ feedback.processing.saved }}</p>
      </section>

      <!-- 模型绑定：只读摘要 + 编辑弹窗，下拉为空时提供直达模型设置入口 -->
      <section class="settings-section" data-test="model-binding-card">
        <header class="settings-card-head">
          <div>
            <p class="eyebrow">MODEL BINDINGS</p>
            <h2>知识库模型用途</h2>
          </div>
          <a-button v-if="workspaceStore.canManage" data-test="edit-model-binding" @click="openModelBinding">编辑</a-button>
        </header>
        <p v-if="!modelProfiles.length" class="notice-panel">
          <b>未配置模型</b>
          <span>尚未配置可用模型，系统不会自动使用全局默认模型。<a-button type="link" data-test="goto-model-settings" @click="goToModelSettings">前往模型设置</a-button></span>
        </p>
        <a-descriptions v-else :column="2" size="small" bordered>
          <a-descriptions-item label="回答生成">{{ modelNameByKey(answerBindingKey) }}</a-descriptions-item>
          <a-descriptions-item label="向量化">{{ modelNameByKey(embeddingBindingKey) }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="feedback.modelBinding.saved" class="success-message" role="status">{{ feedback.modelBinding.saved }}</p>
      </section>

      <p v-if="!workspaceStore.canManage" class="read-only-note">你当前以知识用户身份查看；服务端也会校验所有修改请求。</p>
    </div>

    <!-- 自动化策略编辑弹窗 -->
    <AppDialog v-model="showAutomationDialog" title="编辑自动化策略" size="md">
      <p class="dialog-description">修改后仅影响后续上传与处理，不会回溯已有文档。</p>
      <a-form layout="vertical" @submit.prevent="saveAutomation">
        <div class="toggle-row">
          <div><strong>自动解析</strong><span>文件上传成功后自动进入解析流程。</span></div>
          <a-switch v-model:checked="draft.autoParse" data-test="auto-parse-input" />
        </div>
        <div class="toggle-row">
          <div><strong>自动发布</strong><span>仅当解析成功时，自动发布为可检索知识。</span></div>
          <a-switch v-model:checked="draft.autoPublish" :disabled="!draft.autoParse" data-test="auto-publish-input" />
        </div>
        <a-alert v-if="feedback.automation.error" type="error" :message="feedback.automation.error" show-icon />
        <footer class="dialog-actions">
          <a-button @click="showAutomationDialog = false">取消</a-button>
          <a-button type="primary" html-type="submit" data-test="save-automation" :loading="feedback.automation.saving">保存</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <!-- 解析与分块编辑弹窗 -->
    <AppDialog v-model="showProcessingDialog" title="编辑解析与分块" size="md">
      <p class="dialog-description">调整解析策略与分块参数，仅作用于之后发起的解析任务。</p>
      <a-form layout="vertical" @submit.prevent="saveProcessing">
        <div class="form-columns">
          <a-form-item label="解析策略">
            <a-select v-model:value="draft.parserProfile" data-test="parser-profile-input">
              <a-select-option value="standard">标准解析</a-select-option>
              <a-select-option value="structure-first">结构优先</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="分块大小">
            <a-input-number v-model:value="chunkSizeModel" :min="100" style="width: 100%" data-test="chunk-size-input" />
          </a-form-item>
          <a-form-item label="重叠量">
            <a-input-number v-model:value="overlapModel" :min="0" style="width: 100%" data-test="overlap-input" />
          </a-form-item>
        </div>
        <a-alert v-if="feedback.processing.error" type="error" :message="feedback.processing.error" show-icon />
        <footer class="dialog-actions">
          <a-button @click="showProcessingDialog = false">取消</a-button>
          <a-button type="primary" html-type="submit" data-test="save-processing" :loading="feedback.processing.saving">保存</a-button>
        </footer>
      </a-form>
    </AppDialog>

    <!-- 模型绑定编辑弹窗 -->
    <AppDialog v-model="showModelBindingDialog" title="编辑知识库模型用途" size="md">
      <p class="dialog-description">未显式绑定时预填个人默认模型；保存后成为知识库独立绑定，不再跟随全局默认。</p>
      <a-form layout="vertical" @submit.prevent="saveModelBinding">
        <p v-if="!modelProfiles.length" class="notice-panel">
          <b>未配置模型</b>
          <span>尚未配置可用模型。<a-button type="link" data-test="goto-model-settings-dialog" @click="goToModelSettings">前往模型设置</a-button></span>
        </p>
        <div v-else class="form-columns">
          <a-form-item label="回答生成">
            <a-select v-model:value="draft.answerProfileKey" data-test="answer-profile-input">
              <a-select-option value="">未绑定</a-select-option>
              <a-select-option v-for="config in chatModelProfiles" :key="config.profileKey" :value="config.profileKey">{{ config.modelName }} · {{ config.lastTestStatus }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="向量化">
            <a-select v-model:value="draft.embeddingProfileKey" data-test="embedding-profile-input">
              <a-select-option value="">未绑定</a-select-option>
              <a-select-option v-for="config in embeddingModelProfiles" :key="config.profileKey" :value="config.profileKey">{{ config.modelName }} · {{ config.lastTestStatus }}</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-alert v-if="feedback.modelBinding.error" type="error" :message="feedback.modelBinding.error" show-icon />
        <footer class="dialog-actions">
          <a-button @click="showModelBindingDialog = false">取消</a-button>
          <a-button type="primary" html-type="submit" data-test="save-model-binding" :loading="feedback.modelBinding.saving">保存</a-button>
        </footer>
      </a-form>
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
