import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState, ErrorNotice, Spinner } from '../components/States'
import { api } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth-context'
import { formatDate, formatMoney, relativeTime, statusLabel } from '../lib/format'
import type { ClaimStatus } from '../lib/types'

const ALL_STATUSES: ClaimStatus[] = ['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'DENIED', 'PAID']
const QUEUE: ClaimStatus[] = ['SUBMITTED', 'IN_REVIEW']

export function ClaimsPage() {
  const { user } = useAuth()
  const staff = isStaff(user)
  const navigate = useNavigate()
  const [statuses, setStatuses] = useState<ClaimStatus[]>(staff ? QUEUE : [])
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const claims = useQuery({
    queryKey: ['claims', statuses, search, page],
    queryFn: () => api.claims({ status: statuses, q: search.trim() || undefined, page, size: 10 }),
    placeholderData: keepPreviousData,
  })
  const stats = useQuery({ queryKey: ['stats'], queryFn: api.stats, enabled: staff })

  function toggle(status: ClaimStatus) {
    setPage(0)
    setStatuses((current) => (current.includes(status) ? current.filter((s) => s !== status) : [...current, status]))
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{staff ? 'Review queue' : 'My claims'}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {staff ? 'Claims waiting for a decision come first. Filter to see the rest.' : 'Everything you have submitted and where it stands.'}
          </p>
        </div>
        <Link to="/claims/new" className="btn-primary">New claim</Link>
      </div>

      {staff && stats.data && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {ALL_STATUSES.map((status) => {
            const s = stats.data.find((c) => c.status === status)
            return (
              <div key={status} className="card px-4 py-3">
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{statusLabel[status]}</p>
                <p className="mt-1 text-2xl font-semibold tabular-nums">{s?.count ?? 0}</p>
                <p className="text-xs text-slate-500 tabular-nums dark:text-slate-400">{formatMoney(s?.totalBilled ?? 0)} billed</p>
              </div>
            )
          })}
        </div>
      )}

      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-4 dark:border-slate-800">
          <input
            className="input max-w-xs"
            placeholder="Claim number, provider or member"
            value={search}
            onChange={(e) => {
              setPage(0)
              setSearch(e.target.value)
            }}
            aria-label="Search claims"
          />
          <div className="flex flex-wrap gap-1.5">
            {ALL_STATUSES.map((status) => (
              <button
                key={status}
                type="button"
                onClick={() => toggle(status)}
                aria-pressed={statuses.includes(status)}
                className={`rounded-full px-3 py-1 text-xs font-medium ring-1 transition ring-inset ${
                  statuses.includes(status)
                    ? 'bg-brand-700 text-white ring-brand-700'
                    : 'text-slate-600 ring-slate-300 hover:bg-slate-50 dark:text-slate-300 dark:ring-slate-700 dark:hover:bg-slate-800'
                }`}
              >
                {statusLabel[status]}
              </button>
            ))}
          </div>
        </div>

        {claims.isPending && <Spinner label="Loading claims" />}
        {claims.isError && <div className="p-4"><ErrorNotice error={claims.error} onRetry={() => void claims.refetch()} /></div>}
        {claims.data && claims.data.content.length === 0 && (
          <EmptyState title="No claims match">
            {staff ? 'Try clearing the status filters.' : <Link to="/claims/new" className="text-brand-700 underline">Submit your first claim</Link>}
          </EmptyState>
        )}
        {claims.data && claims.data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-slate-500 uppercase dark:text-slate-400">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Claim</th>
                  {staff && <th className="px-4 py-2.5 font-medium">Member</th>}
                  <th className="px-4 py-2.5 font-medium">Provider</th>
                  <th className="px-4 py-2.5 font-medium">Service date</th>
                  <th className="px-4 py-2.5 text-right font-medium">Billed</th>
                  <th className="px-4 py-2.5 font-medium">Status</th>
                  <th className="px-4 py-2.5 font-medium">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {claims.data.content.map((claim) => (
                  <tr key={claim.id} onClick={() => navigate(`/claims/${claim.id}`)} className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/50">
                    <td className="px-4 py-3 font-mono text-xs">
                      <Link to={`/claims/${claim.id}`} className="hover:underline" onClick={(e) => e.stopPropagation()}>
                        {claim.claimNumber}
                      </Link>
                    </td>
                    {staff && (
                      <td className="px-4 py-3">
                        <p className="font-medium">{claim.memberName}</p>
                        <p className="text-xs text-slate-500 dark:text-slate-400">{claim.memberNumber}</p>
                      </td>
                    )}
                    <td className="px-4 py-3">{claim.providerName}</td>
                    <td className="px-4 py-3 whitespace-nowrap">{formatDate(claim.serviceDate)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(claim.totalBilled)}</td>
                    <td className="px-4 py-3"><StatusBadge status={claim.status} /></td>
                    <td className="px-4 py-3 whitespace-nowrap text-slate-500 dark:text-slate-400">{relativeTime(claim.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {claims.data && claims.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-slate-800">
            <span className="text-slate-500 dark:text-slate-400">
              Page {claims.data.page + 1} of {claims.data.totalPages} &middot; {claims.data.totalElements} claims
            </span>
            <div className="flex gap-2">
              <button type="button" className="btn-secondary px-2.5 py-1" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
              <button type="button" className="btn-secondary px-2.5 py-1" disabled={page + 1 >= claims.data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
