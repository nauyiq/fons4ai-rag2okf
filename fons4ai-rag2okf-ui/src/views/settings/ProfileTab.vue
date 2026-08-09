<script setup lang="ts">
/**
 * 个人信息设置 Tab。
 *
 * <p>展示资料表单：登录邮箱（只读）、展示名称、头像地址（可选）。
 * 数据通过 useSessionStore 加载与保存，成功/失败反馈使用 antd message。
 */
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const sessionStore = useSessionStore()
const loading = ref(true)
const saving = ref(false)
const form = reactive({ displayName: '', avatarUrl: '', preferenceJson: '{}' })

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    await sessionStore.loadProfile()
    const profile = sessionStore.profile
    if (profile) {
      form.displayName = profile.displayName ?? ''
      form.avatarUrl = profile.avatarUrl ?? ''
      form.preferenceJson = profile.preferenceJson || '{}'
    }
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '无法读取个人资料。')
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  saving.value = true
  try {
    await sessionStore.saveProfile({
      ...form,
      displayName: form.displayName.trim(),
      avatarUrl: form.avatarUrl.trim(),
    })
    message.success('个人资料已保存。')
  } catch (error) {
    message.error(error instanceof ApiRequestError ? error.message : '保存失败，请稍后重试。')
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="profile-tab">
    <div class="settings-section">
      <header>
        <p class="eyebrow">PROFILE</p>
        <h2>个人信息</h2>
      </header>
      <a-spin :spinning="loading">
        <a-form layout="vertical" @submit.prevent="saveProfile">
          <a-form-item label="登录邮箱">
            <a-input :value="sessionStore.profile?.email" read-only placeholder="加载中…" />
          </a-form-item>
          <a-form-item label="展示名称">
            <a-input
              v-model:value="form.displayName"
              :maxlength="80"
              autocomplete="nickname"
              placeholder="例如：洪启阳"
            />
          </a-form-item>
          <a-form-item label="头像地址（可选）">
            <a-input
              v-model:value="form.avatarUrl"
              type="url"
              :maxlength="500"
              placeholder="https://…"
            />
          </a-form-item>
          <p class="read-only-note">登录邮箱由账号维护，当前仅可查看。</p>
          <div class="profile-footer">
            <a-button type="primary" html-type="submit" :loading="saving">保存资料</a-button>
          </div>
        </a-form>
      </a-spin>
    </div>
  </section>
</template>

<style scoped>
.profile-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
