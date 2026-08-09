<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ email: '', password: '', confirmPassword: '', displayName: '', termsAccepted: false })

const rules: Record<string, Rule[]> = {
  email: [
    { required: true, message: '请输入邮箱。', trigger: 'change' },
    { type: 'email', message: '请输入有效的邮箱地址。', trigger: 'change' },
  ],
  password: [{ required: true, message: '请输入密码。', trigger: 'change' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码。', trigger: 'change' },
    {
      validator: (_rule, value) => (value === form.password ? Promise.resolve() : Promise.reject('两次输入的密码不一致。')),
      trigger: 'change',
    },
  ],
  termsAccepted: [
    {
      validator: (_rule, value) => (value ? Promise.resolve() : Promise.reject('请阅读并同意服务条款后再创建账号。')),
      trigger: 'change',
    },
  ],
}

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await sessionStore.signUp({
      email: form.email.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
      displayName: form.displayName.trim(),
      termsAccepted: form.termsAccepted,
    })
    message.success('账号创建成功，正在进入知识空间…')
    await router.replace('/knowledge-bases')
  } catch (error) {
    message.error(error instanceof ApiRequestError && error.status === 429
      ? '注册请求过于频繁，请稍后再试。'
      : '注册失败，请更换邮箱或密码后重试。')
  } finally {
    submitting.value = false
    form.password = ''
    form.confirmPassword = ''
  }
}
</script>

<template>
  <main class="login-stage">
    <section class="login-story" aria-label="Rag2OKF 知识加工脉络">
      <div class="story-brand"><span class="story-mark"></span><span>Rag2OKF</span></div>
      <div class="story-copy"><p class="eyebrow">EVIDENCE, MADE USEFUL</p><h1>把来源文件<br />变成可追溯的知识。</h1><p>从原始证据到可用知识，每一次加工都保留清晰脉络。</p></div>
      <ol class="processing-path">
        <li class="active"><b>01</b><span><strong>源文件</strong><small>保留原始证据</small></span></li>
        <li><b>02</b><span><strong>结构解析</strong><small>识别正文与上下文</small></span></li>
        <li><b>03</b><span><strong>知识分块</strong><small>形成可检索切片</small></span></li>
        <li><b>04</b><span><strong>发布</strong><small>进入受控检索</small></span></li>
        <li class="success"><b>05</b><span><strong>OKF</strong><small>沉淀可治理知识</small></span></li>
      </ol>
    </section>

    <section class="login-panel">
      <a-form
        ref="formRef"
        class="login-card"
        layout="vertical"
        :model="form"
        :rules="rules"
        @submit.prevent="submit"
      >
        <p class="eyebrow">CREATE ACCOUNT</p>
        <h2>注册你的知识空间</h2>
        <p class="login-lead">使用邮箱与密码创建本地账号，立即拥有个人知识空间。</p>
        <a-form-item label="邮箱" name="email">
          <a-input
            v-model:value="form.email"
            type="email"
            inputmode="email"
            autocomplete="email"
            :maxlength="254"
            placeholder="name@example.com"
          />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password
            v-model:value="form.password"
            autocomplete="new-password"
            placeholder="8～64 位密码"
          />
        </a-form-item>
        <a-form-item label="确认密码" name="confirmPassword" :dependencies="['password']">
          <a-input-password
            v-model:value="form.confirmPassword"
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </a-form-item>
        <a-form-item label="展示名称（可选）" name="displayName">
          <a-input
            v-model:value="form.displayName"
            :maxlength="80"
            autocomplete="nickname"
            placeholder="输入你的展示名称"
          />
        </a-form-item>
        <a-form-item name="termsAccepted">
          <a-checkbox v-model:checked="form.termsAccepted">我已阅读并同意服务条款</a-checkbox>
        </a-form-item>
        <a-button type="primary" html-type="submit" class="login-submit" :loading="submitting">
          创建账号
        </a-button>
        <p class="login-footnote">已有账号？<router-link to="/login">返回登录</router-link></p>
      </a-form>
    </section>
  </main>
</template>

<style scoped>
:deep(.ant-form-item) {
  margin-bottom: 0;
}
</style>
