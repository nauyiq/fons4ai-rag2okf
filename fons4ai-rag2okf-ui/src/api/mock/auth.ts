/**
 * 认证演示数据。
 * 忠实模拟真实接口响应结构（UserProfile、登录返回 token）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type { UserProfile, LoginInput, RegisterInput, UserProfileInput } from '../auth'

/** demo 模式固定用户。workspaceKey='ws-demo' 与 mock 知识库对齐。 */
const demoUser: UserProfile = {
  userKey: 'user-demo-001',
  email: 'demo@rag2okf.cn',
  displayName: '演示管理员',
  avatarUrl: '',
  preferenceJson: '{}',
  workspaceKey: 'ws-demo',
  workspaceName: '演示工作空间',
  workspaceRole: 'ADMIN',
}

/** 模拟登录（任意邮箱密码均可，返回固定 token）。 */
export function mockLogin(_input: LoginInput): { token: string } {
  return { token: 'demo-token-' + Date.now() }
}

/** 模拟注册。 */
export function mockRegister(input: RegisterInput): { token: string } {
  demoUser.displayName = input.displayName || '演示用户'
  demoUser.email = input.email
  return { token: 'demo-token-' + Date.now() }
}

/** 模拟登出。 */
export function mockLogout(): void {
  // no-op
}

/** 模拟获取当前用户信息。 */
export function mockGetCurrentUser(): UserProfile {
  return { ...demoUser }
}

/** 模拟更新当前用户信息。 */
export function mockUpdateCurrentUser(input: UserProfileInput): UserProfile {
  demoUser.displayName = input.displayName || demoUser.displayName
  demoUser.avatarUrl = input.avatarUrl || demoUser.avatarUrl
  demoUser.preferenceJson = input.preferenceJson || demoUser.preferenceJson
  return { ...demoUser }
}
