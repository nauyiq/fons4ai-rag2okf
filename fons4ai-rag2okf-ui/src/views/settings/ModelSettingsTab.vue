<script setup lang="ts">
/**
 * 模型设置 Tab（设置中心子路由）—— CR-001 T028 Ragflow 风格左右双栏。
 *
 * <p>左侧 ~2/3：顶部默认模型配置（7 类型下拉）+ 下方按连接分组的提供商卡片。
 * <p>右侧 ~1/3：可选模型（搜索框 + 类型标签 + 提供商卡片网格 + 自定义入口）。
 *
 * <p>两级弹窗：
 * - 连接弹窗：displayName / baseUrl / apiKey（providerCode/providerName 由目录预填，隐藏）
 * - 档案弹窗：级联 connectionKey → modelType → modelName + 按类型动态高级参数
 *
 * <p>API Key 安全：创建/替换时提交，弹窗关闭/取消/卸载时清理敏感字段。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined } from '@ant-design/icons-vue'

import { ApiRequestError } from '../../api/http'
import {
  createConnection,
  createProfile,
  deleteConnection,
  deleteProfile,
  listConnections,
  listProfiles,
  replaceConnectionApiKey,
  testProfile,
  updateConnection,
  updateProfile,
  type CatalogProvider,
  type ModelConnection,
  type ModelProfile,
} from '../../api/models'
import { useModelForm } from '../../composables/useModelForm'
import { useModelCatalog } from '../../composables/useModelCatalog'
import { useDefaultModels } from '../../composables/useDefaultModels'
import { modelTestLabel } from '../../utils/formatters'
import { MODEL_TYPES, MODEL_TYPE_LABELS, type ModelType } from '../../types/model'
import AppDialog from '../../components/ui/AppDialog.vue'

/** 与 ant-design-vue a-select 的 SelectValue 结构对齐的本地类型（包根未导出）。 */
type RawValue = string | number
interface LabeledValue {
  key?: string
  value: RawValue
  label?: unknown
}
type SelectValue = RawValue | RawValue[] | LabeledValue | LabeledValue[] | undefined

/** 模型类型标签语义色（跨左右两栏一致）。 */
const MODEL_TYPE_COLORS: Record<ModelType, string> = {
  LLM: 'blue',
  EMBEDDING: 'green',
  RERANK: 'orange',
  TTS: 'purple',
  ASR: 'cyan',
  VLM: 'magenta',
  OCR: 'gold',
}

const connections = ref<ModelConnection[]>([])
const profiles = ref<ModelProfile[]>([])

const loading = ref(true)
const saving = ref(false)
const savingDefaults = ref(false)
const testingKey = ref('')

// 弹窗状态
const showConnectionDialog = ref(false)
const showProfileDialog = ref(false)
const showAddModelConfirm = ref(false)
/** 创建连接后待确认是否立即添加模型的连接。 */
const pendingConnection = ref<ModelConnection | null>(null)
/** 编辑连接/档案时记录的 key。 */
const editingConnectionKey = ref('')
const editingProfileKey = ref('')
/** 表单校验错误列表 */
const connectionErrors = ref<string[]>([])
const profileErrors = ref<string[]>([])

const {
  connectionForm,
  isConnectionCreate,
  profileForm,
  isProfileCreate,
  connectionDialogTitle,
  profileDialogTitle,
  prepareCreateConnection,
  prepareEditConnection,
  prepareCreateProfile,
  prepareEditProfile,
  clearConnectionApiKey,
  validateConnection,
  validateProfile,
  buildConnectionInput,
  buildProfileInput,
} = useModelForm()

const {
  catalog,
  filteredProviders,
  searchKeyword,
  activeTypeFilter,
  providerModels,
  fetchCatalog,
} = useModelCatalog()

const { defaults, load: loadDefaults, save: saveDefaultsRaw } = useDefaultModels()

/** 连接 + 其下属档案的分组，供左侧卡片渲染。 */
const connectionGroups = computed(() =>
  connections.value.map((conn) => ({
    connection: conn,
    profiles: profiles.value.filter((p) => p.connectionKey === conn.connectionKey),
  })),
)

/** 折叠态下每个连接默认展示的档案数量（超出则折叠，点击展开按钮显示全部）。 */
const COLLAPSED_PROFILE_LIMIT = 2

/** 记录每个连接是否处于展开态（key = connectionKey）。 */
const expandedConnections = ref<Set<string>>(new Set())

/** 某连接是否处于展开态。 */
function isConnectionExpanded(connectionKey: string): boolean {
  return expandedConnections.value.has(connectionKey)
}

/** 切换某连接的展开/折叠态。 */
function toggleConnectionExpanded(connectionKey: string): void {
  const next = new Set(expandedConnections.value)
  if (next.has(connectionKey)) {
    next.delete(connectionKey)
  } else {
    next.add(connectionKey)
  }
  expandedConnections.value = next
}

/** 返回某连接当前应渲染的档案列表（折叠态裁剪到阈值）。 */
function visibleProfiles(group: { connection: ModelConnection; profiles: ModelProfile[] }): ModelProfile[] {
  if (isConnectionExpanded(group.connection.connectionKey)) return group.profiles
  return group.profiles.slice(0, COLLAPSED_PROFILE_LIMIT)
}

/** 可选模型网格：排除 CUSTOM（自定义入口单独渲染在底部）。 */
const marketProviders = computed<CatalogProvider[]>(() =>
  filteredProviders.value.filter((p) => p.providerCode !== 'CUSTOM'),
)

/** 按类型分组的档案，供默认偏好下拉使用。 */
function profilesByType(type: ModelType): ModelProfile[] {
  return profiles.value.filter((p) => p.modelType === type)
}

/** 档案下拉选项标签：模型名称（连接显示名）。 */
function profileOptionLabel(p: ModelProfile): string {
  const conn = connections.value.find((c) => c.connectionKey === p.connectionKey)
  return conn ? `${p.modelName}（${conn.displayName}）` : p.modelName
}

/** 档案表单：模型类型相关的字段可见性。 */
const showDimensions = computed(() => profileForm.value.modelType === 'EMBEDDING')
const showContextWindow = computed(
  () => profileForm.value.modelType === 'LLM' || profileForm.value.modelType === 'VLM',
)
const showTemperature = computed(
  () => profileForm.value.modelType === 'LLM' || profileForm.value.modelType === 'VLM',
)

/** 默认模型偏好下拉取值。 */
function defaultModelValue(type: ModelType): string | undefined {
  return defaults.value.defaults[type] ?? undefined
}
/** 默认模型偏好下拉赋值。 */
function onDefaultChange(type: ModelType, value: SelectValue): void {
  defaults.value.defaults[type] = typeof value === 'string' && value ? value : null
}

/** a-select 桥接：档案所属连接选择，切换时重置模型名称。 */
const profileConnectionModel = computed<SelectValue>({
  get: () => profileForm.value.connectionKey,
  set: (v) => {
    profileForm.value.connectionKey = typeof v === 'string' ? v : ''
    profileForm.value.modelName = ''
  },
})

/** a-select 桥接：档案模型类型选择，切换时重置模型名称。 */
const profileTypeModel = computed<SelectValue>({
  get: () => profileForm.value.modelType,
  set: (v) => {
    const t = typeof v === 'number' ? String(v) : typeof v === 'string' ? v : ''
    if (MODEL_TYPES.includes(t as ModelType)) {
      profileForm.value.modelType = t as ModelType
      profileForm.value.modelName = ''
    }
  },
})

/** 当前选中连接对应的目录提供商（无则视为自定义）。 */
const selectedCatalogProvider = computed<CatalogProvider | null>(() => {
  const conn = connections.value.find((c) => c.connectionKey === profileForm.value.connectionKey)
  if (!conn) return null
  return catalog.value.find((p) => p.providerCode === conn.providerCode) ?? null
})

/** 自定义提供商（CUSTOM 或目录中无该提供商）时模型名称走自由输入。 */
const isCustomModelName = computed(() => {
  const conn = connections.value.find((c) => c.connectionKey === profileForm.value.connectionKey)
  if (!conn) return true
  return !selectedCatalogProvider.value || conn.providerCode === 'CUSTOM'
})

/** 模型名称下拉选项：按目录提供商 + 当前类型过滤。 */
const modelNameOptions = computed(() => {
  const provider = selectedCatalogProvider.value
  if (!provider) return []
  return provider.models.filter((m) => m.modelType === profileForm.value.modelType)
})

/** a-input-number 桥接：number | null 字段与组件 string | number 双向转换。 */
function toNullableNumber(v: string | number): number | null {
  if (typeof v === 'number') return v
  if (v.trim() === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}
function toNonNullNumber(v: string | number, fallback: number): number {
  if (typeof v === 'number') return v
  if (v.trim() === '') return fallback
  const n = Number(v)
  return Number.isNaN(n) ? fallback : n
}

const dimensionsModel = computed<string | number>({
  get: () => profileForm.value.dimensions ?? '',
  set: (v) => {
    profileForm.value.dimensions = toNullableNumber(v)
  },
})
const contextWindowModel = computed<string | number>({
  get: () => profileForm.value.contextWindowLength ?? '',
  set: (v) => {
    profileForm.value.contextWindowLength = toNullableNumber(v)
  },
})
const timeoutModel = computed<string | number>({
  get: () => profileForm.value.timeoutSeconds,
  set: (v) => {
    profileForm.value.timeoutSeconds = toNonNullNumber(v, 60)
  },
})
const temperatureModel = computed<string | number>({
  get: () => profileForm.value.temperature ?? '',
  set: (v) => {
    profileForm.value.temperature = toNullableNumber(v)
  },
})

/** API Key 表单帮助文本：创建模式必填，编辑模式可选（留空保持不变）。 */
const connectionApiKeyHelp = computed(() => {
  if (isConnectionCreate.value) {
    return connectionErrors.value.find((e) => e.includes('API Key')) || ''
  }
  return '留空保持原密钥不变；填写则替换为新密钥'
})

function testCellClass(status: string): string {
  if (!status) return 'cell-muted'
  return status === 'SUCCESS' ? 'cell-success' : 'cell-error'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [connList, profileList] = await Promise.all([listConnections(), listProfiles()])
    connections.value = connList
    profiles.value = profileList
    await Promise.all([fetchCatalog(), loadDefaults()])
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法读取模型设置。')
  } finally {
    loading.value = false
  }
}

// ============ 连接弹窗 ============

/** 从目录卡片打开连接新增弹窗（预填 providerCode/providerName/baseUrl）。 */
function openConnectionFromCatalog(provider: CatalogProvider): void {
  prepareCreateConnection()
  connectionForm.value.providerCode = provider.providerCode
  connectionForm.value.providerName = provider.providerName
  connectionForm.value.baseUrl = provider.defaultBaseUrl
  editingConnectionKey.value = ''
  connectionErrors.value = []
  showConnectionDialog.value = true
}

/** 打开自定义提供商连接弹窗（空字段）。 */
function openCustomConnection(): void {
  prepareCreateConnection()
  editingConnectionKey.value = ''
  connectionErrors.value = []
  showConnectionDialog.value = true
}

/** 打开连接编辑弹窗。 */
function openEditConnection(conn: ModelConnection): void {
  prepareEditConnection(conn)
  editingConnectionKey.value = conn.connectionKey
  connectionErrors.value = []
  showConnectionDialog.value = true
}

async function submitConnection(): Promise<void> {
  // CUSTOM 提供商无固定名称，用 displayName 兜底 providerName 以通过校验
  if (connectionForm.value.providerCode === 'CUSTOM' && !connectionForm.value.providerName.trim()) {
    connectionForm.value.providerName = connectionForm.value.displayName
  }
  const errors = validateConnection()
  if (errors.length) {
    connectionErrors.value = errors
    return
  }
  connectionErrors.value = []
  saving.value = true
  try {
    if (isConnectionCreate.value) {
      const created = await createConnection(buildConnectionInput())
      connections.value = [...connections.value, created]
      showConnectionDialog.value = false
      clearConnectionApiKey()
      message.success('连接已创建')
      pendingConnection.value = created
      showAddModelConfirm.value = true
    } else {
      const key = editingConnectionKey.value
      const updated = await updateConnection(key, buildConnectionInput())
      if (updated) {
        connections.value = connections.value.map((c) => (c.connectionKey === key ? updated : c))
      }
      // 编辑模式：若填写了新 API Key，则调用替换接口更新密钥
      const newApiKey = connectionForm.value.apiKey.trim()
      if (newApiKey) {
        const keyResult = await replaceConnectionApiKey(key, newApiKey)
        if (keyResult) {
          connections.value = connections.value.map((c) =>
            c.connectionKey === key ? { ...c, apiKeyMask: keyResult.apiKeyMask } : c,
          )
        }
      }
      showConnectionDialog.value = false
      message.success('连接已更新')
    }
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法保存连接。')
  } finally {
    saving.value = false
    if (!isConnectionCreate.value) clearConnectionApiKey()
  }
}

function cancelConnection(): void {
  showConnectionDialog.value = false
  connectionErrors.value = []
  clearConnectionApiKey()
}

/** 创建连接后确认立即添加模型。 */
function confirmAddModel(): void {
  const conn = pendingConnection.value
  showAddModelConfirm.value = false
  pendingConnection.value = null
  if (conn) openCreateProfile(conn)
}

function cancelAddModel(): void {
  showAddModelConfirm.value = false
  pendingConnection.value = null
}

// ============ 档案弹窗 ============

function openCreateProfile(conn?: ModelConnection): void {
  prepareCreateProfile(conn?.connectionKey ?? '')
  editingProfileKey.value = ''
  profileErrors.value = []
  showProfileDialog.value = true
}

function openEditProfile(profile: ModelProfile): void {
  prepareEditProfile(profile)
  editingProfileKey.value = profile.profileKey
  profileErrors.value = []
  showProfileDialog.value = true
}

async function submitProfile(): Promise<void> {
  const errors = validateProfile()
  if (errors.length) {
    profileErrors.value = errors
    return
  }
  profileErrors.value = []
  saving.value = true
  try {
    if (isProfileCreate.value) {
      const created = await createProfile(buildProfileInput())
      profiles.value = [...profiles.value, created]
      message.success('模型档案已创建')
      showProfileDialog.value = false
    } else {
      const key = editingProfileKey.value
      const updated = await updateProfile(key, buildProfileInput())
      if (updated) {
        profiles.value = profiles.value.map((p) => (p.profileKey === key ? updated : p))
      }
      message.success('模型档案已更新')
      showProfileDialog.value = false
    }
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法保存模型档案。')
  } finally {
    saving.value = false
  }
}

function cancelProfile(): void {
  showProfileDialog.value = false
  profileErrors.value = []
}

async function runTest(profile: ModelProfile): Promise<void> {
  testingKey.value = profile.profileKey
  try {
    const result = await testProfile(profile.profileKey)
    const target = profiles.value.find((p) => p.profileKey === profile.profileKey)
    if (target) {
      target.lastTestStatus = result.status
      target.lastTestAt = new Date().toISOString()
    }
    const label = modelTestLabel(result.status, result.errorCode)
    if (result.status === 'SUCCESS') message.success(label)
    else message.warning(label)
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '模型测试未完成。')
  } finally {
    testingKey.value = ''
  }
}

async function confirmDeleteProfile(profile: ModelProfile): Promise<void> {
  try {
    await deleteProfile(profile.profileKey)
    profiles.value = profiles.value.filter((p) => p.profileKey !== profile.profileKey)
    message.success('档案已删除')
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法删除档案。')
  }
}

async function confirmDeleteConnection(conn: ModelConnection): Promise<void> {
  try {
    await deleteConnection(conn.connectionKey)
    connections.value = connections.value.filter((c) => c.connectionKey !== conn.connectionKey)
    profiles.value = profiles.value.filter((p) => p.connectionKey !== conn.connectionKey)
    message.success('连接已删除')
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法删除连接。')
  }
}

// ============ 默认模型偏好 ============

async function saveDefaults(): Promise<void> {
  savingDefaults.value = true
  try {
    await saveDefaultsRaw()
    message.success('默认模型偏好已保存')
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法保存默认模型偏好。')
  } finally {
    savingDefaults.value = false
  }
}

onBeforeUnmount(() => {
  clearConnectionApiKey()
  showConnectionDialog.value = false
  showProfileDialog.value = false
  showAddModelConfirm.value = false
})
onMounted(load)
</script>

<template>
  <section class="model-settings-tab" data-test="model-settings-tab">
    <header class="page-heading">
      <div>
        <p class="eyebrow">MODEL CONFIGURATION</p>
        <h1>模型设置</h1>
        <p>管理模型连接与档案；密钥保存后仅显示掩码，不回显原值。</p>
      </div>
    </header>

    <a-spin :spinning="loading">
      <div class="dual-pane">
        <!-- ============ 左侧：默认配置 + 连接卡片 ============ -->
        <div class="left-pane" data-test="left-pane">
          <!-- 默认模型配置 -->
          <div class="settings-section defaults-section" data-test="defaults-section">
            <h2 class="section-title">默认模型配置</h2>
            <p class="section-hint">选择各类型默认使用的模型档案，保存后对后续调用生效。</p>
            <div class="defaults-grid">
              <div v-for="t in MODEL_TYPES" :key="t" class="defaults-item">
                <label>{{ MODEL_TYPE_LABELS[t] }}</label>
                <a-select
                  :value="defaultModelValue(t)"
                  allow-clear
                  :placeholder="`选择默认 ${MODEL_TYPE_LABELS[t]} 档案`"
                  style="width: 100%"
                  :data-test="`default-model-${t}`"
                  @change="(v: SelectValue) => onDefaultChange(t, v)"
                >
                  <a-select-option
                    v-for="p in profilesByType(t)"
                    :key="p.profileKey"
                    :value="p.profileKey"
                  >
                    {{ profileOptionLabel(p) }}
                  </a-select-option>
                </a-select>
              </div>
            </div>
            <div class="form-footer">
              <a-button type="primary" :loading="savingDefaults" data-test="save-defaults" @click="saveDefaults">
                保存默认配置
              </a-button>
            </div>
          </div>

          <!-- 按连接分组的提供商卡片 -->
          <div class="settings-section connections-section">
            <h2 class="section-title">模型连接</h2>
            <a-empty
              v-if="!connectionGroups.length"
              description="尚无模型连接，从右侧可选模型添加提供商"
            />
            <div
              v-for="group in connectionGroups"
              :key="group.connection.connectionKey"
              class="connection-card"
              :data-test="`connection-card-${group.connection.connectionKey}`"
            >
              <div class="connection-card-head">
                <div class="connection-card-title">
                  <span class="provider-name">{{ group.connection.providerName }}</span>
                  <span class="display-name">{{ group.connection.displayName }}</span>
                  <span class="api-key-mask">{{ group.connection.apiKeyMask ? group.connection.apiKeyMask : '未配置 Key' }}</span>
                  <a-tag :color="group.connection.status === 'ACTIVE' ? 'green' : 'default'">
                    {{ group.connection.status === 'ACTIVE' ? '启用' : '禁用' }}
                  </a-tag>
                </div>
                <a-space wrap>
                  <a-button
                    size="small"
                    :data-test="`edit-connection-${group.connection.connectionKey}`"
                    @click="openEditConnection(group.connection)"
                  >
                    编辑
                  </a-button>
                  <a-popconfirm
                    title="确认删除该连接及其所有档案？"
                    @confirm="confirmDeleteConnection(group.connection)"
                  >
                    <a-button
                      size="small"
                      danger
                      type="text"
                      class="icon-action-btn"
                      :data-test="`delete-connection-${group.connection.connectionKey}`"
                      aria-label="删除连接"
                    >
                      <DeleteOutlined />
                    </a-button>
                  </a-popconfirm>
                </a-space>
              </div>

              <div class="connection-card-body">
                <a-empty v-if="!group.profiles.length" description="该连接下暂无模型档案" />
                <div v-else class="profile-list">
                  <div
                    v-for="p in visibleProfiles(group)"
                    :key="p.profileKey"
                    class="profile-row"
                    :data-test="`profile-row-${p.profileKey}`"
                  >
                    <div class="profile-row-main">
                      <span class="profile-model-name">{{ p.modelName }}</span>
                      <a-tag :color="MODEL_TYPE_COLORS[p.modelType]">
                        {{ MODEL_TYPE_LABELS[p.modelType] }}
                      </a-tag>
                      <span :class="['profile-test-status', testCellClass(p.lastTestStatus)]">
                        {{ p.lastTestStatus ? modelTestLabel(p.lastTestStatus) : '未测试' }}
                      </span>
                    </div>
                    <a-space>
                      <a-button
                        size="small"
                        :loading="testingKey === p.profileKey"
                        :data-test="`test-profile-${p.profileKey}`"
                        @click="runTest(p)"
                      >
                        测试
                      </a-button>
                      <a-button
                        size="small"
                        :data-test="`edit-profile-${p.profileKey}`"
                        @click="openEditProfile(p)"
                      >
                        编辑
                      </a-button>
                      <a-popconfirm title="确认删除该档案？" @confirm="confirmDeleteProfile(p)">
                        <a-button
                          size="small"
                          danger
                          type="text"
                          class="icon-action-btn"
                          :data-test="`delete-profile-${p.profileKey}`"
                          aria-label="删除档案"
                        >
                          <DeleteOutlined />
                        </a-button>
                      </a-popconfirm>
                    </a-space>
                  </div>
                </div>
                <div class="connection-card-actions">
                  <a-button
                    v-if="group.profiles.length > COLLAPSED_PROFILE_LIMIT"
                    size="small"
                    type="link"
                    :data-test="`toggle-profiles-${group.connection.connectionKey}`"
                    @click="toggleConnectionExpanded(group.connection.connectionKey)"
                  >
                    {{ isConnectionExpanded(group.connection.connectionKey) ? '隐藏模型' : `展示更多模型（${group.profiles.length}）` }}
                  </a-button>
                  <a-button
                    size="small"
                    type="primary"
                    :data-test="`add-profile-${group.connection.connectionKey}`"
                    @click="openCreateProfile(group.connection)"
                  >
                    ＋ 添加模型
                  </a-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ============ 右侧：可选模型 ============ -->
        <div class="right-pane" data-test="right-pane">
          <div class="settings-section market-section">
            <h2 class="section-title">可选模型</h2>
            <a-input-search
              v-model:value="searchKeyword"
              placeholder="搜索提供商或模型名称"
              allow-clear
              class="market-search"
              data-test="market-search"
            />
            <div class="type-tag-bar">
              <a-tag
                class="type-tag"
                :class="{ active: activeTypeFilter === '' }"
                data-test="type-tag-all"
                @click="activeTypeFilter = ''"
              >
                全部
              </a-tag>
              <a-tag
                v-for="t in MODEL_TYPES"
                :key="t"
                class="type-tag"
                :class="{ active: activeTypeFilter === t }"
                :color="activeTypeFilter === t ? MODEL_TYPE_COLORS[t] : ''"
                :data-test="`type-tag-${t}`"
                @click="activeTypeFilter = activeTypeFilter === t ? '' : t"
              >
                {{ MODEL_TYPE_LABELS[t] }}
              </a-tag>
            </div>

            <div class="market-grid">
              <div
                v-for="provider in marketProviders"
                :key="provider.providerCode"
                class="catalog-card"
                :data-test="`catalog-card-${provider.providerCode}`"
                @click="openConnectionFromCatalog(provider)"
              >
                <div class="catalog-card-head">
                  <span class="catalog-provider-name">{{ provider.providerName }}</span>
                  <a
                    v-if="provider.officialUrl"
                    class="official-link"
                    :href="provider.officialUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    :data-test="`official-link-${provider.providerCode}`"
                    @click.stop
                  >↗</a>
                </div>
                <div class="catalog-models">
                  <a-tag
                    v-for="m in providerModels(provider)"
                    :key="m.modelName"
                    :color="MODEL_TYPE_COLORS[m.modelType]"
                    class="catalog-model-tag"
                  >
                    {{ m.modelName }}
                  </a-tag>
                  <span v-if="!providerModels(provider).length" class="cell-muted">无匹配模型</span>
                </div>
                <div class="catalog-card-foot">
                  <a-button
                    type="primary"
                    size="small"
                    :data-test="`add-connection-${provider.providerCode}`"
                    @click.stop="openConnectionFromCatalog(provider)"
                  >
                    添加
                  </a-button>
                </div>
              </div>
            </div>

            <!-- 自定义提供商入口 -->
            <div
              class="catalog-card custom-card"
              data-test="custom-provider-card"
              @click="openCustomConnection"
            >
              <div class="catalog-card-head">
                <span class="catalog-provider-name">自定义提供商</span>
              </div>
              <p class="custom-hint">通过 Base URL 与 API Key 接入任意 OpenAI 兼容服务。</p>
              <div class="catalog-card-foot">
                <a-button type="primary" size="small" data-test="add-custom-connection" @click.stop="openCustomConnection">
                  添加
                </a-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <!-- ============ 连接弹窗 ============ -->
    <AppDialog
      v-model="showConnectionDialog"
      :title="connectionDialogTitle"
      size="md"
      :persistent="true"
      @cancel="cancelConnection"
    >
      <a-form layout="vertical" @submit.prevent="submitConnection">
        <a-form-item
          label="显示名称"
          :required="true"
          :validate-status="connectionErrors.some((e) => e.includes('显示名称')) ? 'error' : ''"
          :help="connectionErrors.find((e) => e.includes('显示名称')) || ''"
        >
          <a-input
            v-model:value="connectionForm.displayName"
            placeholder="例如：通义千问连接"
            data-test="connection-display-name"
          />
        </a-form-item>

        <a-form-item
          label="Base URL"
          :required="true"
          :validate-status="connectionErrors.some((e) => e.includes('Base URL')) ? 'error' : ''"
          :help="connectionErrors.find((e) => e.includes('Base URL')) || ''"
        >
          <a-input
            v-model:value="connectionForm.baseUrl"
            placeholder="https://…"
            data-test="connection-base-url"
          />
        </a-form-item>

        <a-form-item
          label="API Key"
          :required="isConnectionCreate"
          :validate-status="connectionErrors.some((e) => e.includes('API Key')) ? 'error' : ''"
          :help="connectionApiKeyHelp"
        >
          <a-input-password
            v-model:value="connectionForm.apiKey"
            autocomplete="off"
            :placeholder="isConnectionCreate ? 'sk-…' : '留空保持原密钥不变，填写则替换为新密钥'"
            data-test="connection-api-key"
          />
        </a-form-item>

        <a-alert
          v-if="connectionErrors.length"
          class="form-errors"
          type="error"
          show-icon
          :message="connectionErrors.join('；')"
        />

        <div class="form-footer">
          <a-button data-test="cancel-connection" @click="cancelConnection">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="saving" data-test="save-connection">
            保存
          </a-button>
        </div>
      </a-form>
    </AppDialog>

    <!-- ============ 档案弹窗 ============ -->
    <AppDialog
      v-model="showProfileDialog"
      :title="profileDialogTitle"
      size="md"
      :persistent="true"
      @cancel="cancelProfile"
    >
      <a-form layout="vertical" @submit.prevent="submitProfile">
        <a-form-item label="所属连接" :required="true">
          <a-select
            v-model:value="profileConnectionModel"
            placeholder="选择所属连接"
            data-test="profile-connection"
          >
            <a-select-option
              v-for="c in connections"
              :key="c.connectionKey"
              :value="c.connectionKey"
            >
              {{ c.displayName }}（{{ c.providerName }}）
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="模型类型" :required="true">
          <a-select
            v-model:value="profileTypeModel"
            placeholder="选择模型类型"
            data-test="profile-type"
          >
            <a-select-option v-for="t in MODEL_TYPES" :key="t" :value="t">
              {{ MODEL_TYPE_LABELS[t] }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item
          label="模型名称"
          :required="true"
          :validate-status="profileErrors.some((e) => e.includes('模型名称')) ? 'error' : ''"
          :help="profileErrors.find((e) => e.includes('模型名称')) || ''"
        >
          <a-select
            v-if="!isCustomModelName"
            v-model:value="profileForm.modelName"
            show-search
            placeholder="选择模型"
            data-test="profile-model-name"
          >
            <a-select-option
              v-for="m in modelNameOptions"
              :key="m.modelName"
              :value="m.modelName"
            >
              {{ m.modelName }}
            </a-select-option>
          </a-select>
          <a-input
            v-else
            v-model:value="profileForm.modelName"
            placeholder="输入模型名称"
            data-test="profile-model-name"
          />
        </a-form-item>

        <a-form-item
          v-if="showDimensions"
          label="向量维度"
          :validate-status="profileErrors.some((e) => e.includes('向量维度')) ? 'error' : ''"
          :help="profileErrors.find((e) => e.includes('向量维度')) || ''"
        >
          <a-input-number
            v-model:value="dimensionsModel"
            :min="1"
            :step="1"
            style="width: 100%"
            placeholder="留空使用默认值"
          />
        </a-form-item>

        <a-form-item
          v-if="showContextWindow"
          label="上下文窗口长度"
          :validate-status="profileErrors.some((e) => e.includes('上下文窗口长度')) ? 'error' : ''"
          :help="profileErrors.find((e) => e.includes('上下文窗口长度')) || ''"
        >
          <a-input-number
            v-model:value="contextWindowModel"
            :min="1"
            :step="1"
            style="width: 100%"
            placeholder="留空使用默认值"
          />
        </a-form-item>

        <a-form-item
          label="超时秒数"
          :validate-status="profileErrors.some((e) => e.includes('超时秒数')) ? 'error' : ''"
          :help="profileErrors.find((e) => e.includes('超时秒数')) || ''"
        >
          <a-input-number
            v-model:value="timeoutModel"
            :min="1"
            :max="120"
            :step="1"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item
          v-if="showTemperature"
          label="温度"
          :validate-status="profileErrors.some((e) => e.includes('温度')) ? 'error' : ''"
          :help="profileErrors.find((e) => e.includes('温度')) || ''"
        >
          <a-input-number
            v-model:value="temperatureModel"
            :min="0"
            :max="2"
            :step="0.1"
            style="width: 100%"
            placeholder="留空使用默认值"
          />
        </a-form-item>

        <a-alert
          v-if="profileErrors.length"
          class="form-errors"
          type="error"
          show-icon
          :message="profileErrors.join('；')"
        />

        <div class="form-footer">
          <a-button data-test="cancel-profile" @click="cancelProfile">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="saving" data-test="save-profile">
            保存
          </a-button>
        </div>
      </a-form>
    </AppDialog>

    <!-- ============ 创建连接后确认是否立即添加模型 ============ -->
    <AppDialog
      v-model="showAddModelConfirm"
      title="是否立即添加模型？"
      size="sm"
      :persistent="false"
      @cancel="cancelAddModel"
    >
      <p class="dialog-description">
        已创建连接「{{ pendingConnection?.displayName }}」，是否立即添加模型档案？
      </p>
      <div class="form-footer">
        <a-button data-test="confirm-add-model-no" @click="cancelAddModel">稍后</a-button>
        <a-button type="primary" data-test="confirm-add-model-yes" @click="confirmAddModel">
          添加模型
        </a-button>
      </div>
    </AppDialog>
  </section>
</template>

<style scoped>
.model-settings-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 左右双栏：左 2fr 右 1fr */
.dual-pane {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: start;
}

.left-pane,
.right-pane {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 低于 992px 上下堆叠 */
@media (max-width: 992px) {
  .dual-pane {
    grid-template-columns: 1fr;
  }
}

.settings-section {
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.section-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.section-hint {
  margin: 0 0 12px;
  color: var(--muted-foreground);
  font-size: 12px;
}

/* 默认配置：每项独占一行 */
.defaults-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-top: 8px;
}

.defaults-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.defaults-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

/* 连接卡片 */
.connection-card {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface);
}

.connection-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.connection-card-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.provider-name {
  font-weight: 700;
  color: var(--ink);
}

.display-name {
  color: var(--muted-foreground);
  font-size: 13px;
}

.api-key-mask {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--surface-muted, #f0f3fa);
  color: var(--muted-foreground);
  font-size: 12px;
  font-family: "Roboto Mono", monospace;
}

.connection-card-body {
  margin-top: 10px;
}

.profile-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted, #f0f3fa);
}

.profile-row-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.profile-model-name {
  font-weight: 600;
  color: var(--ink);
}

.profile-test-status {
  font-size: 12px;
}

.connection-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

/* 回收站图标按钮：无边框、仅图标，悬停高亮 */
.icon-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
}

.icon-action-btn :deep(svg) {
  font-size: 16px;
}

/* 可选模型 */
.market-search {
  margin-top: 10px;
}

.type-tag-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.type-tag {
  cursor: pointer;
  user-select: none;
}

.type-tag.active {
  box-shadow: 0 0 0 2px var(--violet);
}

.market-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

/* 目录卡片 */
.catalog-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.catalog-card:hover {
  border-color: var(--violet);
  box-shadow: 0 6px 24px rgb(108 77 255 / 12%);
}

.catalog-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.catalog-provider-name {
  font-weight: 700;
  color: var(--ink);
}

.official-link {
  color: var(--violet);
  text-decoration: none;
  font-size: 14px;
  line-height: 1;
}

.official-link:hover {
  text-decoration: underline;
}

.catalog-models {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
}

.catalog-model-tag {
  margin: 0;
}

.catalog-card-foot {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
}

.custom-card {
  margin-top: 12px;
  cursor: pointer;
}

.custom-hint {
  margin: 0;
  color: var(--muted-foreground);
  font-size: 12px;
  line-height: 1.5;
}

/* 弹窗通用 */
.dialog-description {
  margin: 0 0 12px;
  color: var(--muted-foreground);
  font-size: 13px;
}

.form-errors {
  margin-top: 12px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.cell-muted {
  color: var(--muted-foreground);
}

.cell-success {
  color: var(--teal);
}

.cell-error {
  color: var(--danger);
}

/* RAGFlow 风格：中性色、扁平边框、紧凑信息密度。 */
.model-settings-tab {
  gap: 14px;
  color: var(--ink);
}

.page-heading {
  padding: 0 2px 10px;
}

.page-heading .eyebrow {
  display: none;
}

.page-heading h1 {
  margin: 0 0 6px;
  font-size: 24px;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.page-heading p:not(.eyebrow) {
  font-size: 13px;
}

.dual-pane {
  grid-template-columns: minmax(560px, 1.75fr) minmax(360px, 1fr);
  gap: 14px;
}

.left-pane,
.right-pane {
  gap: 14px;
}

.right-pane {
  position: sticky;
  top: 14px;
}

.settings-section {
  padding: 18px 20px;
  border-radius: 8px;
  box-shadow: none;
}

.section-title {
  margin-bottom: 6px;
}

.defaults-grid {
  gap: 12px;
}

.defaults-item {
  gap: 5px;
}

.defaults-item label {
  font-size: 12px;
  font-weight: 500;
}

.connection-card {
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
}

.connection-card-head {
  min-height: 58px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
}

.connection-card-body {
  margin-top: 0;
  padding: 10px 14px 12px;
}

.profile-list {
  gap: 0;
}

.profile-row {
  min-height: 48px;
  padding: 7px 10px;
  border: 0;
  border-bottom: 1px solid var(--border-color);
  border-radius: 0;
  background: color-mix(in srgb, var(--surface-muted) 68%, var(--surface));
}

.profile-row:first-child {
  border-radius: 6px 6px 0 0;
}

.profile-row:last-child {
  border-bottom: 0;
  border-radius: 0 0 6px 6px;
}

.market-search {
  margin-top: 8px;
}

.type-tag-bar {
  gap: 5px;
  margin: 12px 0 14px;
}

.type-tag {
  margin-inline-end: 0;
  border: 0;
  border-radius: 4px;
  color: var(--muted-foreground);
  background: var(--surface-muted);
}

.type-tag.active {
  color: #fff;
  background: #262626;
  box-shadow: none;
}

.market-grid {
  gap: 10px;
}

.catalog-card {
  gap: 10px;
  min-height: 92px;
  padding: 14px 16px;
  border-radius: 8px;
}

.catalog-card:hover {
  border-color: var(--ink-faint);
  box-shadow: 0 3px 12px rgb(0 0 0 / 6%);
}

.catalog-provider-name {
  font-size: 14px;
  font-weight: 600;
}

.official-link {
  color: var(--ink-soft);
}

.market-section {
  max-height: calc(100vh - 126px);
  overflow-y: auto;
  scrollbar-width: thin;
}

.model-settings-tab :deep(.ant-btn-primary) {
  border-color: #262626;
  background: #262626;
  box-shadow: none;
}

.model-settings-tab :deep(.ant-btn-primary:hover),
.model-settings-tab :deep(.ant-btn-primary:focus) {
  border-color: #404040;
  background: #404040;
}

.model-settings-tab :deep(.ant-select-selector),
.model-settings-tab :deep(.ant-input-affix-wrapper) {
  border-radius: 6px !important;
  box-shadow: none !important;
}

.model-settings-tab :deep(.ant-tag) {
  font-size: 11px;
}

@media (max-width: 992px) {
  .dual-pane {
    grid-template-columns: 1fr;
  }

  .right-pane {
    position: static;
  }

  .market-section {
    max-height: none;
  }
}
</style>
