import axios from './axios'
import type {
  ScriptResponse,
  CreateScriptRequest,
  ScriptRawResponse,
  IdentityMapResponse
} from '@/types'

export const getScripts = (params: {
  page?: number
  size?: number
  status?: string
  grade?: string
  genre?: string
}): Promise<{
  content: ScriptResponse[]
  totalElements: number
  totalPages: number
  number: number
}> => {
  return axios.get('/scripts', { params }) as any
}

export const getScriptById = (id: number): Promise<ScriptResponse> => {
  return axios.get(`/scripts/${id}`) as Promise<ScriptResponse>
}

/** 管理员受控原稿：调用会被后端记录到脱敏审计；评委/查看员调用返回 403。 */
export const getRawScript = (id: number): Promise<ScriptRawResponse> => {
  return axios.get(`/scripts/${id}/raw`) as Promise<ScriptRawResponse>
}

/** 管理员代号映射核对视图：原始内容 / 识别线索 / 脱敏结果三区。 */
export const getIdentityMap = (id: number): Promise<IdentityMapResponse> => {
  return axios.get(`/scripts/${id}/identity-map`) as Promise<IdentityMapResponse>
}

/** 管理员核对可疑身份：CONFIRMED_IDENTITY（代号替换）或 CONFIRMED_CHARACTER（剧情人物保留）。 */
export const decideIdentityGroup = (
  scriptId: number,
  groupId: number,
  decision: 'CONFIRMED_IDENTITY' | 'CONFIRMED_CHARACTER'
): Promise<IdentityMapResponse> => {
  return axios.post(`/scripts/${scriptId}/identity-groups/${groupId}/decision`, { decision }) as Promise<IdentityMapResponse>
}

export const createScript = (data: CreateScriptRequest): Promise<ScriptResponse> => {
  return axios.post('/scripts', data) as Promise<ScriptResponse>
}

export const updateScript = (id: number, data: CreateScriptRequest): Promise<ScriptResponse> => {
  return axios.put(`/scripts/${id}`, data) as Promise<ScriptResponse>
}

export const deleteScript = (id: number): Promise<void> => {
  return axios.delete(`/scripts/${id}`) as Promise<void>
}
