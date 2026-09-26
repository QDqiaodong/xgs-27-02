import axios from './axios'
import type { ScriptResponse, CreateScriptRequest } from '@/types'

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

export const createScript = (data: CreateScriptRequest): Promise<ScriptResponse> => {
  return axios.post('/scripts', data) as Promise<ScriptResponse>
}

export const updateScript = (id: number, data: CreateScriptRequest): Promise<ScriptResponse> => {
  return axios.put(`/scripts/${id}`, data) as Promise<ScriptResponse>
}

export const deleteScript = (id: number): Promise<void> => {
  return axios.delete(`/scripts/${id}`) as Promise<void>
}