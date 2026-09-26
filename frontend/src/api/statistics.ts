import axios from './axios'
import type { GradeDistributionResponse, LowScoreCommentResponse } from '@/types'

export const getGradeDistribution = (): Promise<GradeDistributionResponse[]> => {
  return axios.get('/statistics/grade-distribution') as Promise<GradeDistributionResponse[]>
}

export const getLowScoreComments = (): Promise<LowScoreCommentResponse[]> => {
  return axios.get('/statistics/low-score-comments') as Promise<LowScoreCommentResponse[]>
}

export const getStatusStatistics = (): Promise<Record<string, number>> => {
  return axios.get('/statistics/status') as Promise<Record<string, number>>
}