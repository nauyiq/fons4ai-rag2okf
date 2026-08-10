<script setup lang="ts">
/**
 * 设置中心外壳（Ragflow 风格左侧导航）。
 *
 * <p>左侧边栏结构：
 *   顶部 — 用户头像 + 展示名 + 邮箱
 *   中部 — 设置分区导航（个人信息、模型设置）
 *   底部 — 版本号、主题切换（与右上角一致）、突出的"登出"按钮
 * <p>右侧 RouterView 渲染当前子路由内容。
 *
 * <p>本视图在 AppShell 的 <slot> 内渲染，因此不再包裹 AppShell。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useTheme } from '../../composables/useTheme'
import { useSessionStore } from '../../stores/session'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const { mode, setTheme } = useTheme()

const APP_VERSION = 'v1.0.0'

/** 左侧导航菜单项：key 与设置中心子路由 path 一一对应。 */
const menuItems = [
  { key: '/settings/profile', label: '个人信息' },
  { key: '/settings/models', label: '模型设置' },
] as const

/** 当前选中菜单项 key：与路由 path 同步。 */
const selectedKeys = computed(() => [route.path])

/** 菜单点击跳转到对应子路由。 */
function onMenuClick(info: { key: string | number }): void {
  void router.push(String(info.key))
}

async function signOut(): Promise<void> {
  await sessionStore.signOut()
  await router.replace({ name: 'login' })
}

function displayName(): string {
  return sessionStore.profile?.displayName || sessionStore.profile?.email || '我的账号'
}

function emailText(): string {
  return sessionStore.profile?.email || '本地知识空间'
}

function avatarInitial(): string {
  return (displayName() || 'U').slice(0, 1).toUpperCase()
}

/** 主题循环切换：light → dark → system → light（与右上角 AppShell 完全一致）。 */
function chooseTheme(): void {
  const sequence = { light: 'dark', dark: 'system', system: 'light' } as const
  setTheme(sequence[mode.value])
}
</script>

<template>
  <section class="settings-page">
    <aside class="settings-sidebar">
      <div class="sidebar-top">
        <div class="user-block">
          <div class="avatar">{{ avatarInitial() }}</div>
          <div class="user-info">
            <strong class="user-name">{{ displayName() }}</strong>
            <span class="user-email">{{ emailText() }}</span>
          </div>
        </div>
      </div>

      <nav class="sidebar-nav">
        <a-menu mode="vertical" :selected-keys="selectedKeys" @click="onMenuClick">
          <a-menu-item v-for="item in menuItems" :key="item.key">{{ item.label }}</a-menu-item>
        </a-menu>
      </nav>

      <div class="sidebar-bottom">
        <div class="bottom-row version-row">
          <span class="app-version">{{ APP_VERSION }}</span>
          <button class="icon-button theme-toggle" type="button" data-test="theme-toggle" :title="`当前主题：${mode}`" :aria-label="`切换主题，当前：${mode}`" @click="chooseTheme">◐</button>
        </div>
        <button class="sign-out-btn" type="button" data-test="sign-out" @click="signOut">登出</button>
      </div>
    </aside>

    <main class="settings-main">
      <RouterView />
    </main>
  </section>
</template>

<style scoped>
.settings-page {
  display: grid;
  grid-template-columns: 286px minmax(0, 1fr);
  align-items: start;
  /* 取消外层 .workspace-main 的内边距，让设置页全幅铺满（侧栏贴左、内容贴右） */
  margin: -30px -28px -48px;
  min-height: calc(100vh - 64px);
  max-width: none;
  background: var(--surface);
}

.settings-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  padding: 28px 24px 20px;
  background: var(--surface);
  border-right: 1px solid var(--line);
  gap: 18px;
  overflow-y: auto;
}

.sidebar-top {
  padding: 0 0 14px;
}

.user-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-block .avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #0f766e;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-email {
  font-size: 12px;
  color: var(--ink-soft);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
}

.sidebar-nav :deep(.ant-menu) {
  border-inline-end: none !important;
  background: transparent;
  color: var(--ink);
}

.sidebar-nav :deep(.ant-menu-item) {
  border-radius: 6px;
  margin: 3px 0;
  height: 40px;
  line-height: 40px;
  padding-inline: 14px !important;
  color: var(--ink);
}

.sidebar-nav :deep(.ant-menu-item:hover) {
  color: var(--ink);
  background: var(--surface-muted);
}

.sidebar-nav :deep(.ant-menu-item-selected) {
  color: var(--ink);
  font-weight: 600;
  background: var(--surface-muted) !important;
}

.sidebar-nav :deep(.ant-menu-item-selected::after) {
  border-inline-end: none;
}

.sidebar-bottom {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 8px 0;
  border-top: 0;
  margin-top: 4px;
  padding-top: 12px;
}

.bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
}

.app-version {
  font-size: 12px;
  color: var(--ink-faint);
}

/* 主题切换按钮复用全局 .icon-button（38x38 圆角 10px），与右上角完全一致 */
.theme-toggle {
  font-size: 16px;
}

.sign-out-btn {
  width: 100%;
  padding: 10px 16px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
  font-size: 14px;
  text-align: center;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.sign-out-btn:hover {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: var(--danger-soft);
}

.settings-main {
  min-width: 0;
  padding: 24px 24px 32px 20px;
  overflow: visible;
  border-left: 1px solid var(--line);
}

@media (max-width: 760px) {
  .settings-page {
    grid-template-columns: 1fr;
    margin: -16px -16px -48px;
  }

  .settings-sidebar {
    position: static;
    height: auto;
    padding: 16px;
    gap: 10px;
    border-right: none;
    border-bottom: 1px solid var(--line);
    overflow: visible;
  }

  .sidebar-top {
    padding-bottom: 0;
  }

  .sidebar-nav {
    flex: none;
  }

  .sidebar-nav :deep(.ant-menu) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .sidebar-nav :deep(.ant-menu-item) {
    margin: 0;
    text-align: center;
  }

  .sidebar-bottom {
    margin-top: 0;
    padding: 0;
  }

  .version-row {
    display: none;
  }

  .sign-out-btn {
    padding-block: 8px;
  }

  .settings-main {
    padding: 20px 16px 32px;
    border-left: 0;
  }
}
</style>
