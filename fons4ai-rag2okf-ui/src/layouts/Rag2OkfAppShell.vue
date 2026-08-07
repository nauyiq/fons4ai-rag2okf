<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useTheme } from '../composables/useTheme'
import { useWorkspaceStore } from '../stores/workspace'
import { useSessionStore } from '../stores/session'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const sessionStore = useSessionStore()
const { mode, setTheme } = useTheme()

const sectionLabel = computed(() => route.meta.sectionLabel ?? '知识库')
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

function chooseTheme(): void {
  const sequence = { light: 'dark', dark: 'system', system: 'light' } as const
  setTheme(sequence[mode.value])
}

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
    <aside class="evidence-rail" aria-label="知识加工脉络">
      <button class="brand-mark" type="button" aria-label="返回知识库" @click="goHome">
        <span></span>
      </button>
      <nav class="rail-nav" aria-label="主导航">
        <RouterLink class="rail-action" :class="{ active: route.name === 'knowledge-bases' }" :to="{ name: 'knowledge-bases' }" aria-label="知识库">▤</RouterLink>
        <span class="rail-action muted" aria-disabled="true" tabindex="-1" title="文档页面将在后续任务开放">▱</span>
        <span class="rail-action muted" aria-disabled="true" tabindex="-1" title="OKF 页面将在后续任务开放">◇</span>
        <span class="rail-action muted" aria-disabled="true" tabindex="-1" title="知识问答将在后续任务开放">⌁</span>
        <span class="rail-action muted" aria-disabled="true" tabindex="-1" title="评测将在后续任务开放">◌</span>
      </nav>
      <div class="evidence-steps" aria-label="文档到 OKF 的处理过程">
        <span class="evidence-step active" title="源文件"></span>
        <span class="evidence-step" title="解析"></span>
        <span class="evidence-step" title="分块"></span>
        <span class="evidence-step" title="发布"></span>
        <span class="evidence-step success" title="OKF"></span>
      </div>
    </aside>

    <div class="shell-content">
      <header class="topbar">
        <div class="breadcrumb"><span>知识库</span><span class="slash">/</span><strong>{{ sectionLabel }}</strong></div>
        <div class="topbar-actions">
          <label class="global-search" aria-label="全局搜索">
            <span>⌕</span><input placeholder="搜索知识库、文档，或直接提问…" disabled />
            <kbd>⌘ K</kbd>
          </label>
          <button class="icon-button" type="button" :title="`当前主题：${mode}`" :aria-label="`切换主题，当前：${mode}`" @click="chooseTheme">◐</button>
          <div ref="accountMenuRef" class="account-menu"><button class="avatar" type="button" aria-label="打开个人中心" aria-haspopup="true" :aria-expanded="menuOpen" @click="toggleMenu">{{ profileInitial() }}<i></i></button><div v-if="menuOpen" class="account-popover" role="menu"><strong>{{ sessionStore.profile?.displayName || '我的账号' }}</strong><span>{{ sessionStore.profile?.email || '本地知识空间' }}</span><button type="button" role="menuitem" @click="closeMenu(); router.push({ name: 'profile' })">个人中心</button><button type="button" role="menuitem" @click="closeMenu(); router.push({ name: 'personal-settings' })">个人偏好</button><button type="button" role="menuitem" @click="closeMenu(); router.push({ name: 'model-settings' })">模型设置</button><button type="button" role="menuitem" @click="closeMenu(); signOut()">退出登录</button></div></div>
        </div>
      </header>

      <div class="contextbar">
        <button class="workspace-select" type="button" title="工作空间列表接口接入后可切换">
          <span class="workspace-dot"></span>
          <span>{{ workspaceStore.currentWorkspace.name }}</span>
          <small>{{ workspaceStore.currentWorkspace.role === 'ADMIN' ? '管理员' : '知识用户' }}</small>
          <span>⌄</span>
        </button>
        <nav class="context-nav" aria-label="知识库导航">
          <RouterLink :to="{ name: 'knowledge-bases' }">概览</RouterLink>
          <span>文档</span><span>OKF</span><span>知识问答</span><span>评测</span>
          <RouterLink :to="{ name: 'personal-settings' }">设置</RouterLink>
        </nav>
      </div>
      <main class="workspace-main"><slot /></main>
    </div>
  </div>
</template>

<style scoped>
.account-popover { display: grid; gap: 6px; }
</style>
