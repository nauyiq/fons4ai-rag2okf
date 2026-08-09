import { createRouter, createWebHistory } from 'vue-router'

import KnowledgeBaseListView from '../views/knowledge-bases/KnowledgeBaseListView.vue'
import KnowledgeBaseSettingsView from '../views/knowledge-bases/KnowledgeBaseSettingsView.vue'
import DocumentsView from '../views/documents/DocumentsView.vue'
import DocumentDetailView from '../views/documents/DocumentDetailView.vue'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import SettingsCenterView from '../views/settings/SettingsCenterView.vue'
import ProfileTab from '../views/settings/ProfileTab.vue'
import ModelSettingsTab from '../views/settings/ModelSettingsTab.vue'
import { hasRuntimeSession } from '../stores/session'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'knowledge-bases' } },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/profile', redirect: '/settings/profile' },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsCenterView,
      meta: { requiresAuth: true, sectionLabel: '设置' },
      children: [
        {
          path: '',
          redirect: { name: 'settings-profile' },
        },
        { path: 'profile', name: 'settings-profile', component: ProfileTab, meta: { sectionLabel: '设置 / 个人信息' } },
        { path: 'models', name: 'settings-models', component: ModelSettingsTab, meta: { sectionLabel: '设置 / 模型设置' } },
      ],
    },
    { path: '/knowledge-bases', name: 'knowledge-bases', component: KnowledgeBaseListView, meta: { requiresAuth: true, sectionLabel: '全部知识库' } },
    { path: '/knowledge-bases/:knowledgeBaseKey/settings', name: 'knowledge-base-settings', component: KnowledgeBaseSettingsView, meta: { requiresAuth: true, sectionLabel: '知识库设置' } },
    { path: '/knowledge-bases/:knowledgeBaseKey/documents', name: 'documents', component: DocumentsView, meta: { requiresAuth: true, sectionLabel: '文档工作台' } },
    { path: '/knowledge-bases/:knowledgeBaseKey/documents/:documentKey', name: 'document-detail', component: DocumentDetailView, meta: { requiresAuth: true, sectionLabel: '文档详情' } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !hasRuntimeSession.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && hasRuntimeSession.value) {
    return { name: 'knowledge-bases' }
  }
  return true
})
