<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { type ThemeMode, useTheme } from '../../composables/useTheme'
import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

interface PersonalPreferences { theme: ThemeMode; defaultChunkSize: number; defaultChunkOverlap: number }
const defaults: PersonalPreferences = { theme: 'system', defaultChunkSize: 800, defaultChunkOverlap: 120 }
const router = useRouter()
const sessionStore = useSessionStore()
const { mode, setTheme } = useTheme()
const form = reactive<PersonalPreferences>({ ...defaults })
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const savedMessage = ref('')

function parsePreferences(value: string): PersonalPreferences {
  try {
    const parsed = JSON.parse(value) as Partial<PersonalPreferences>
    return { theme: ['light', 'dark', 'system'].includes(parsed.theme ?? '') ? parsed.theme as ThemeMode : defaults.theme,
      defaultChunkSize: Number.isFinite(parsed.defaultChunkSize) ? Number(parsed.defaultChunkSize) : defaults.defaultChunkSize,
      defaultChunkOverlap: Number.isFinite(parsed.defaultChunkOverlap) ? Number(parsed.defaultChunkOverlap) : defaults.defaultChunkOverlap }
  } catch { return { ...defaults } }
}

async function loadPreferences(): Promise<void> {
  loading.value = true
  try {
    await sessionStore.loadProfile()
    Object.assign(form, parsePreferences(sessionStore.profile?.preferenceJson ?? '{}'))
    setTheme(form.theme)
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取个人偏好。'
  } finally { loading.value = false }
}

async function savePreferences(): Promise<void> {
  saving.value = true; savedMessage.value = ''; errorMessage.value = ''
  try {
    if (form.defaultChunkOverlap >= form.defaultChunkSize) { errorMessage.value = '分块重叠量必须小于分块大小。'; return }
    const current = sessionStore.profile
    if (!current) throw new Error('未找到当前用户')
    await sessionStore.saveProfile({ displayName: current.displayName, avatarUrl: current.avatarUrl, preferenceJson: JSON.stringify(form) })
    setTheme(form.theme)
    savedMessage.value = '个人偏好已保存，默认分块习惯只应用于之后创建的设置。'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。'
  } finally { saving.value = false }
}

onMounted(loadPreferences)
</script>

<template>
  <section class="knowledge-page settings-page personal-settings-page">
    <header class="page-heading compact"><div><button class="back-link" type="button" @click="router.push({ name: 'profile' })">← 返回个人中心</button><p class="eyebrow">SETTINGS / PERSONAL</p><h1>个人偏好</h1><p>在个人、知识库与模型配置之间保持清晰边界。</p></div></header>
    <div v-if="loading" class="state-panel">正在读取个人偏好…</div>
    <form v-else class="settings-form" @submit.prevent="savePreferences">
      <section class="settings-section"><header><p class="eyebrow">PERSONAL DEFAULTS</p><h2>显示与默认分块</h2></header><div class="form-columns"><label>界面主题<select v-model="form.theme" @change="setTheme(form.theme)"><option value="system">跟随系统</option><option value="light">明亮</option><option value="dark">暗色</option></select></label><label>默认分块大小<input v-model.number="form.defaultChunkSize" type="number" min="100" max="4000" /></label><label>默认重叠量<input v-model.number="form.defaultChunkOverlap" type="number" min="0" max="1000" /></label></div><p class="read-only-note">当前主题：{{ mode === 'system' ? '跟随系统' : mode === 'light' ? '明亮' : '暗色' }}。分块习惯不会重新处理已有文档。</p></section>
      <section class="settings-section settings-map"><header><p class="eyebrow">SETTINGS MAP</p><h2>配置在正确的层级发生</h2></header><div class="settings-lanes"><article class="active"><b>01</b><strong>个人偏好</strong><span>主题与默认分块习惯</span></article><article><b>02</b><strong>知识库设置</strong><span>自动解析、发布与具体处理策略</span><button class="text-button" type="button" @click="router.push({ name: 'knowledge-bases' })">前往知识库 →</button></article><article><b>03</b><strong>模型设置</strong><span>Provider 与模型档案将在模型配置任务中开放</span></article></div></section>
      <p v-if="savedMessage" class="success-message" role="status">{{ savedMessage }}</p><p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p><footer><button class="primary-action" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存个人偏好' }}</button></footer>
    </form>
  </section>
</template>
