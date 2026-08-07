import { request } from './http'

export interface ModelProviderTemplateInfo { code: string; providerName: string; defaultBaseUrl: string | null }
export interface ModelConnection { connectionKey: string; providerCode: string; providerName: string; displayName: string; baseUrl: string; apiKeyMask: string; status: string; lastTestStatus: string; lastTestAt: string | null }
export interface ModelProfile { profileKey: string; connectionKey: string; modelName: string; modelType: 'CHAT' | 'EMBEDDING'; dimensions: number | null; timeoutSeconds: number; temperature: number | null; status: string; lastTestStatus: string; lastTestAt: string | null }
export function listModelProviderTemplates(): Promise<ModelProviderTemplateInfo[]> { return request('/model-provider-templates') }
export function listModelConnections(): Promise<ModelConnection[]> { return request('/model-connections') }
export function listModelProfiles(): Promise<ModelProfile[]> { return request('/model-profiles') }
export function createModelConnection(input: { templateCode: string; providerName: string; displayName: string; baseUrl: string; apiKey: string }): Promise<ModelConnection> { return request('/model-connections', { method: 'POST', body: JSON.stringify(input) }) }
export function createModelProfile(input: { connectionKey: string; modelType: 'CHAT' | 'EMBEDDING'; modelName: string; dimensions: number | null; timeoutSeconds: number; temperature: number | null }): Promise<ModelProfile> { return request('/model-profiles', { method: 'POST', body: JSON.stringify(input) }) }
export function testModelProfile(profileKey: string): Promise<{ status: string; errorCode: string | null; dimensions: number | null }> { return request(`/model-profiles/${profileKey}/test`, { method: 'POST' }) }
export function updateModelConnection(connectionKey: string, input: { providerName: string; displayName: string; baseUrl: string; status: string; apiKey: string }): Promise<ModelConnection> { return request(`/model-connections/${connectionKey}`, { method: 'PATCH', body: JSON.stringify(input) }) }
export function updateModelProfile(profileKey: string, input: { modelName: string; dimensions: number | null; timeoutSeconds: number; temperature: number | null; status: string }): Promise<ModelProfile> { return request(`/model-profiles/${profileKey}`, { method: 'PATCH', body: JSON.stringify(input) }) }
