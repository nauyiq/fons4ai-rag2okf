import { request } from './http'
import { isDemoMode } from '../composables/useDataSource'
import {
  mockLogin,
  mockRegister,
  mockLogout,
  mockGetCurrentUser,
  mockUpdateCurrentUser,
} from './mock/auth'

export interface UserProfile {
  userKey: string
  email: string
  displayName: string
  avatarUrl: string
  preferenceJson: string
  workspaceKey: string
  workspaceName: string
  workspaceRole: string
}

export interface LoginInput {
  email: string
  password: string
  rememberMe: boolean
}

export interface RegisterInput {
  email: string
  password: string
  confirmPassword: string
  displayName: string
  termsAccepted: boolean
}

export interface UserProfileInput {
  displayName: string
  avatarUrl: string
  preferenceJson: string
}

export function login(input: LoginInput): Promise<{ token: string }> {
  if (isDemoMode()) {
    return Promise.resolve(mockLogin(input))
  }
  return request('/auth/login', { method: 'POST', body: JSON.stringify(input) })
}

export function register(input: RegisterInput): Promise<{ token: string }> {
  if (isDemoMode()) {
    return Promise.resolve(mockRegister(input))
  }
  return request('/auth/registration', { method: 'POST', body: JSON.stringify(input) })
}

export function logout(): Promise<void> {
  if (isDemoMode()) {
    mockLogout()
    return Promise.resolve()
  }
  return request('/auth/logout', { method: 'POST' })
}

export function getCurrentUser(): Promise<UserProfile> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetCurrentUser())
  }
  return request('/users/me')
}

export function updateCurrentUser(input: UserProfileInput): Promise<UserProfile> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateCurrentUser(input))
  }
  return request('/users/me', { method: 'PATCH', body: JSON.stringify(input) })
}
