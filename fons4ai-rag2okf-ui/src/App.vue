<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { theme as antdTheme } from 'ant-design-vue'

import Rag2OkfAppShell from './layouts/Rag2OkfAppShell.vue'
import { useSessionStore } from './stores/session'
import { setAuthenticationToken } from './api/http'
import { isDemoMode } from './composables/useDataSource'
import { useTheme } from './composables/useTheme'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const isPublicPage = computed(() => route.meta.public === true)
const appReady = ref(false)

const { activeTheme } = useTheme()

/**
 * antd ConfigProvider 主题配置：由 useTheme 的 activeTheme 驱动，
 * 切换 darkAlgorithm / defaultAlgorithm，并将 tokens.css 的关键 CSS 变量
 * 映射到 antd token，使 antd 组件视觉与现有设计保持一致。
 * tokens.css 变量继续驱动布局类与自定义元素，antd token 仅驱动 antd 组件。
 */
const antdThemeConfig = computed(() => {
  const isDark = activeTheme.value === 'dark'
  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: isDark
      ? {
          // 暗色下用浅色（--ink）作为主色，保证黑底可见；与 tokens.css 的 --ink 一致
          colorPrimary: '#e8edf7',
          colorText: '#e8edf7',
          colorTextSecondary: '#a9b7ce',
          colorBorder: '#213650',
          colorBgContainer: '#0c1828',
          colorBgLayout: '#07111f',
          colorError: '#ff7777',
          colorSuccess: '#23c9ae',
          colorWarning: '#ff9b52',
          borderRadius: 10,
        }
      : {
          // 亮色下用近黑色（#262626）作为主色，统一按钮/选择框/输入框聚焦边框为黑色系
          colorPrimary: '#262626',
          colorText: '#111827',
          colorTextSecondary: '#52617a',
          colorBorder: '#d9e1ef',
          colorBgContainer: '#ffffff',
          colorBgLayout: '#f5f7fb',
          colorError: '#d54848',
          colorSuccess: '#00a88f',
          colorWarning: '#e9771f',
          borderRadius: 10,
        },
  }
})

sessionStore.configureExpiryHandler(() => {
  if (route.name !== 'login') router.replace({ name: 'login', query: { expired: '1' } })
})

/**
 * 页面加载时恢复会话（刷新页面场景）；主题由 useTheme 从 localStorage 自动应用。
 * demo 模式下自动注入演示 token 并加载 profile，免登录直接查看页面效果。
 */
onMounted(async () => {
  // demo 模式自动初始化会话，方便预览
  if (isDemoMode() && !sessionStore.isAuthenticated) {
    setAuthenticationToken('demo-token-auto')
    try {
      await sessionStore.loadProfile()
    } catch { /* demo 模式下 profile 加载失败时仍保持会话 */ }
  }
  if (sessionStore.isAuthenticated && !sessionStore.profile) {
    try {
      await sessionStore.loadProfile()
    } catch { /* profile 加载失败时保持默认会话状态 */ }
  }
  appReady.value = true
})
</script>

<template>
  <a-config-provider :theme="antdThemeConfig">
    <template v-if="!appReady && !isPublicPage" />
    <RouterView v-else-if="isPublicPage" />
    <Rag2OkfAppShell v-else><RouterView /></Rag2OkfAppShell>
  </a-config-provider>
</template>
