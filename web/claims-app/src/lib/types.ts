export type Role = 'MEMBER' | 'ADJUSTER' | 'ADMIN'

export type ClaimStatus = 'SUBMITTED' | 'IN_REVIEW' | 'APPROVED' | 'DENIED' | 'PAID'

export type ClaimAction = 'START_REVIEW' | 'APPROVE' | 'DENY' | 'MARK_PAID'

export interface User {
  username: string
  displayName: string
  role: Role
  memberId: string | null
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ClaimSummary {
  id: string
  claimNumber: string
  memberId: string
  memberNumber: string
  memberName: string
  providerName: string
  serviceDate: string
  status: ClaimStatus
  totalBilled: number
  payableAmount: number | null
  assignedTo: string | null
  createdAt: string
  updatedAt: string
}

export interface ClaimLine {
  lineNumber: number
  procedureCode: string
  description: string
  units: number
  chargeAmount: number
}

export interface ClaimDetail extends ClaimSummary {
  planCode: string
  coinsurancePercent: number
  providerNpi: string
  diagnosisCode: string
  memberResponsibility: number | null
  decisionNote: string | null
  submittedBy: string
  lines: ClaimLine[]
  allowedActions: ClaimAction[]
  version: number
}

export interface StatusCount {
  status: ClaimStatus
  count: number
  totalBilled: number
}

export interface TimelineEntry {
  eventId: string
  claimId: string
  claimNumber: string
  fromStatus: ClaimStatus | null
  toStatus: ClaimStatus
  actor: string
  actorRole: Role
  note: string | null
  amount: number | null
  claimVersion: number
  occurredAt: string
}

export interface ClaimTimeline {
  claimId: string
  claimNumber: string | null
  events: number
  lastChangeAt: string | null
  entries: TimelineEntry[]
}

export interface Violation {
  code: string
  message: string
}

export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  violations?: Violation[]
  errors?: FieldError[]
}

export interface NewClaimLine {
  procedureCode: string
  description: string
  units: number
  chargeAmount: string
}

export interface NewClaim {
  memberId?: string
  providerNpi: string
  providerName: string
  serviceDate: string
  diagnosisCode: string
  lines: NewClaimLine[]
}
