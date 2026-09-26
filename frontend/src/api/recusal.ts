import axios from './axios'
import type {
  CreateRecusalRequest,
  RecusalApplicationResponse,
  RecusalDecision,
  RecusalReviewLogResponse,
  SubstituteCandidateResponse
} from '@/types'

export const createRecusal = (data: CreateRecusalRequest): Promise<RecusalApplicationResponse> => {
  return axios.post('/recusals', data) as Promise<RecusalApplicationResponse>
}

export const supplementRecusal = (id: number, supplementInfo: string): Promise<RecusalApplicationResponse> => {
  return axios.post(`/recusals/${id}/supplement`, { supplementInfo }) as Promise<RecusalApplicationResponse>
}

export const decideRecusal = (
  id: number,
  decision: RecusalDecision,
  comment: string
): Promise<RecusalApplicationResponse> => {
  return axios.post(`/recusals/${id}/decision`, { decision, comment }) as Promise<RecusalApplicationResponse>
}

export const assignSubstitute = (
  id: number,
  substituteJudgeId: number,
  deadline?: string
): Promise<unknown> => {
  return axios.post(`/recusals/${id}/substitute`, { substituteJudgeId, deadline })
}

export const getSubstituteCandidates = (id: number): Promise<SubstituteCandidateResponse[]> => {
  return axios.get(`/recusals/${id}/substitute-candidates`) as Promise<SubstituteCandidateResponse[]>
}

export const getAllRecusals = (): Promise<RecusalApplicationResponse[]> => {
  return axios.get('/recusals') as Promise<RecusalApplicationResponse[]>
}

export const getRecusalsByScript = (scriptId: number): Promise<RecusalApplicationResponse[]> => {
  return axios.get(`/recusals/script/${scriptId}`) as Promise<RecusalApplicationResponse[]>
}

export const getMyRecusals = (): Promise<RecusalApplicationResponse[]> => {
  return axios.get('/recusals/mine') as Promise<RecusalApplicationResponse[]>
}

export const getRecusalDetail = (id: number): Promise<RecusalApplicationResponse> => {
  return axios.get(`/recusals/${id}`) as Promise<RecusalApplicationResponse>
}

export const getRecusalLogs = (id: number): Promise<RecusalReviewLogResponse[]> => {
  return axios.get(`/recusals/${id}/logs`) as Promise<RecusalReviewLogResponse[]>
}
