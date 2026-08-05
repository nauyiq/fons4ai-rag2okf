<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getKnowledgeBase, updateKnowledgeBase } from '../../api/knowledge-bases'
import { ApiRequestError } from '../../api/http'
import { useWorkspaceStore } from '../../stores/workspace'
import { listModelProfiles, type ModelProfile } from '../../api/models'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const savedMessage = ref('')
const modelProfiles = ref<ModelProfile[]>([])
const form = reactive({
  name: '', description: '', autoParse: false, autoPublish: false, parserProfile: 'standard', chunkSize: 800, overlap: 120, revision: 0, answerProfileKey: '', embeddingProfileKey: '',
})

async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [knowledgeBase, profiles] = await Promise.all([getKnowledgeBase(String(route.params.knowledgeBaseKey)), listModelProfiles()])
    modelProfiles.value = profiles.filter(item => item.status === 'ACTIVE')
    Object.assign(form, {
      name: knowledgeBase.name, description: knowledgeBase.description, autoParse: knowledgeBase.autoParse,
      autoPublish: knowledgeBase.autoPublish, parserProfile: knowledgeBase.parserProfile,
      chunkSize: knowledgeBase.chunkProfile.chunkSize, overlap: knowledgeBase.chunkProfile.overlap, revision: knowledgeBase.revision,
      answerProfileKey: knowledgeBase.modelBindings.find(item => item.usageType === 'ANSWER_GENERATION')?.modelProfileKey ?? '', embeddingProfileKey: knowledgeBase.modelBindings.find(item => item.usageType === 'EMBEDDING')?.modelProfileKey ?? '',
    })
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取知识库设置。'
  } finally {
    loading.value = false
  }
}

async function saveSettings(): Promise<void> {
  if (form.autoPublish && !form.autoParse) {
    errorMessage.value = '自动发布依赖自动解析，请先开启自动解析。'
    return
  }
  saving.value = true
  errorMessage.value = ''
  savedMessage.value = ''
  try {
    const saved = await updateKnowledgeBase(String(route.params.knowledgeBaseKey), {
      name: form.name, description: form.description, autoParse: form.autoParse, autoPublish: form.autoPublish,
      parserProfile: form.parserProfile,
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: form.chunkSize, overlap: form.overlap, titleLevel: null },
      modelBindings: [{ usageType: 'ANSWER_GENERATION', modelProfileKey: form.answerProfileKey }, { usageType: 'EMBEDDING', modelProfileKey: form.embeddingProfileKey }].filter(item => item.modelProfileKey),
      revision: form.revision,
    })
    form.revision = saved.revision
    savedMessage.value = '设置已保存，仅应用于之后发起的上传和处理操作。'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。'
  } finally {
    saving.value = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <section class="knowledge-page settings-page">
    <header class="page-heading compact"><div><button class="back-link" type="button" @click="router.push({ name: 'knowledge-bases' })">← 返回知识库</button><p class="eyebrow">KNOWLEDGE BASE SETTINGS</p><h1>{{ form.name || '知识库设置' }}</h1><p>定义之后上传文件的默认加工路径。</p></div></header>
    <p class="notice-panel"><b>只影响后续操作</b><span>修改自动解析、自动发布或分块配置，不会重新处理已有文档，也不会隐式批量执行。</span></p>
    <div v-if="loading" class="state-panel">正在读取设置…</div>
    <p v-else-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="loadSettings">重试</button></p>
    <form v-else class="settings-form" @submit.prevent="saveSettings">
      <section class="settings-section"><header><p class="eyebrow">AUTOMATION</p><h2>自动化策略</h2></header><div class="toggle-row"><div><strong>自动解析</strong><span>文件上传成功后自动进入解析流程。</span></div><input v-model="form.autoParse" type="checkbox" role="switch" :disabled="!workspaceStore.canManage" /></div><div class="toggle-row"><div><strong>自动发布</strong><span>仅当解析成功时，自动发布为可检索知识。</span></div><input v-model="form.autoPublish" type="checkbox" role="switch" :disabled="!workspaceStore.canManage || !form.autoParse" /></div></section>
      <section class="settings-section"><header><p class="eyebrow">PROCESSING PROFILE</p><h2>解析与分块</h2></header><div class="form-columns"><label>Parser Profile<select v-model="form.parserProfile" :disabled="!workspaceStore.canManage"><option value="standard">标准解析</option><option value="structure-first">结构优先</option></select></label><label>分块大小<input v-model.number="form.chunkSize" type="number" min="100" :disabled="!workspaceStore.canManage" /></label><label>重叠量<input v-model.number="form.overlap" type="number" min="0" :disabled="!workspaceStore.canManage" /></label></div></section>
      <section class="settings-section"><header><p class="eyebrow">MODEL BINDINGS</p><h2>知识库模型用途</h2></header><p v-if="!modelProfiles.length" class="notice-panel">请先完成模型设置；系统不会自动使用全局默认模型。</p><div v-else class="form-columns"><label>回答生成<select v-model="form.answerProfileKey" :disabled="!workspaceStore.canManage"><option value="">未绑定</option><option v-for="profile in modelProfiles.filter(item => item.modelType === 'CHAT')" :key="profile.profileKey" :value="profile.profileKey">{{ profile.modelName }} · {{ profile.lastTestStatus }}</option></select></label><label>向量化<select v-model="form.embeddingProfileKey" :disabled="!workspaceStore.canManage"><option value="">未绑定</option><option v-for="profile in modelProfiles.filter(item => item.modelType === 'EMBEDDING')" :key="profile.profileKey" :value="profile.profileKey">{{ profile.modelName }} · {{ profile.lastTestStatus }}</option></select></label></div></section>
      <p v-if="savedMessage" class="success-message" role="status">{{ savedMessage }}</p><p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
      <footer v-if="workspaceStore.canManage"><button class="primary-action" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存后续默认设置' }}</button></footer><p v-else class="read-only-note">你当前以知识用户身份查看；服务端也会校验所有修改请求。</p>
    </form>
  </section>
</template>
