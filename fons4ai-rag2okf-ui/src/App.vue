<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import Rag2OkfAppShell from './layouts/Rag2OkfAppShell.vue'
import { useSessionStore } from './stores/session'
import { setAuthenticationToken } from './api/http'
import { isDemoMode } from './composables/useDataSource'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const isPublicPage = computed(() => route.meta.public === true)
const appReady = ref(false)

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
  <template v-if="!appReady && !isPublicPage" />
  <RouterView v-else-if="isPublicPage" />
  <Rag2OkfAppShell v-else><RouterView /></Rag2OkfAppShell>
</template>
