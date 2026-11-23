import type {
  ClaimAction,
  ClaimDetail,
  ClaimStatus,
  ClaimSummary,
  ClaimTimeline,
  LoginResponse,
  NewClaim,
  Page,
  ProblemDetail,
  StatusCount,
} from './types'

const BASE_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8180'
const TOKEN_KEY = 'claimflow.token'

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with ${problem.status}`)
    this.status = problem.status
    this.problem = problem
  }
}

// Session storage keeps the token out of other tabs and drops it when the browser closes.
export const tokenStore = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
}

let onUnauthorized: () => void = () => {}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

interface RawResponse<T> {
  body: T
  etag: string | null
}

async function request<T>(path: string, init: RequestInit = {}): Promise<RawResponse<T>> {
  const headers = new Headers(init.headers)
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body) headers.set('Content-Type', 'application/json')

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError({ status: 0, title: 'Network error', detail: 'Could not reach the ClaimFlow gateway.' })
  }

  if (response.status === 401 && token) onUnauthorized()
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Partial<ProblemDetail>
    throw new ApiError({ ...problem, status: response.status })
  }
  const body = response.status === 204 ? (undefined as T) : ((await response.json()) as T)
  return { body, etag: response.headers.get('ETag') }
}

const get = async <T>(path: string) => (await request<T>(path)).body

export interface ClaimQuery {
  status?: ClaimStatus[]
  q?: string
  page?: number
  size?: number
  sort?: string
}

function toSearch(query: ClaimQuery) {
  const params = new URLSearchParams()
  query.status?.forEach((s) => params.append('status', s))
  if (query.q) params.set('q', query.q)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 10))
  params.set('sort', query.sort ?? 'createdAt,desc')
  return params.toString()
}

export const api = {
  login: async (username: string, password: string) =>
    (await request<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })).body,

  claims: (query: ClaimQuery) => get<Page<ClaimSummary>>(`/api/claims?${toSearch(query)}`),

  claim: (id: string) => get<ClaimDetail>(`/api/claims/${id}`),

  stats: () => get<StatusCount[]>('/api/claims/stats'),

  timeline: (id: string) => get<ClaimTimeline>(`/api/audit/claims/${id}/timeline`),

  submitClaim: async (claim: NewClaim) =>
    (await request<ClaimDetail>('/api/claims', { method: 'POST', body: JSON.stringify(claim) })).body,

  act: async (claim: Pick<ClaimDetail, 'id' | 'version'>, action: ClaimAction, note?: string) =>
    (
      await request<ClaimDetail>(`/api/claims/${claim.id}/actions`, {
        method: 'POST',
        headers: { 'If-Match': `"${claim.version}"` },
        body: JSON.stringify({ action, note: note || undefined }),
      })
    ).body,
}
