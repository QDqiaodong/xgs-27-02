import axios from './axios'
import type { ScriptAssignmentResponse, AssignScriptRequest } from '@/types'

export const getAssignments = (params?: { scriptId?: number; judgeId?: number }): Promise<ScriptAssignmentResponse[]> => {
  return axios.get('/assignments', { params }) as Promise<ScriptAssignmentResponse[]>
}

export const assignScript = (data: AssignScriptRequest): Promise<ScriptAssignmentResponse[]> => {
  return axios.post('/assignments', data) as Promise<ScriptAssignmentResponse[]>
}

export const getAssignmentsByScriptId = (scriptId: number): Promise<ScriptAssignmentResponse[]> => {
  // 走带角色过滤的列表端点：评委只能取到本人在此脚本上的分配
  return axios.get('/assignments', { params: { scriptId } }) as Promise<ScriptAssignmentResponse[]>
}

export const getAssignmentsByJudgeId = (judgeId: number): Promise<ScriptAssignmentResponse[]> => {
  return axios.get('/assignments', { params: { judgeId } }) as Promise<ScriptAssignmentResponse[]>
}