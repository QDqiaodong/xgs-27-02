import axios from './axios'
import type { MaskingMapResponse, ClueType, ClueFieldOrigin } from '@/types'

/** 管理员查看脚本的原稿/线索/脱敏结果映射（每次查看都会写审计记录） */
export const getMaskingMap = (scriptId: number): Promise<MaskingMapResponse> => {
  return axios.get(`/scripts/${scriptId}/masking`) as Promise<MaskingMapResponse>
}

/** 管理员核对线索：decision=IDENTITY 属于投稿身份 / CHARACTER 属于正文角色 */
export const confirmClue = (
  scriptId: number,
  clueId: number,
  decision: 'IDENTITY' | 'CHARACTER',
  identityId?: number
): Promise<MaskingMapResponse> => {
  return axios.post(`/scripts/${scriptId}/masking/clues/${clueId}/confirm`, {
    decision,
    identityId: identityId ?? null
  }) as Promise<MaskingMapResponse>
}

/** 管理员手工补充一条自动识别遗漏的线索 */
export const addClue = (
  scriptId: number,
  data: { clueType: ClueType; fieldOrigin: ClueFieldOrigin; originalText: string; identityId?: number }
): Promise<MaskingMapResponse> => {
  return axios.post(`/scripts/${scriptId}/masking/clues`, data) as Promise<MaskingMapResponse>
}
