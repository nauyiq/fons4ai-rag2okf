<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'

import { ApiRequestError } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()
const submitting = ref(false)
const form = reactive({ email: '', password: '', rememberMe: false })

const rules: Record<string, Rule[]> = {
  email: [
    { required: true, message: '请输入邮箱。', trigger: 'change' },
    { type: 'email', message: '请输入有效的邮箱地址。', trigger: 'change' },
  ],
  password: [{ required: true, message: '请输入密码。', trigger: 'change' }],
}

function normalizeRedirect(): string {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/knowledge-bases'
}

async function submit(): Promise<void> {
  submitting.value = true
  try {
    await sessionStore.signIn({ email: form.email.trim(), password: form.password, rememberMe: form.rememberMe })
    message.success('登录成功，正在进入知识空间…')
    await router.replace(normalizeRedirect())
  } catch (error) {
    message.error(error instanceof ApiRequestError && error.status === 429
      ? '登录尝试过于频繁，请稍后再试。'
      : '邮箱或密码不正确。')
  } finally {
    submitting.value = false
    form.password = ''
  }
}

onMounted(() => {
  if (route.query.expired === '1') {
    message.warning('会话已结束，请重新登录。')
  }
})
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
        class="login-card"
        layout="vertical"
        :model="form"
        :rules="rules"
        @finish="submit"
      >
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录你的知识空间</h2>
        <p class="login-lead">使用邮箱与密码继续管理你的知识库。</p>
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
            autocomplete="current-password"
            placeholder="输入密码"
          />
        </a-form-item>
        <a-form-item name="rememberMe">
          <a-checkbox v-model:checked="form.rememberMe">
            <span class="remember-text">
              <strong>保持登录</strong>
              <small>在这台设备上延长安全会话</small>
            </span>
          </a-checkbox>
        </a-form-item>
        <a-button type="primary" html-type="submit" class="login-submit" :loading="submitting">
          进入知识空间
        </a-button>
        <p class="login-footnote">还没有账号？<router-link to="/register">注册新账号</router-link></p>
      </a-form>
    </section>
  </main>
</template>

<style scoped>
:deep(.ant-form-item) {
  margin-bottom: 0;
}

.remember-text {
  display: inline-flex;
  flex-direction: column;
}

.remember-text small {
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
