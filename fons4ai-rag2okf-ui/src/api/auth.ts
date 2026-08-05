import { request } from './http'

export interface UserProfile {
  userKey: string
  email: string
  displayName: string
  avatarUrl: string
  preferenceJson: string
}

export interface LoginInput {
  email: string
  password: string
  rememberMe: boolean
}

export interface UserProfileInput {
  displayName: string
  avatarUrl: string
  preferenceJson: string
}

export function login(input: LoginInput): Promise<{ token: string }> {
  return request('/auth/login', { method: 'POST', body: JSON.stringify(input) })
}

export function logout(): Promise<void> {
  return request('/auth/logout', { method: 'POST' })
}

export function getCurrentUser(): Promise<UserProfile> {
  return request('/users/me')
}

export function updateCurrentUser(input: UserProfileInput): Promise<UserProfile> {
  return request('/users/me', { method: 'PATCH', body: JSON.stringify(input) })
}
