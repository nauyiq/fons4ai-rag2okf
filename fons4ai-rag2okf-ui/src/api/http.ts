export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

const apiBaseUrl = (import.meta.env.VITE_RAG2OKF_API_BASE_URL ?? '/knowledge/api/v1').replace(/\/$/, '')
let authenticationToken: string | undefined
let unauthorizedHandler: (() => void) | undefined

/**
 * Keeps the short-lived bearer token only in runtime memory. Authentication
 * pages will populate it in T026; it is intentionally never persisted here.
 */
export function setAuthenticationToken(token?: string): void {
  authenticationToken = token
}

/** Lets the UI return to its own login page when a Bearer session expires. */
export function setUnauthorizedHandler(handler?: () => void): void {
  unauthorizedHandler = handler
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (authenticationToken) {
    headers.set('Authentication', `Bearer ${authenticationToken}`)
  }

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers })
  } catch {
    throw new ApiRequestError('暂时无法连接服务，请检查网络后重试。', 0)
  }

  const envelope = await response.json().catch(() => undefined) as ApiEnvelope<T> | undefined
  if (response.status === 401) {
    authenticationToken = undefined
    unauthorizedHandler?.()
  }
  if (!response.ok || !envelope?.success) {
    throw new ApiRequestError(
      envelope?.message || '请求未能完成，请稍后重试。',
      response.status,
      envelope?.code,
    )
  }
  return envelope.data
}
