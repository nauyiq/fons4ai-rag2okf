<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import Rag2OkfAppShell from './layouts/Rag2OkfAppShell.vue'
import { useSessionStore } from './stores/session'
import { useTheme, type ThemeMode } from './composables/useTheme'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const isPublicPage = computed(() => route.meta.public === true)
const { setTheme } = useTheme()
const appReady = ref(false)

sessionStore.configureExpiryHandler(() => {
  if (route.name !== 'login') router.replace({ name: 'login', query: { expired: '1' } })
})

/** 从 profile 的 preferenceJson 中解析并应用主题。 */
function applyThemeFromProfile(): void {
  if (!sessionStore.profile?.preferenceJson) return
  try {
    const parsed = JSON.parse(sessionStore.profile.preferenceJson) as { theme?: string }
    if (['light', 'dark', 'system'].includes(parsed.theme ?? '')) {
      setTheme(parsed.theme as ThemeMode)
    }
  } catch { /* 忽略无效 JSON */ }
}

/** 页面加载时恢复会话和主题（刷新页面场景）。 */
onMounted(async () => {
  if (sessionStore.isAuthenticated && !sessionStore.profile) {
    try {
      await sessionStore.loadProfile()
      applyThemeFromProfile()
    } catch { /* profile 加载失败时保持默认主题 */ }
  }
  appReady.value = true
})

/** 登录/注册成功后也应用主题。 */
watch(() => sessionStore.profile, (profile) => {
  if (profile) applyThemeFromProfile()
})
</script>

<template>
  <template v-if="!appReady && !isPublicPage" />
  <RouterView v-else-if="isPublicPage" />
  <Rag2OkfAppShell v-else><RouterView /></Rag2OkfAppShell>
</template>
