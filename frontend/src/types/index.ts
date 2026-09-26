export interface ScriptResponse {
  id: number
  title: string
  genre: string
  duration: number
  targetAudience: string
  description: string
  author?: string
  source?: string
  status: 'PENDING' | 'REVIEWING' | 'PENDING_SUPPLEMENT' | 'GRADED'
  grade: string | null
  totalScore: number | null
  validScoreCount?: number
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