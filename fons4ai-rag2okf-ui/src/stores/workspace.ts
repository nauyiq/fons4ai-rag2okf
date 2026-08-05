import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type WorkspaceRole = 'KNOWLEDGE_USER' | 'ADMIN'

export interface WorkspaceContext {
  key: string
  name: string
  role: WorkspaceRole
}

const defaultWorkspace: WorkspaceContext = {
  key: import.meta.env.VITE_RAG2OKF_WORKSPACE_KEY ?? '',
  name: import.meta.env.VITE_RAG2OKF_WORKSPACE_NAME ?? '未选择工作空间',
  role: import.meta.env.VITE_RAG2OKF_WORKSPACE_ROLE === 'ADMIN' ? 'ADMIN' : 'KNOWLEDGE_USER',
}

/**
 * UI-only workspace context. Server APIs remain the source of truth for
 * membership and write authorization; this store only controls presentation.
 */
export const useWorkspaceStore = defineStore('workspace', () => {
  const currentWorkspace = ref<WorkspaceContext>({ ...defaultWorkspace })
  const canManage = computed(() => currentWorkspace.value.role === 'ADMIN')

  function setWorkspace(workspace: WorkspaceContext): void {
    currentWorkspace.value = workspace
  }

  return { currentWorkspace, canManage, setWorkspace }
})
