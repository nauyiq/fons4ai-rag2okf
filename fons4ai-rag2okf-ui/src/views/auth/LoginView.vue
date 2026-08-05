<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()
const submitting = ref(false)
const errorMessage = ref(route.query.expired === '1' ? '会话已结束，请重新登录。' : '')
const form = reactive({ email: '', password: '', rememberMe: false })

function normalizeRedirect(): string {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/knowledge-bases'
}

async function submit(): Promise<void> {
  errorMessage.value = ''
  if (!form.email.trim() || !form.password) {
    errorMessage.value = '请输入邮箱和密码。'
    return
  }
  submitting.value = true
  try {
    await sessionStore.signIn({ email: form.email.trim(), password: form.password, rememberMe: form.rememberMe })
    await router.replace(normalizeRedirect())
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError && error.status === 429
      ? '登录尝试过于频繁，请稍后再试。'
      : '邮箱或密码不正确。'
  } finally {
    submitting.value = false
    form.password = ''
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
        <p class="eyebrow">WELCOME BACK</p><h2>登录你的知识空间</h2><p class="login-lead">使用邮箱与密码继续管理你的知识库。</p>
        <label>邮箱<input v-model="form.email" type="email" inputmode="email" autocomplete="email" maxlength="254" placeholder="name@example.com" /></label>
        <label>密码<input v-model="form.password" type="password" autocomplete="current-password" placeholder="输入密码" /></label>
        <label class="remember-row"><input v-model="form.rememberMe" type="checkbox" /><span><strong>保持登录</strong><small>在这台设备上延长安全会话</small></span></label>
        <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-action login-submit" type="submit" :disabled="submitting">{{ submitting ? '正在进入…' : '进入知识空间' }}</button>
        <p class="login-footnote">新账号注册入口将在下一阶段开放。</p>
      </form>
    </section>
  </main>
</template>
