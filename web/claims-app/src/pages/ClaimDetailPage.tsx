import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState, ErrorNotice, Spinner } from '../components/States'
import { Timeline } from '../components/Timeline'
import { ApiError, api } from '../lib/api'
import { actionLabel, formatDate, formatDateTime, formatMoney } from '../lib/format'
import type { ClaimAction, ClaimDetail } from '../lib/types'

export function ClaimDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const claim = useQuery({ queryKey: ['claim', id], queryFn: () => api.claim(id) })

  const version = claim.data?.version
  // Audit events arrive through Kafka a moment after the change, so poll briefly until they catch up.
  const timeline = useQuery({
    queryKey: ['timeline', id],
    queryFn: () => api.timeline(id),
    refetchInterval: (query) => {
      const latest = query.state.data?.entries.at(-1)?.claimVersion
      return version !== undefined && latest !== version ? 1000 : false
    },
  })

  if (claim.isPending) return <Spinner label="Loading claim" />
  if (claim.isError) {
    return (
      <div className="space-y-4">
        <Link to="/claims" className="text-sm text-slate-500 hover:underline">Back to claims</Link>
        <ErrorNotice error={claim.error} onRetry={() => void claim.refetch()} />
      </div>
    )
  }

  const c = claim.data
  const onChanged = (updated: ClaimDetail) => {
    queryClient.setQueryData(['claim', id], updated)
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    void queryClient.invalidateQueries({ queryKey: ['stats'] })
    void queryClient.invalidateQueries({ queryKey: ['timeline', id] })
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/claims" className="text-sm text-slate-500 hover:underline dark:text-slate-400">Back to claims</Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="font-mono text-2xl font-semibold tracking-tight">{c.claimNumber}</h1>
          <StatusBadge status={c.status} />
        </div>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          {c.memberName} ({c.memberNumber}) &middot; {c.planCode} &middot; submitted by {c.submittedBy} {formatDateTime(c.createdAt)}
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card grid grid-cols-2 gap-x-6 gap-y-4 p-6 sm:grid-cols-4">
            <Field label="Provider" value={c.providerName} sub={`NPI ${c.providerNpi}`} />
            <Field label="Service date" value={formatDate(c.serviceDate)} />
            <Field label="Diagnosis" value={c.diagnosisCode} mono />
            <Field label="Coinsurance" value={`${c.coinsurancePercent}%`} />
            <Field label="Billed" value={formatMoney(c.totalBilled)} />
            <Field label="Plan pays" value={formatMoney(c.payableAmount)} />
            <Field label="Member owes" value={formatMoney(c.memberResponsibility)} />
            <Field label="Assigned to" value={c.assignedTo ?? 'Unassigned'} />
          </section>

          <section className="card overflow-hidden">
            <h2 className="border-b border-slate-200 px-6 py-4 font-medium dark:border-slate-800">Service lines</h2>
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-slate-500 uppercase dark:text-slate-400">
                <tr>
                  <th className="px-6 py-2 font-medium">#</th>
                  <th className="px-6 py-2 font-medium">CPT</th>
                  <th className="px-6 py-2 font-medium">Description</th>
                  <th className="px-6 py-2 text-right font-medium">Units</th>
                  <th className="px-6 py-2 text-right font-medium">Charge</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {c.lines.map((line) => (
                  <tr key={line.lineNumber}>
                    <td className="px-6 py-2.5 text-slate-500">{line.lineNumber}</td>
                    <td className="px-6 py-2.5 font-mono text-xs">{line.procedureCode}</td>
                    <td className="px-6 py-2.5">{line.description}</td>
                    <td className="px-6 py-2.5 text-right tabular-nums">{line.units}</td>
                    <td className="px-6 py-2.5 text-right tabular-nums">{formatMoney(line.chargeAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          {c.decisionNote && (
            <section className="card p-6">
              <h2 className="label">Decision note</h2>
              <p className="text-sm">{c.decisionNote}</p>
            </section>
          )}

          {c.allowedActions.length > 0 && <ActionPanel claim={c} onChanged={onChanged} />}
        </div>

        <section className="card h-fit p-6">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="font-medium">Audit trail</h2>
            {timeline.isFetching && <span className="size-3 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600" />}
          </div>
          {timeline.isPending && <Spinner label="Loading timeline" />}
          {timeline.isError && <ErrorNotice error={timeline.error} onRetry={() => void timeline.refetch()} />}
          {timeline.data && timeline.data.entries.length === 0 && <EmptyState title="No events yet">They show up a moment after each change.</EmptyState>}
          {timeline.data && timeline.data.entries.length > 0 && <Timeline entries={timeline.data.entries} />}
        </section>
      </div>
    </div>
  )
}

function Field({ label, value, sub, mono }: { label: string; value: string; sub?: string; mono?: boolean }) {
  return (
    <div>
      <p className="label">{label}</p>
      <p className={`text-sm font-medium ${mono ? 'font-mono' : ''}`}>{value}</p>
      {sub && <p className="text-xs text-slate-500 dark:text-slate-400">{sub}</p>}
    </div>
  )
}

function ActionPanel({ claim, onChanged }: { claim: ClaimDetail; onChanged: (c: ClaimDetail) => void }) {
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const act = useMutation({
    mutationFn: (action: ClaimAction) => api.act(claim, action, note),
    onSuccess: (updated) => {
      setNote('')
      onChanged(updated)
    },
  })
  const stale = act.error instanceof ApiError && act.error.status === 412

  return (
    <section className="card p-6">
      <h2 className="font-medium">Decision</h2>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        Approving pays {100 - claim.coinsurancePercent}% of the billed amount under {claim.planCode}. Denials need a reason.
      </p>
      <label htmlFor="note" className="label mt-4">Note</label>
      <textarea id="note" rows={2} maxLength={500} className="input" placeholder="Visible in the audit trail" value={note} onChange={(e) => setNote(e.target.value)} />
      {stale ? (
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          Someone else changed this claim while you had it open.{' '}
          <button type="button" className="font-medium underline" onClick={() => { act.reset(); void queryClient.invalidateQueries({ queryKey: ['claim', claim.id] }) }}>
            Load the latest version
          </button>
        </div>
      ) : (
        act.isError && <div className="mt-4"><ErrorNotice error={act.error} /></div>
      )}
      <div className="mt-4 flex flex-wrap gap-2">
        {claim.allowedActions.map((action) => (
          <button
            key={action}
            type="button"
            disabled={act.isPending || (action === 'DENY' && !note.trim())}
            onClick={() => act.mutate(action)}
            className={action === 'DENY' ? 'btn-danger' : action === 'APPROVE' || action === 'MARK_PAID' ? 'btn-primary' : 'btn-secondary'}
          >
            {actionLabel[action]}
          </button>
        ))}
      </div>
    </section>
  )
}
