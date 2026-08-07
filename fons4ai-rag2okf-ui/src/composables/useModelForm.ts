/**
 * 模型配置表单 composable（DDD-lite）。
 *
 * <p>把表单状态构造、模板预填、校验规则和 API 输入构造集中在此处，
 * 视图只负责调用这些能力并渲染，不内嵌业务规则。
 *
 * <p>设计依据：T011 合并表单需求，AC-008/AC-009/AC-014。
 * - 新增模式：apiKey 必填，提交时包含在输入中
 * - 编辑模式：不回填 apiKey，提交时不包含 apiKey，保留原密钥
 */
import { computed, ref } from 'vue'
import type { ModelConfig, ModelProviderTemplateInfo, SaveModelConfigInput } from '../api/models'

/** 模型表单状态结构，覆盖基础信息与高级参数。 */
export interface ModelFormState {
  /** 模板编码，仅用于新增时回显选中模板 */
  templateCode: string
  /** 厂商编码，提交时作为 providerCode */
  providerCode: string
  /** 厂商名称 */
  providerName: string
  /** 显示名称 */
  displayName: string
  /** Base URL */
  baseUrl: string
  /** API Key，仅新增模式填写，编辑模式恒为空 */
  apiKey: string
  /** 模型类型：文本模型或向量化模型 */
  modelType: 'CHAT' | 'EMBEDDING'
  /** 模型名称 */
  modelName: string
  /** 向量维度，仅 EMBEDDING 时有意义 */
  dimensions: number | null
  /** 上下文窗口长度 */
  contextWindowLength: number | null
  /** 超时秒数 */
  timeoutSeconds: number
  /** 温度 */
  temperature: number | null
}

/** 创建一份空的模型表单状态（默认自定义模板、CHAT 类型、60 秒超时）。 */
export function createEmptyModelForm(): ModelFormState {
  return {
    templateCode: 'CUSTOM',
    providerCode: 'CUSTOM',
    providerName: '',
    displayName: '',
    baseUrl: '',
    apiKey: '',
    modelType: 'CHAT',
    modelName: '',
    dimensions: null,
    contextWindowLength: null,
    timeoutSeconds: 60,
    temperature: null,
  }
}

/** 从已有配置加载到表单状态（编辑模式入口），apiKey 不回填以避免泄露原值。 */
export function modelFormFromConfig(config: ModelConfig): ModelFormState {
  return {
    templateCode: config.providerCode,
    providerCode: config.providerCode,
    providerName: config.providerName,
    displayName: config.displayName,
    baseUrl: config.baseUrl,
    apiKey: '',
    modelType: config.modelType,
    modelName: config.modelName,
    dimensions: config.dimensions,
    contextWindowLength: config.contextWindowLength,
    timeoutSeconds: config.timeoutSeconds,
    temperature: config.temperature,
  }
}

/** 应用厂商模板：预填 providerCode/providerName/baseUrl，自定义模板时 baseUrl 留空。 */
export function applyTemplateToForm(form: ModelFormState, template: ModelProviderTemplateInfo | undefined): void {
  if (!template) return
  form.templateCode = template.code
  form.providerCode = template.code
  form.providerName = template.providerName
  form.baseUrl = template.defaultBaseUrl ?? ''
}

/**
 * 校验模型表单，返回错误消息数组；空数组表示通过。
 * 校验规则集中在此处，视图不内嵌业务规则。
 */
export function validateModelForm(form: ModelFormState, isCreate: boolean): string[] {
  const errors: string[] = []
  if (!form.providerName.trim()) errors.push('请填写厂商名称')
  if (!form.displayName.trim()) errors.push('请填写显示名称')
  if (!form.baseUrl.trim()) errors.push('请填写 Base URL')
  // API Key 仅新增时必填，编辑模式通过"替换 Key"独立入口修改
  if (isCreate && !form.apiKey.trim()) errors.push('请填写 API Key')
  if (!form.modelName.trim()) errors.push('请填写模型名称')
  if (form.timeoutSeconds < 1 || form.timeoutSeconds > 120) errors.push('超时秒数需在 1 到 120 之间')
  if (form.temperature != null && (form.temperature < 0 || form.temperature > 2)) {
    errors.push('温度需在 0 到 2 之间')
  }
  if (form.dimensions != null && (!Number.isInteger(form.dimensions) || form.dimensions < 1)) {
    errors.push('向量维度需为正整数')
  }
  if (form.contextWindowLength != null && (!Number.isInteger(form.contextWindowLength) || form.contextWindowLength < 1)) {
    errors.push('上下文窗口长度需为正整数')
  }
  return errors
}

/**
 * 构造保存输入。
 * 新增模式包含 apiKey；编辑模式不包含 apiKey，由后端保留原密钥。
 */
export function buildSaveModelInput(form: ModelFormState, isCreate: boolean): SaveModelConfigInput {
  const input: SaveModelConfigInput = {
    providerCode: form.providerCode,
    providerName: form.providerName,
    displayName: form.displayName,
    baseUrl: form.baseUrl,
    modelType: form.modelType,
    modelName: form.modelName,
    dimensions: form.dimensions,
    contextWindowLength: form.contextWindowLength,
    timeoutSeconds: form.timeoutSeconds,
    temperature: form.temperature,
  }
  if (isCreate) input.apiKey = form.apiKey
  return input
}

/**
 * 模型表单 composable：封装表单状态、模式切换、模板预填、校验与输入构造。
 * 供 ModelSettingsView 在新增/编辑合并表单中复用。
 */
export function useModelForm() {
  /** 当前表单状态 */
  const form = ref<ModelFormState>(createEmptyModelForm())
  /** 是否为新增模式（true 新增，false 编辑） */
  const isCreate = ref(true)
  /** 高级参数折叠区域是否展开 */
  const advancedOpen = ref(false)

  /** 重置为空表单（新增模式默认状态） */
  function resetForm(): void {
    form.value = createEmptyModelForm()
  }

  /** 进入新增模式：清空表单并标记为新增 */
  function prepareCreate(): void {
    resetForm()
    isCreate.value = true
    advancedOpen.value = false
  }

  /** 进入编辑模式：从已有配置加载表单，apiKey 留空 */
  function prepareEdit(config: ModelConfig): void {
    form.value = modelFormFromConfig(config)
    isCreate.value = false
    advancedOpen.value = false
  }

  /** 根据模板编码应用模板预填 */
  function selectTemplate(template: ModelProviderTemplateInfo | undefined): void {
    applyTemplateToForm(form.value, template)
  }

  /** 构造当前表单对应的保存输入（自动根据模式决定是否包含 apiKey） */
  function buildInput(): SaveModelConfigInput {
    return buildSaveModelInput(form.value, isCreate.value)
  }

  /** 校验当前表单，返回错误消息数组 */
  function validate(): string[] {
    return validateModelForm(form.value, isCreate.value)
  }

  /** 对话框标题，随模式切换 */
  const dialogTitle = computed(() => (isCreate.value ? '添加模型配置' : '编辑模型配置'))
  /** 提交按钮文案，随模式切换 */
  const submitLabel = computed(() => (isCreate.value ? '保存配置' : '保存'))

  return {
    form,
    isCreate,
    advancedOpen,
    dialogTitle,
    submitLabel,
    resetForm,
    prepareCreate,
    prepareEdit,
    selectTemplate,
    buildInput,
    validate,
  }
}
