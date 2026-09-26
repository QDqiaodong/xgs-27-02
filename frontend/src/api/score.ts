import axios from './axios'
import type { ScoreResponse, CreateScoreRequest } from '@/types'

export const createScore = (data: CreateScoreRequest): Promise<ScoreResponse> => {
  return axios.post('/scores', data) as Promise<ScoreResponse>
}

export const getScoresByScriptId = (scriptId: number): Promise<ScoreResponse[]> => {
  return axios.get(`/scores/script/${scriptId}`) as Promise<ScoreResponse[]>
}

export const calculateGrade = (scriptId: number): Promise<{
  scriptId: number
  totalScore: number
  grade: string
  dimensionAverages: Record<string, number>
}> => {
  return axios.post(`/scores/grade/${scriptId}`) as any
}