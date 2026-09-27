export interface ScriptResponse {
  id: number
  title: string
  genre: string
  duration: number
  targetAudience: string
  description: string
  attachmentNote?: string | null
  status: 'PENDING' | 'REVIEWING' | 'PENDING_SUPPLEMENT' | 'GRADED'
  grade: string | null
  totalScore: number | null
  validScoreCount?: number
  /** 恒为 true：所有角色拿到的脚本响应都是脱敏视图 */
  masked?: boolean
  /** 待管理员核对的可疑身份数量（评委侧这些片段已被更保守地隐藏） */
  pendingCount?: number
  createdAt: string
  updatedAt: string
}

export interface CreateScriptRequest {
  title: string
  genre: string
  duration: number
  targetAudience: string
  description?: string
  author?: string
  source?: string
  contactInfo?: string
  attachmentNote?: string
}

/** 管理员受控原稿视图：仅 /scripts/{id}/raw（ADMIN）返回 */
export interface ScriptRawResponse {
  id: number
  title: string
  genre: string
  duration: number
  targetAudience: string
  author?: string | null
  source?: string | null
  contactInfo?: string | null
  description?: string | null
  attachmentNote?: string | null
  status: string
  grade: string | null
  totalScore: number | null
  createdAt: string
  updatedAt: string
}

export interface IdentityClueView {
  id: number
  groupId: number
  fieldName: string
  fieldLabel: string
  startOffset: number
  endOffset: number
  matchedText: string
  reason: string
  reasonLabel: string
}

export interface IdentityGroupView {
  id: number
  clueType: string
  clueTypeLabel: string
  labelLetter: string
  code: string
  status: 'PENDING' | 'CONFIRMED_IDENTITY' | 'CONFIRMED_CHARACTER'
  statusLabel: string
  representative: string
  detectReason?: string | null
  detectReasonLabel?: string | null
  clueCount: number
  clues: IdentityClueView[]
  decidedByName?: string | null
  decidedAt?: string | null
}

export interface IdentityMapAuditView {
  id: number
  action: string
  actionLabel: string
  viewerName: string | null
  ipAddress: string | null
  detail: string | null
  createdAt: string
}

export interface IdentityMapResponse {
  scriptId: number
  scriptSalt: string
  raw: {
    title: string
    author?: string | null
    source?: string | null
    contactInfo?: string | null
    description?: string | null
    attachmentNote?: string | null
  }
  groups: IdentityGroupView[]
  masked: {
    title: string
    description?: string | null
    attachmentNote?: string | null
    authorCode?: string | null
    sourceCode?: string | null
  }
  recentAudits: IdentityMapAuditView[]
  pendingCount: number
}

export interface JudgeResponse {
  id: number
  name: string
  role: string
  userId?: number
  expertise: string
  status: string
  createdAt: string
}

export interface CreateJudgeRequest {
  name: string
  role?: string
  expertise?: string
}

export interface ScoreResponse {
  id: number
  scriptId: number
  judgeId: number
  judgeName: string
  creativityScore: number
  commercialScore: number
  difficultyScore: number
  logicScore: number
  audienceScore: number
  comment: string
  isExtreme: boolean
  valid: boolean
  invalidatedAt?: string | null
  invalidatedReason?: string | null
  createdAt: string
}

export interface CreateScoreRequest {
  scriptId: number
  judgeId: number
  creativityScore: number
  commercialScore: number
  difficultyScore: number
  logicScore: number
  audienceScore: number
  comment?: string
}

export interface GradeDistributionResponse {
  grade: string
  count: number
  percentage: number
}

export interface LowScoreCommentResponse {
  scriptId: number
  scriptTitle: string
  totalScore: number
  grade: string
  comments: {
    judgeName: string
    comment: string
    createdAt: string
  }[]
}

export interface UserResponse {
  id: number
  username: string
  name: string
  role: string
  email: string
  phone: string
  status: string
  createdAt: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserResponse
}

export interface CreateUserRequest {
  username: string
  password: string
  name: string
  role?: string
  email?: string
  phone?: string
}

export interface ScriptAssignmentResponse {
  id: number
  scriptId: number
  scriptTitle: string
  judgeId: number
  judgeName: string
  assignedBy: number
  assignedByName: string
  status: 'ASSIGNED' | 'COMPLETED' | 'RECUSED' | string
  deadline: string | null
  assignedAt: string | null
  recusedAt?: string | null
  recusalApplicationId?: number | null
  replacesAssignmentId?: number | null
}

export interface AssignScriptRequest {
  scriptId: number
  judgeIds: number[]
  deadline?: string
}

export type RecusalType = 'AUTHOR' | 'ORGANIZATION' | 'TOPIC' | 'OTHER'
export type RecusalStatus = 'PENDING' | 'SUPPLEMENT_REQUESTED' | 'APPROVED' | 'REJECTED'
export type RecusalDecision = 'APPROVE' | 'REJECT' | 'REQUEST_SUPPLEMENT'

export interface CreateRecusalRequest {
  scriptId: number
  judgeId: number
  type: RecusalType
  reason: string
  evidence: string
}

export interface RecusalApplicationResponse {
  id: number
  scriptId: number
  scriptTitle: string
  judgeId: number
  judgeName: string
  assignmentId: number
  type: RecusalType
  status: RecusalStatus
  submittedBy: number
  submittedByName: string
  submittedByRole: string
  submittedOnBehalf: boolean
  decidedBy: number | null
  decidedByName: string | null
  decidedAt: string | null
  createdAt: string
  updatedAt: string
  reason?: string
  evidence?: string
  supplementInfo?: string
  decisionComment?: string
  canViewSensitive: boolean
  substituteAssignmentId?: number | null
  substituteJudgeId?: number | null
  substituteJudgeName?: string | null
}

export interface RecusalReviewLogResponse {
  id: number
  applicationId: number
  action: string
  operatorId: number | null
  operatorName: string | null
  fromStatus: string | null
  toStatus: string | null
  comment: string | null
  createdAt: string
}

export interface SubstituteCandidateResponse {
  judgeId: number
  judgeName: string
  expertise: string
  expertiseMatched: boolean
}

export interface AuditLogResponse {
  id: number
  action: string
  module: string
  targetId: number | null
  userId: number | null
  userName: string | null
  ipAddress: string | null
  requestParams: string | null
  responseResult: string | null
  success: boolean
  errorMessage: string | null
  createdAt: string
}