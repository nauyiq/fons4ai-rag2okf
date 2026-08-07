<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ApiRequestError } from '../../api/http'
import { createModelConnection, createModelProfile, listModelConnections, listModelProfiles, listModelProviderTemplates, testModelProfile, updateModelConnection, updateModelProfile, type ModelConnection, type ModelProfile, type ModelProviderTemplateInfo } from '../../api/models'
import { modelTestLabel } from '../../utils/formatters'

const connections = ref<ModelConnection[]>([])
const profiles = ref<ModelProfile[]>([])
const templates = ref<ModelProviderTemplateInfo[]>([])
const selectedKey = ref('')
const loading = ref(true)
const errorMessage = ref('')
const creatingConnection = ref(false)
const creatingProfile = ref(false)
const testingKey = ref('')
const showConnection = ref(false)
const showProfile = ref(false)
const editingConnection = ref(false)
const editingProfile = ref(false)
const replacingKey = ref(false)
const replacingKeyValue = ref('')
const testResultMessage = ref('')
const testResultProfileKey = ref('')
const savedMessage = ref('')
const connectionForm = ref({ templateCode: 'CUSTOM', providerName: '', displayName: '', baseUrl: '', apiKey: '' })
const profileForm = ref({ modelType: 'CHAT' as 'CHAT' | 'EMBEDDING', modelName: '', dimensions: null as number | null, timeoutSeconds: 60, temperature: null as number | null })
const selected = computed(() => connections.value.find(item => item.connectionKey === selectedKey.value))
const selectedProfiles = computed(() => profiles.value.filter(item => item.connectionKey === selectedKey.value))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [connList, profileList, templateList] = await Promise.all([listModelConnections(), listModelProfiles(), listModelProviderTemplates()])
    connections.value = connList
    profiles.value = profileList
    templates.value = templateList
    selectedKey.value ||= connections.value[0]?.connectionKey ?? ''
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取模型设置。'
  } finally {
    loading.value = false
  }
}

function selectTemplate(code: string): void {
  const template = templates.value.find(item => item.code === code)
  if (template) {
    connectionForm.value.templateCode = code
    connectionForm.value.providerName = template.providerName
    connectionForm.value.baseUrl = template.defaultBaseUrl ?? ''
  }
}

async function saveConnection(): Promise<void> {
  creatingConnection.value = true
  try {
    const item = await createModelConnection(connectionForm.value)
    connections.value.push(item)
    selectedKey.value = item.connectionKey
    showConnection.value = false
    connectionForm.value = { templateCode: 'CUSTOM', providerName: '', displayName: '', baseUrl: '', apiKey: '' }
    savedMessage.value = '连接已创建'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法保存连接。'
  } finally {
    creatingConnection.value = false
  }
}

async function saveProfile(): Promise<void> {
  if (!selectedKey.value) return
  creatingProfile.value = true
  try {
    const item = await createModelProfile({ ...profileForm.value, connectionKey: selectedKey.value })
    profiles.value.push(item)
    showProfile.value = false
    profileForm.value = { modelType: 'CHAT', modelName: '', dimensions: null, timeoutSeconds: 60, temperature: null }
    savedMessage.value = '模型档案已创建'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法保存模型档案。'
  } finally {
    creatingProfile.value = false
  }
}

async function testProfile(profileKey: string): Promise<void> {
  testingKey.value = profileKey
  testResultMessage.value = ''
  testResultProfileKey.value = profileKey
  try {
    const result = await testModelProfile(profileKey)
    const profile = profiles.value.find(item => item.profileKey === profileKey)
    if (profile) profile.lastTestStatus = result.status
    testResultMessage.value = modelTestLabel(result.status, result.errorCode)
  } catch (error) {
    testResultMessage.value = error instanceof ApiRequestError ? error.message : '模型测试未完成。'
  } finally {
    testingKey.value = ''
  }
}

function openConnectionEdit(): void {
  if (!selected.value) return
  connectionForm.value = { templateCode: selected.value.providerCode, providerName: selected.value.providerName, displayName: selected.value.displayName, baseUrl: selected.value.baseUrl, apiKey: '' }
  editingConnection.value = true
}

async function saveConnectionEdit(): Promise<void> {
  if (!selected.value) return
  try {
    const saved = await updateModelConnection(selected.value.connectionKey, { providerName: connectionForm.value.providerName, displayName: connectionForm.value.displayName, baseUrl: connectionForm.value.baseUrl, status: selected.value.status, apiKey: replacingKey.value ? replacingKeyValue.value : '' })
    Object.assign(selected.value, saved)
    editingConnection.value = false
    savedMessage.value = '连接已保存'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法保存连接。'
  } finally {
    clearSensitiveFields()
  }
}

/** 清理 API Key 等敏感字段，防止取消或卸载后残留在响应式内存中（CR-015 AC-046）。 */
function clearSensitiveFields(): void {
  replacingKey.value = false
  replacingKeyValue.value = ''
  connectionForm.value.apiKey = ''
}

/** 取消操作时清理敏感字段并关闭浮层。 */
function cancelEdit(): void {
  editingConnection.value = false
  showConnection.value = false
  showProfile.value = false
  clearSensitiveFields()
}

onBeforeUnmount(clearSensitiveFields)

async function saveProfileEdit(profile: ModelProfile): Promise<void> {
  const saved = await updateModelProfile(profile.profileKey, { modelName: profile.modelName, dimensions: profile.dimensions, timeoutSeconds: profile.timeoutSeconds, temperature: profile.temperature, status: profile.status })
  Object.assign(profile, saved)
  editingProfile.value = false
}

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
      <button class="primary-action" type="button" @click="showConnection = true">＋ 添加连接</button>
    </header>
    <div v-if="loading" class="state-panel">正在读取模型配置…</div>
    <p v-else-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="load">重试</button></p>
    <p v-if="savedMessage" class="success-message" role="status">{{ savedMessage }}</p>
    <div v-else class="model-layout">
      <aside class="settings-section">
        <p class="eyebrow">PROVIDER CONNECTIONS</p>
        <button v-for="item in connections" :key="item.connectionKey" class="model-row" :class="{ active: item.connectionKey === selectedKey }" @click="selectedKey = item.connectionKey">
          <strong>{{ item.displayName }}</strong>
          <span>{{ item.apiKeyMask ? `已配置 ${item.apiKeyMask}` : '尚未配置密钥' }}</span>
        </button>
        <p v-if="!connections.length" class="read-only-note">尚未创建 Provider 连接。</p>
      </aside>
      <section class="settings-section">
        <template v-if="selected">
          <p class="eyebrow">CURRENT CONNECTION</p>
          <h2>{{ selected.displayName }}</h2>
          <p class="read-only-note">Base URL: {{ selected.baseUrl }}</p>
          <div class="key-row">
            <span>API Key: {{ selected.apiKeyMask ? `已配置（${selected.apiKeyMask}）` : '请先配置 API Key' }}</span>
            <button class="text-button" type="button" @click="replacingKey = !replacingKey; if (!replacingKey) replacingKeyValue = ''">{{ replacingKey ? '取消替换' : '替换 Key' }}</button>
          </div>
          <div v-if="replacingKey" class="inline-form">
            <label>新 API Key<input v-model="replacingKeyValue" type="password" autocomplete="off" /></label>
            <button class="secondary-action" @click="saveConnectionEdit">保存新 Key</button>
          </div>
          <div class="connection-actions">
            <button class="secondary-action" @click="openConnectionEdit">编辑连接</button>
            <button class="secondary-action" @click="showProfile = true">添加模型档案</button>
          </div>

          <div v-if="editingConnection" class="drawer-backdrop">
            <form class="settings-drawer" @submit.prevent="saveConnectionEdit">
              <h2>编辑连接</h2>
              <label>厂商名称<input v-model="connectionForm.providerName" required /></label>
              <label>连接名称<input v-model="connectionForm.displayName" required /></label>
              <label>Base URL<input v-model="connectionForm.baseUrl" type="url" required /></label>
              <p class="read-only-note">API Key 请使用"替换 Key"功能修改。</p>
              <footer><button class="secondary-action" type="button" @click="cancelEdit">取消</button><button class="primary-action">保存</button></footer>
            </form>
          </div>

          <h3>模型档案</h3>
          <div v-for="profile in selectedProfiles" :key="profile.profileKey" class="profile-row">
            <strong>{{ profile.modelType === 'CHAT' ? '文本模型' : '向量化模型' }} · {{ profile.modelName }}</strong>
            <span>{{ profile.status }} · 最近测试：{{ profile.lastTestStatus ? modelTestLabel(profile.lastTestStatus) : '未测试' }}</span>
            <button class="text-button" :disabled="testingKey === profile.profileKey" @click="testProfile(profile.profileKey)">{{ testingKey === profile.profileKey ? '测试中…' : '测试模型' }}</button>
            <p v-if="testResultProfileKey === profile.profileKey && testResultMessage" class="test-result">{{ testResultMessage }}</p>
          </div>
          <p v-if="!selectedProfiles.length" class="read-only-note">当前连接还没有模型档案。</p>
          <p class="fee-notice">会向模型厂商发送固定测试文本，可能产生少量费用。</p>
        </template>
        <div v-else class="empty-panel">
          <strong>先添加一个 Provider 连接</strong>
          <p>连接、档案与知识库用途绑定保持独立，避免系统默认模型。</p>
        </div>
      </section>
    </div>

    <div v-if="showConnection" class="drawer-backdrop">
      <form class="settings-drawer" @submit.prevent="saveConnection">
        <h2>添加 Provider 连接</h2>
        <label>选择模板
          <select v-model="connectionForm.templateCode" @change="selectTemplate(connectionForm.templateCode)">
            <option v-for="tpl in templates" :key="tpl.code" :value="tpl.code">{{ tpl.providerName }}</option>
          </select>
        </label>
        <label>厂商名称<input v-model="connectionForm.providerName" required /></label>
        <label>连接名称<input v-model="connectionForm.displayName" required /></label>
        <label>Base URL<input v-model="connectionForm.baseUrl" type="url" required /></label>
        <label>API Key<input v-model="connectionForm.apiKey" type="password" required autocomplete="off" /></label>
        <footer><button class="secondary-action" type="button" @click="cancelEdit">取消</button><button class="primary-action" :disabled="creatingConnection">保存连接</button></footer>
      </form>
    </div>

    <div v-if="showProfile" class="drawer-backdrop">
      <form class="settings-drawer" @submit.prevent="saveProfile">
        <h2>添加模型档案</h2>
        <label>类型<select v-model="profileForm.modelType"><option value="CHAT">文本模型</option><option value="EMBEDDING">向量化模型</option></select></label>
        <label>模型名称<input v-model="profileForm.modelName" required /></label>
        <label>超时秒数<input v-model.number="profileForm.timeoutSeconds" type="number" min="1" max="120" /></label>
        <label v-if="profileForm.modelType === 'EMBEDDING'">向量维度<input v-model.number="profileForm.dimensions" type="number" /></label>
        <footer><button class="secondary-action" type="button" @click="cancelEdit">取消</button><button class="primary-action" :disabled="creatingProfile">保存档案</button></footer>
      </form>
    </div>
  </section>
</template>
