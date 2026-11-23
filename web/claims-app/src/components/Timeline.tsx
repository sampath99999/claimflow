import { formatDateTime, formatMoney, relativeTime, statusLabel } from '../lib/format'
import type { ClaimStatus, TimelineEntry } from '../lib/types'

const dot: Record<ClaimStatus, string> = {
  SUBMITTED: 'bg-sky-500',
  IN_REVIEW: 'bg-amber-500',
  APPROVED: 'bg-emerald-500',
  DENIED: 'bg-rose-500',
  PAID: 'bg-violet-500',
}

export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  return (
    <ol className="relative space-y-6 border-l border-slate-200 pl-6 dark:border-slate-800">
      {entries.map((entry) => (
        <li key={entry.eventId} className="relative">
          <span className={`absolute top-1 -left-[31px] size-3 rounded-full ring-4 ring-white dark:ring-slate-900 ${dot[entry.toStatus]}`} />
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <p className="text-sm font-medium">
              {entry.fromStatus ? `${statusLabel[entry.fromStatus]} to ${statusLabel[entry.toStatus]}` : 'Claim submitted'}
            </p>
            <time dateTime={entry.occurredAt} title={formatDateTime(entry.occurredAt)} className="text-xs text-slate-500 dark:text-slate-400">
              {relativeTime(entry.occurredAt)}
            </time>
          </div>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
            by <span className="font-medium text-slate-700 dark:text-slate-300">{entry.actor}</span> ({entry.actorRole.toLowerCase()})
            {entry.amount != null && <> &middot; {formatMoney(entry.amount)}</>} &middot; v{entry.claimVersion}
          </p>
          {entry.note && (
            <p className="mt-2 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:bg-slate-800/60 dark:text-slate-300">
              {entry.note}
            </p>
          )}
        </li>
      ))}
    </ol>
  )
}
