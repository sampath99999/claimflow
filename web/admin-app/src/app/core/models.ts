export type PlanType = 'HMO' | 'PPO' | 'EPO' | 'HDHP';
export type MemberStatus = 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  type: PlanType;
  annualDeductible: number;
  coinsurancePercent: number;
  outOfPocketMax: number;
  active: boolean;
  memberCount: number;
  updatedAt: string;
}

export interface PlanInput {
  code?: string;
  name: string;
  type: PlanType;
  annualDeductible: number;
  coinsurancePercent: number;
  outOfPocketMax: number;
  active?: boolean;
}

export interface Member {
  id: string;
  memberNumber: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  email: string | null;
  plan: { id: string; code: string; name: string };
  coverageStart: string;
  coverageEnd: string | null;
  status: MemberStatus;
  updatedAt: string;
}

export interface MemberInput {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  email: string | null;
  planCode: string;
  coverageStart: string;
  coverageEnd: string | null;
  status?: MemberStatus;
}

export interface User {
  username: string;
  displayName: string;
  role: 'MEMBER' | 'ADJUSTER' | 'ADMIN';
  memberId: string | null;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}
