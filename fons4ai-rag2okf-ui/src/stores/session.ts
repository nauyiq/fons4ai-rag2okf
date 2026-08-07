import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getCurrentUser, login, logout, type LoginInput, type RegisterInput, type UserProfile, type UserProfileInput, register, updateCurrentUser } from '../api/auth'
import { setAuthenticationToken, setUnauthorizedHandler } from '../api/http'
import { useWorkspaceStore } from './workspace'

/** True only while the current browser runtime holds a successful login token. */
export const hasRuntimeSession = ref(false)

/** Initialize runtime session from localStorage on module load. */
function restoreSession(): void {
  const stored = localStorage.getItem('rag2okf_auth_token')
  if (stored) {
    hasRuntimeSession.value = true
  }
}

restoreSession()

function syncWorkspaceFromProfile(profile: UserProfile): void {
  if (profile.workspaceKey) {
    const workspaceStore = useWorkspaceStore()
    workspaceStore.setWorkspace({
      key: profile.workspaceKey,
      name: profile.workspaceName || '个人工作空间',
      role: profile.workspaceRole === 'ADMIN' ? 'ADMIN' : 'KNOWLEDGE_USER',
    })
  }
}

export const useSessionStore = defineStore('session', () => {
  const profile = ref<UserProfile>()
  const loadingProfile = ref(false)
  const isAuthenticated = computed(() => hasRuntimeSession.value)

  function clearSession(): void {
    profile.value = undefined
    hasRuntimeSession.value = false
    setAuthenticationToken()
  }

  async function signIn(input: LoginInput): Promise<void> {
    const result = await login(input)
    setAuthenticationToken(result.token)
    hasRuntimeSession.value = true
    try {
      profile.value = await getCurrentUser()
      syncWorkspaceFromProfile(profile.value)
    } catch (error) {
      clearSession()
      throw error
    }
  }

  async function signUp(input: RegisterInput): Promise<void> {
    const result = await register(input)
    setAuthenticationToken(result.token)
    hasRuntimeSession.value = true
    try {
      profile.value = await getCurrentUser()
      syncWorkspaceFromProfile(profile.value)
    } catch (error) {
      clearSession()
      throw error
    }
  }

  async function loadProfile(): Promise<void> {
    if (!hasRuntimeSession.value) return
    loadingProfile.value = true
    try {
      profile.value = await getCurrentUser()
      syncWorkspaceFromProfile(profile.value)
    } finally {
      loadingProfile.value = false
    }
  }

  async function saveProfile(input: UserProfileInput): Promise<UserProfile> {
    const saved = await updateCurrentUser(input)
    profile.value = saved
    return saved
  }

  async function signOut(): Promise<void> {
    try {
      if (hasRuntimeSession.value) await logout()
    } finally {
      clearSession()
    }
  }

  function configureExpiryHandler(handler: () => void): void {
    setUnauthorizedHandler(() => {
      clearSession()
      handler()
    })
  }

  return { profile, loadingProfile, isAuthenticated, clearSession, signIn, signUp, signOut, loadProfile, saveProfile, configureExpiryHandler }
})
