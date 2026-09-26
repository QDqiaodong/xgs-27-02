import axios from './axios'
import type { AuditLogResponse } from '@/types'

export const getLogs = (params?: { page?: number; size?: number; module?: string; success?: boolean }): Promise<{ content: AuditLogResponse[] }> => {
  return axios.get('/logs', { params }) as Promise<{ content: AuditLogResponse[] }>
}

export const getLogById = (id: number): Promise<AuditLogResponse> => {
  return axios.get(`/logs/${id}`) as Promise<AuditLogResponse>
}