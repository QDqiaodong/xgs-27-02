import axios from './axios'
import type { JudgeResponse, CreateJudgeRequest } from '@/types'

export const getJudges = (params?: { role?: string }): Promise<JudgeResponse[]> => {
  return axios.get('/judges', { params }) as Promise<JudgeResponse[]>
}

export const getMyJudgeProfile = (): Promise<JudgeResponse | null> => {
  return axios.get('/judges/me') as Promise<JudgeResponse | null>
}

export const createJudge = (data: CreateJudgeRequest): Promise<JudgeResponse> => {
  return axios.post('/judges', data) as Promise<JudgeResponse>
}

export const updateJudge = (id: number, data: CreateJudgeRequest): Promise<JudgeResponse> => {
  return axios.put(`/judges/${id}`, data) as Promise<JudgeResponse>
}

export const deleteJudge = (id: number): Promise<void> => {
  return axios.delete(`/judges/${id}`) as Promise<void>
}