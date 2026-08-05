import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getCurrentUser, login, logout, type LoginInput, type UserProfile, type UserProfileInput, updateCurrentUser } from '../api/auth'
import { setAuthenticationToken, setUnauthorizedHandler } from '../api/http'

/** True only while the current browser runtime holds a successful login token. */
export const hasRuntimeSession = ref(false)

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

  return { profile, loadingProfile, isAuthenticated, clearSession, signIn, signOut, loadProfile, saveProfile, configureExpiryHandler }
})
