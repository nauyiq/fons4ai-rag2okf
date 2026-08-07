<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useTheme } from '../composables/useTheme'
import { useSessionStore } from '../stores/session'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const { mode, setTheme } = useTheme()

/** 面包屑标签：随路由 meta.sectionLabel 动态变化，反映当前页面层级。 */
const sectionLabel = computed(() => (route.meta.sectionLabel as string) ?? '知识库')

/**
 * 顶部导航选中态：知识库相关路由（knowledge-bases/documents/document-detail/knowledge-base-settings）
 * 均高亮"知识库"导航项；logo 首页图标为品牌入口，不参与选中态；搜索/聊天为禁用占位，不参与选中态。
 */
const activeNav = computed(() => {
  const name = route.name as string | undefined
  if (!name) return ''
  if (name.startsWith('knowledge-base') || name.startsWith('document')) return 'knowledge-bases'
  return ''
})

const menuOpen = ref(false)
const accountMenuRef = ref<HTMLElement | null>(null)

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function closeMenu(): void {
  menuOpen.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && menuOpen.value) {
    closeMenu()
  }
}

function onDocumentClick(event: MouseEvent): void {
  if (menuOpen.value && accountMenuRef.value && !accountMenuRef.value.contains(event.target as Node)) {
    closeMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})

/** 主题循环切换：light → dark → system → light。 */
function chooseTheme(): void {
  const sequence = { light: 'dark', dark: 'system', system: 'light' } as const
  setTheme(sequence[mode.value])
}

/** logo 首页图标点击回到知识库列表。 */
function goHome(): void {
  router.push({ name: 'knowledge-bases' })
}

function profileInitial(): string {
  return (sessionStore.profile?.displayName || sessionStore.profile?.email || 'U').slice(0, 1).toUpperCase()
}

async function signOut(): Promise<void> {
  await sessionStore.signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <div class="shell-content">
      <header class="topbar">
        <div class="topbar-left">
          <button class="brand-mark" type="button" data-test="nav-home" aria-label="返回首页" @click="goHome">
            <span></span>
          </button>
          <nav class="topbar-nav" aria-label="主导航">
            <RouterLink class="nav-item" :class="{ active: activeNav === 'knowledge-bases' }" data-test="nav-knowledge-bases" :to="{ name: 'knowledge-bases' }">知识库</RouterLink>
            <button class="nav-item disabled" type="button" data-test="nav-search" disabled title="搜索功能将在后续版本开放">搜索</button>
            <button class="nav-item disabled" type="button" data-test="nav-chat" disabled title="聊天功能将在后续版本开放">聊天</button>
          </nav>
        </div>
        <div class="topbar-actions">
          <div class="breadcrumb" data-test="breadcrumb"><span class="slash">/</span><strong>{{ sectionLabel }}</strong></div>
          <button class="icon-button" type="button" data-test="theme-toggle" :title="`当前主题：${mode}`" :aria-label="`切换主题，当前：${mode}`" @click="chooseTheme">◐</button>
          <div ref="accountMenuRef" class="account-menu" data-test="account-menu">
            <button class="avatar" type="button" aria-label="打开个人中心" aria-haspopup="true" :aria-expanded="menuOpen" @click="toggleMenu">{{ profileInitial() }}<i></i></button>
            <div v-if="menuOpen" class="account-popover" role="menu">
              <strong>{{ sessionStore.profile?.displayName || '我的账号' }}</strong>
              <span>{{ sessionStore.profile?.email || '本地知识空间' }}</span>
              <button type="button" role="menuitem" @click="closeMenu(); router.push({ name: 'profile' })">个人中心</button>
              <button type="button" role="menuitem" @click="closeMenu(); router.push({ name: 'model-settings' })">模型设置</button>
              <button type="button" role="menuitem" @click="closeMenu(); signOut()">退出登录</button>
            </div>
          </div>
        </div>
      </header>
      <main class="workspace-main"><slot /></main>
    </div>
  </div>
</template>

<style scoped>
.account-popover { display: grid; gap: 6px; }
</style>
