<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ email: '', password: '', confirmPassword: '', displayName: '', termsAccepted: false })

async function submit(): Promise<void> {
  errorMessage.value = ''
  if (!form.email.trim() || !form.password) {
    errorMessage.value = '请输入邮箱和密码。'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致。'
    return
  }
  if (!form.termsAccepted) {
    errorMessage.value = '请阅读并同意服务条款后再创建账号。'
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
    await router.replace('/knowledge-bases')
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError && error.status === 429
      ? '注册请求过于频繁，请稍后再试。'
      : '注册失败，请更换邮箱或密码后重试。'
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
      <form class="login-card" @submit.prevent="submit">
        <p class="eyebrow">CREATE ACCOUNT</p><h2>注册你的知识空间</h2><p class="login-lead">使用邮箱与密码创建本地账号，立即拥有个人知识空间。</p>
        <label>邮箱<input v-model="form.email" type="email" inputmode="email" autocomplete="email" maxlength="254" placeholder="name@example.com" /></label>
        <label>密码<input v-model="form.password" type="password" autocomplete="new-password" placeholder="8～64 位密码" /></label>
        <label>确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" /></label>
        <label>展示名称（可选）<input v-model="form.displayName" maxlength="80" autocomplete="nickname" placeholder="输入你的展示名称" /></label>
        <label class="terms-row"><input v-model="form.termsAccepted" type="checkbox" name="terms" />我已阅读并同意服务条款</label>
        <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-action login-submit" type="submit" :disabled="submitting">{{ submitting ? '正在创建…' : '创建账号' }}</button>
        <p class="login-footnote">已有账号？<router-link to="/login">返回登录</router-link></p>
      </form>
    </section>
  </main>
</template>
