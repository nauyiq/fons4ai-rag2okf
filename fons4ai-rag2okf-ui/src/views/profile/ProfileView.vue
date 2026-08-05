<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const savedMessage = ref('')
const form = reactive({ displayName: '', avatarUrl: '', preferenceJson: '{}' })

async function loadProfile(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await sessionStore.loadProfile()
    const profile = sessionStore.profile
    if (profile) Object.assign(form, { displayName: profile.displayName ?? '', avatarUrl: profile.avatarUrl ?? '', preferenceJson: profile.preferenceJson || '{}' })
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '无法读取个人资料。'
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  saving.value = true
  savedMessage.value = ''
  errorMessage.value = ''
  try {
    await sessionStore.saveProfile({ ...form, displayName: form.displayName.trim(), avatarUrl: form.avatarUrl.trim() })
    savedMessage.value = '个人资料已保存。'
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。'
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="knowledge-page profile-page">
    <header class="page-heading"><div><p class="eyebrow">LOCAL PROFILE</p><h1>个人中心</h1><p>维护只属于你的展示资料与个人偏好。</p></div><button class="secondary-action" type="button" @click="router.push({ name: 'personal-settings' })">打开个人偏好 →</button></header>
    <div v-if="loading" class="state-panel">正在读取个人资料…</div>
    <p v-else-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }} <button type="button" @click="loadProfile">重试</button></p>
    <form v-else class="profile-layout" @submit.prevent="saveProfile">
      <aside class="profile-identity"><div class="profile-avatar">{{ (form.displayName || sessionStore.profile?.email || 'U').slice(0, 1).toUpperCase() }}</div><strong>{{ form.displayName || '未命名用户' }}</strong><span>{{ sessionStore.profile?.email }}</span><p>邮箱仅用于登录，本页不展示密码、令牌或验证状态。</p></aside>
      <section class="settings-section"><header><p class="eyebrow">PROFILE DETAILS</p><h2>展示资料</h2></header><div class="form-columns"><label>登录邮箱<input :value="sessionStore.profile?.email" type="email" readonly aria-readonly="true" /></label><label>展示名称<input v-model="form.displayName" maxlength="80" autocomplete="nickname" placeholder="例如：洪启阳" /></label><label class="full-width">头像地址（可选）<input v-model="form.avatarUrl" type="url" maxlength="500" placeholder="https://…" /></label></div><p class="read-only-note">登录邮箱由账号维护，当前仅可查看。</p><p v-if="savedMessage" class="success-message" role="status">{{ savedMessage }}</p><p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p><footer><button class="primary-action" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存资料' }}</button></footer></section>
    </form>
  </section>
</template>
