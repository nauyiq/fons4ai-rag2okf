<script setup lang="ts">
/**
 * 模型设置视图（T011 合并表单版本）。
 *
 * <p>布局：单列表 + 中央 AppDialog 合并表单。
 * 新增和编辑共用一个 AppDialog，基础信息与高级参数分组呈现；
 * 高级参数默认折叠，按需展开。API Key 在新增时必填，编辑时不回显原值，
 * 通过"替换 Key"独立入口修改。提交时调用 createModelConfig / updateModelConfig 单步 API。
 *
 * <p>表单状态与校验规则由 useModelForm composable 承载，视图不内嵌业务规则（DDD-lite）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiRequestError } from '../../api/http'
import {
  createModelConfig,
  listModelConfigs,
  listModelProviderTemplates,
  replaceModelApiKey,
  testModelConfig,
  updateModelConfig,
  type ModelConfig,
  type ModelProviderTemplateInfo,
} from '../../api/models'
import AppDialog from '../../components/ui/AppDialog.vue'
import { useModelForm } from '../../composables/useModelForm'
import { modelTestLabel } from '../../utils/formatters'

const configs = ref<ModelConfig[]>([])
const templates = ref<ModelProviderTemplateInfo[]>([])
const selectedKey = ref('')
const loading = ref(true)
const errorMessage = ref('')
const saving = ref(false)
const testingKey = ref('')
const showFormDialog = ref(false)
const replacingKey = ref(false)
const replacingKeyValue = ref('')
const testResultMessage = ref('')
const testResultConfigKey = ref('')
const savedMessage = ref('')
/** 表单校验错误列表，提交前由 composable 校验填充 */
const formErrors = ref<string[]>([])

const {
  form,
  isCreate,
  advancedOpen,
  dialogTitle,
  submitLabel,
  prepareCreate,
  prepareEdit,
  selectTemplate,
  buildInput,
  validate,
} = useModelForm()

const selected = computed(() => configs.value.find(item => item.modelConfigKey === selectedKey.value))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [configList, templateList] = await Promise.all([listModelConfigs(), listModelProviderTemplates()])
    configs.value = configList
    templates.value = templateList
    selectedKey.value ||= configs.value[0]?.modelConfigKey ?? ''
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取模型设置。'
  } finally {
    loading.value = false
  }
}

/** 打开新增对话框：重置表单为新增模式 */
function openCreate(): void {
  prepareCreate()
  formErrors.value = []
  showFormDialog.value = true
}

/** 打开编辑对话框：从选中配置加载表单（apiKey 留空） */
function openEdit(): void {
  if (!selected.value) return
  prepareEdit(selected.value)
  formErrors.value = []
  showFormDialog.value = true
}

/** 选择模板时查找模板并预填厂商和 Base URL */
function onTemplateChange(code: string): void {
  const template = templates.value.find(item => item.code === code)
  selectTemplate(template)
}

/** 提交合并表单：校验通过后按模式调用单步创建或更新 API */
async function submitForm(): Promise<void> {
  const errors = validate()
  if (errors.length) {
    formErrors.value = errors
    return
  }
  formErrors.value = []
  saving.value = true
  try {
    if (isCreate.value) {
      const item = await createModelConfig(buildInput())
      configs.value.push(item)
      selectedKey.value = item.modelConfigKey
      savedMessage.value = '模型配置已创建'
    } else if (selected.value) {
      const saved = await updateModelConfig(selected.value.modelConfigKey, buildInput())
      if (saved) Object.assign(selected.value, saved)
      savedMessage.value = '配置已保存'
    }
    showFormDialog.value = false
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法保存模型配置。'
  } finally {
    saving.value = false
    clearSensitiveFields()
  }
}

async function testConfig(configKey: string): Promise<void> {
  testingKey.value = configKey
  testResultMessage.value = ''
  testResultConfigKey.value = configKey
  try {
    const result = await testModelConfig(configKey)
    const config = configs.value.find(item => item.modelConfigKey === configKey)
    if (config) config.lastTestStatus = result.status
    testResultMessage.value = modelTestLabel(result.status, result.errorCode)
  } catch (error) {
    testResultMessage.value = error instanceof ApiRequestError ? error.message : '模型测试未完成。'
  } finally {
    testingKey.value = ''
  }
}

/** 替换 API Key：独立入口，不更新其他字段 */
async function saveReplaceKey(): Promise<void> {
  if (!selected.value) return
  try {
    const result = await replaceModelApiKey(selected.value.modelConfigKey, replacingKeyValue.value)
    if (result) selected.value.apiKeyMask = result.apiKeyMask
    savedMessage.value = 'API Key 已替换'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法替换 API Key。'
  } finally {
    clearSensitiveFields()
  }
}

/** 清理 API Key 等敏感字段，防止取消或卸载后残留在响应式内存中（CR-015 AC-046）。 */
function clearSensitiveFields(): void {
  replacingKey.value = false
  replacingKeyValue.value = ''
  form.value.apiKey = ''
}

/** 取消表单：关闭对话框并清理敏感字段 */
function cancelForm(): void {
  showFormDialog.value = false
  formErrors.value = []
  clearSensitiveFields()
}

/** 切换"替换 Key"内联表单显示 */
function toggleReplaceKey(): void {
  replacingKey.value = !replacingKey.value
  if (!replacingKey.value) replacingKeyValue.value = ''
}

onBeforeUnmount(clearSensitiveFields)
onMounted(load)
</script>

<template>
  <section class="knowledge-page settings-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">MODEL CONFIGURATION</p>
        <h1>模型设置</h1>
        <p>连接由本人维护；密钥保存后仅显示配置状态。</p>
      </div>
      <button class="primary-action" type="button" @click="openCreate">＋ 添加模型</button>
    </header>
    <div v-if="loading" class="state-panel">正在读取模型配置…</div>
    <p v-else-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="load">重试</button></p>
    <p v-if="savedMessage" class="success-message" role="status">{{ savedMessage }}</p>
    <div v-if="!loading && !errorMessage" class="model-layout">
      <aside class="settings-section">
        <p class="eyebrow">MODEL CONFIGS</p>
        <button v-for="item in configs" :key="item.modelConfigKey" class="model-row" :class="{ active: item.modelConfigKey === selectedKey }" @click="selectedKey = item.modelConfigKey">
          <strong>{{ item.displayName }}</strong>
          <span>{{ item.apiKeyMask ? `已配置 ${item.apiKeyMask}` : '尚未配置密钥' }}</span>
        </button>
        <p v-if="!configs.length" class="read-only-note">尚未创建模型配置。</p>
      </aside>
      <section class="settings-section">
        <template v-if="selected">
          <p class="eyebrow">CURRENT CONFIG</p>
          <h2>{{ selected.displayName }}</h2>
          <p class="read-only-note">厂商: {{ selected.providerName }} · Base URL: {{ selected.baseUrl }}</p>
          <p class="read-only-note">模型: {{ selected.modelType === 'CHAT' ? '文本模型' : '向量化模型' }} · {{ selected.modelName }}</p>
          <p class="read-only-note">状态: {{ selected.status }} · 最近测试：{{ selected.lastTestStatus ? modelTestLabel(selected.lastTestStatus) : '未测试' }}</p>
          <div class="key-row">
            <span>API Key: {{ selected.apiKeyMask ? `已配置（${selected.apiKeyMask}）` : '请先配置 API Key' }}</span>
            <button class="text-button" type="button" @click="toggleReplaceKey">{{ replacingKey ? '取消替换' : '替换 Key' }}</button>
          </div>
          <div v-if="replacingKey" class="inline-form">
            <label>新 API Key<input v-model="replacingKeyValue" type="password" autocomplete="off" /></label>
            <button class="secondary-action" @click="saveReplaceKey">保存新 Key</button>
          </div>
          <div class="connection-actions">
            <button class="secondary-action" @click="openEdit">编辑配置</button>
            <button class="secondary-action" :disabled="testingKey === selected.modelConfigKey" @click="testConfig(selected.modelConfigKey)">{{ testingKey === selected.modelConfigKey ? '测试中…' : '测试模型' }}</button>
          </div>
          <p v-if="testResultConfigKey === selected.modelConfigKey && testResultMessage" class="test-result">{{ testResultMessage }}</p>
          <p class="fee-notice">会向模型厂商发送固定测试文本，可能产生少量费用。</p>
        </template>
        <div v-else class="empty-panel">
          <strong>先添加一个模型配置</strong>
          <p>模型配置包含连接信息和模型档案，创建后可绑定到知识库。</p>
        </div>
      </section>
    </div>

    <!-- 合并表单 AppDialog：新增/编辑共用，基础信息 + 高级参数分组 -->
    <AppDialog v-model="showFormDialog" :title="dialogTitle" size="md">
      <header>
        <h2 id="dialog-title">{{ dialogTitle }}</h2>
      </header>
      <form class="model-form" @submit.prevent="submitForm">
        <fieldset class="form-group">
          <legend>基础信息</legend>
          <label v-if="isCreate">选择模板
            <select v-model="form.templateCode" @change="onTemplateChange(form.templateCode)">
              <option v-for="tpl in templates" :key="tpl.code" :value="tpl.code">{{ tpl.providerName }}</option>
            </select>
          </label>
          <label>厂商名称<input v-model="form.providerName" required /></label>
          <label>显示名称<input v-model="form.displayName" required /></label>
          <label>Base URL<input v-model="form.baseUrl" type="url" required /></label>
          <label v-if="isCreate">API Key<input v-model="form.apiKey" type="password" required autocomplete="off" /></label>
          <p v-else class="read-only-note">API Key 请使用"替换 Key"功能修改。</p>
          <label>类型
            <select v-model="form.modelType">
              <option value="CHAT">文本模型</option>
              <option value="EMBEDDING">向量化模型</option>
            </select>
          </label>
          <label>模型名称<input v-model="form.modelName" required /></label>
        </fieldset>

        <button type="button" class="advanced-toggle text-button" @click="advancedOpen = !advancedOpen">
          {{ advancedOpen ? '收起高级参数' : '展开高级参数' }}
        </button>
        <fieldset v-if="advancedOpen" class="form-group advanced-section">
          <legend>高级参数</legend>
          <label>上下文窗口长度<input v-model.number="form.contextWindowLength" type="number" min="1" /></label>
          <label>超时秒数<input v-model.number="form.timeoutSeconds" type="number" min="1" max="120" /></label>
          <label>温度<input v-model.number="form.temperature" type="number" min="0" max="2" step="0.1" /></label>
          <label v-if="form.modelType === 'EMBEDDING'">向量维度<input v-model.number="form.dimensions" type="number" min="1" /></label>
        </fieldset>

        <p v-if="formErrors.length" class="inline-error" role="alert">
          <span v-for="err in formErrors" :key="err">{{ err }}</span>
        </p>

        <footer class="dialog-actions">
          <button class="secondary-action" type="button" @click="cancelForm">取消</button>
          <button class="primary-action" type="submit" :disabled="saving">{{ saving ? '保存中…' : submitLabel }}</button>
        </footer>
      </form>
    </AppDialog>
  </section>
</template>

<style scoped>
/* 合并表单容器：纵向排列字段 */
.model-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 字段分组：基础信息与高级参数各自一组，带分隔边框 */
.form-group {
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 14px;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-group legend {
  color: var(--ink-soft);
  font-size: 13px;
  padding: 0 6px;
}

/* 高级参数折叠区域：与切换按钮紧邻 */
.advanced-section {
  border-color: var(--line);
}

.advanced-toggle {
  align-self: flex-start;
  color: var(--violet);
}

/* 对话框底部操作区 */
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}
</style>
