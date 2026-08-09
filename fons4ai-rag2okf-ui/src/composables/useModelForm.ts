/**
 * 模型配置表单 composable（CR-001 T025 还原两步式）。
 *
 * <p>把"连接表单"与"档案表单"的状态构造、校验规则和 API 输入构造集中在此处，
 * 视图只负责调用这些能力并渲染，不内嵌业务规则。
 *
 * <p>设计依据：技术设计说明书 §3.1（CR-001 替换后）。
 * - 连接表单：providerCode/providerName 来自 catalog 预填，apiKey 仅创建时必填
 * - 档案表单：挂在某个连接下，含 7 类型 + 动态高级参数（dimensions 仅 EMBEDDING 等）
 */
import { computed, ref } from 'vue'
import type { ModelConnection, ModelProfile, SaveConnectionInput, SaveProfileInput } from '../api/models'
import { isValidModelType, normalizeModelType, type ModelType } from '../types/model'

// ============ 连接表单 ============

/** 连接表单状态结构。 */
export interface ConnectionFormState {
  /** 提供商编码（从 catalog 预填，如 OPENAI/DEEPSEEK/QWEN/CUSTOM） */
  providerCode: string
  /** 提供商名称 */
  providerName: string
  /** 用户可编辑的显示名称 */
  displayName: string
  /** Base URL */
  baseUrl: string
  /** API Key，仅创建模式填写，编辑模式恒为空 */
  apiKey: string
}

/** 创建一份空的连接表单状态（默认自定义提供商）。 */
export function createEmptyConnectionForm(): ConnectionFormState {
  return {
    providerCode: 'CUSTOM',
    providerName: '',
    displayName: '',
    baseUrl: '',
    apiKey: '',
  }
}

/**
 * 校验连接表单，返回错误消息数组；空数组表示通过。
 * apiKey 仅创建模式必填，编辑模式通过"替换 Key"独立入口修改。
 */
export function validateConnectionForm(form: ConnectionFormState, isCreate: boolean): string[] {
  const errors: string[] = []
  if (!form.providerName.trim()) errors.push('请填写提供商名称')
  if (!form.displayName.trim()) errors.push('请填写显示名称')
  if (!form.baseUrl.trim()) errors.push('请填写 Base URL')
  if (isCreate && !form.apiKey.trim()) errors.push('请填写 API Key')
  return errors
}

/**
 * 构造连接保存输入。
 * 创建模式包含 apiKey；编辑模式不包含 apiKey，由后端保留原密钥。
 */
export function buildSaveConnectionInput(form: ConnectionFormState, isCreate: boolean): SaveConnectionInput {
  const input: SaveConnectionInput = {
    providerCode: form.providerCode,
    providerName: form.providerName,
    displayName: form.displayName,
    baseUrl: form.baseUrl,
  }
  if (isCreate) input.apiKey = form.apiKey
  return input
}

// ============ 档案表单 ============

/** 档案表单状态结构。 */
export interface ProfileFormState {
  /** 所属连接 key（创建档案时必填） */
  connectionKey: string
  /** 模型类型（7 类） */
  modelType: ModelType
  /** 模型名称 */
  modelName: string
  /** 向量维度（仅 EMBEDDING） */
  dimensions: number | null
  /** 上下文窗口长度（LLM/VLM） */
  contextWindowLength: number | null
  /** 超时秒数 */
  timeoutSeconds: number
  /** 温度（LLM/VLM） */
  temperature: number | null
}

/** 创建一份空的档案表单状态（默认 LLM 类型、60 秒超时）。 */
export function createEmptyProfileForm(connectionKey = ''): ProfileFormState {
  return {
    connectionKey,
    modelType: 'LLM',
    modelName: '',
    dimensions: null,
    contextWindowLength: null,
    timeoutSeconds: 60,
    temperature: null,
  }
}

/**
 * 校验档案表单，返回错误消息数组；空数组表示通过。
 * dimensions 仅 EMBEDDING 时由视图收集，填写时需为正整数。
 */
export function validateProfileForm(form: ProfileFormState): string[] {
  const errors: string[] = []
  if (!form.connectionKey) errors.push('请选择所属连接')
  if (!isValidModelType(form.modelType)) errors.push('请选择模型类型')
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

/** 构造档案保存输入。 */
export function buildSaveProfileInput(form: ProfileFormState): SaveProfileInput {
  return {
    connectionKey: form.connectionKey,
    modelType: form.modelType,
    modelName: form.modelName,
    dimensions: form.dimensions,
    contextWindowLength: form.contextWindowLength,
    timeoutSeconds: form.timeoutSeconds,
    temperature: form.temperature,
  }
}

// ============ composable ============

/**
 * 模型表单 composable：封装连接与档案两套表单状态、模式切换、校验与输入构造。
 * 供 ModelSettingsTab 在新增/编辑连接、新增/编辑档案中复用。
 */
export function useModelForm() {
  /** 当前连接表单状态 */
  const connectionForm = ref<ConnectionFormState>(createEmptyConnectionForm())
  /** 是否为连接新增模式（true 新增，false 编辑） */
  const isConnectionCreate = ref(true)

  /** 当前档案表单状态 */
  const profileForm = ref<ProfileFormState>(createEmptyProfileForm())
  /** 是否为档案新增模式（true 新增，false 编辑） */
  const isProfileCreate = ref(true)

  /** 重置连接表单为空（新增模式默认状态） */
  function resetConnectionForm(): void {
    connectionForm.value = createEmptyConnectionForm()
  }

  /** 重置档案表单为空 */
  function resetProfileForm(): void {
    profileForm.value = createEmptyProfileForm()
  }

  /** 进入连接新增模式：清空表单并标记为新增 */
  function prepareCreateConnection(): void {
    resetConnectionForm()
    isConnectionCreate.value = true
  }

  /** 进入连接编辑模式：从已有连接加载表单，apiKey 留空 */
  function prepareEditConnection(conn: ModelConnection): void {
    connectionForm.value = {
      providerCode: conn.providerCode,
      providerName: conn.providerName,
      displayName: conn.displayName,
      baseUrl: conn.baseUrl,
      apiKey: '',
    }
    isConnectionCreate.value = false
  }

  /** 进入档案新增模式：绑定到指定连接 */
  function prepareCreateProfile(connectionKey: string): void {
    profileForm.value = createEmptyProfileForm(connectionKey)
    isProfileCreate.value = true
  }

  /** 进入档案编辑模式：从已有档案加载表单，modelType 兼容 CHAT→LLM */
  function prepareEditProfile(profile: ModelProfile): void {
    profileForm.value = {
      connectionKey: profile.connectionKey,
      modelType: normalizeModelType(profile.modelType),
      modelName: profile.modelName,
      dimensions: profile.dimensions,
      contextWindowLength: profile.contextWindowLength,
      timeoutSeconds: profile.timeoutSeconds,
      temperature: profile.temperature,
    }
    isProfileCreate.value = false
  }

  /** 清理连接表单中的 apiKey 等敏感字段，防止取消或卸载后残留 */
  function clearConnectionApiKey(): void {
    connectionForm.value.apiKey = ''
  }

  /** 连接对话框标题，随模式切换 */
  const connectionDialogTitle = computed(() => (isConnectionCreate.value ? '添加连接' : '编辑连接'))
  /** 档案对话框标题，随模式切换 */
  const profileDialogTitle = computed(() => (isProfileCreate.value ? '添加模型档案' : '编辑模型档案'))

  /** 校验当前连接表单，返回错误消息数组 */
  function validateConnection(): string[] {
    return validateConnectionForm(connectionForm.value, isConnectionCreate.value)
  }

  /** 校验当前档案表单，返回错误消息数组 */
  function validateProfile(): string[] {
    return validateProfileForm(profileForm.value)
  }

  /** 构造当前连接表单对应的保存输入（自动根据模式决定是否包含 apiKey） */
  function buildConnectionInput(): SaveConnectionInput {
    return buildSaveConnectionInput(connectionForm.value, isConnectionCreate.value)
  }

  /** 构造当前档案表单对应的保存输入 */
  function buildProfileInput(): SaveProfileInput {
    return buildSaveProfileInput(profileForm.value)
  }

  return {
    connectionForm,
    isConnectionCreate,
    profileForm,
    isProfileCreate,
    connectionDialogTitle,
    profileDialogTitle,
    resetConnectionForm,
    resetProfileForm,
    prepareCreateConnection,
    prepareEditConnection,
    prepareCreateProfile,
    prepareEditProfile,
    clearConnectionApiKey,
    validateConnection,
    validateProfile,
    buildConnectionInput,
    buildProfileInput,
  }
}
