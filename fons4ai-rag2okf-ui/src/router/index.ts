import { createRouter, createWebHistory } from 'vue-router'

import KnowledgeBaseListView from '../views/knowledge-bases/KnowledgeBaseListView.vue'
import KnowledgeBaseSettingsView from '../views/knowledge-bases/KnowledgeBaseSettingsView.vue'
import DocumentsView from '../views/documents/DocumentsView.vue'
import DocumentDetailView from '../views/documents/DocumentDetailView.vue'
import LoginView from '../views/auth/LoginView.vue'
import ProfileView from '../views/profile/ProfileView.vue'
import PersonalSettingsView from '../views/settings/PersonalSettingsView.vue'
import ModelSettingsView from '../views/settings/ModelSettingsView.vue'
import { hasRuntimeSession } from '../stores/session'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'knowledge-bases' } },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true, sectionLabel: '个人中心' } },
    { path: '/settings/personal', name: 'personal-settings', component: PersonalSettingsView, meta: { requiresAuth: true, sectionLabel: '个人偏好' } },
    { path: '/settings/models', name: 'model-settings', component: ModelSettingsView, meta: { requiresAuth: true, sectionLabel: '模型设置' } },
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
  if (to.name === 'login' && hasRuntimeSession.value) {
    return { name: 'knowledge-bases' }
  }
  return true
})
