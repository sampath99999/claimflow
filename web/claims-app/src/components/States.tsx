import type { ReactNode } from 'react'
import { ApiError } from '../lib/api'

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-3 p-6 text-sm text-slate-500 dark:text-slate-400">
      <span className="size-4 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600" />
      {label}
    </div>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <div className="flex size-10 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800">
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth={1.8}>
          <path d="M9 12h6m-6 4h6M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" />
        </svg>
      </div>
      <p className="font-medium text-slate-700 dark:text-slate-200">{title}</p>
      {children && <div className="text-sm text-slate-500 dark:text-slate-400">{children}</div>}
    </div>
  )
}

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null
  const violations = apiError?.problem.violations ?? []
  const fieldErrors = apiError?.problem.errors ?? []
  return (
    <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">
      <p className="font-medium">{apiError?.problem.title ?? 'Something went wrong'}</p>
      <p className="mt-1">{error instanceof Error ? error.message : String(error)}</p>
      {violations.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-5">
          {violations.map((v) => (
            <li key={v.code}>
              <span className="font-mono text-xs">{v.code}</span> {v.message}
            </li>
          ))}
        </ul>
      )}
      {fieldErrors.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-5">
          {fieldErrors.map((f) => (
            <li key={f.field}>
              <span className="font-mono text-xs">{f.field}</span> {f.message}
            </li>
          ))}
        </ul>
      )}
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-3 text-sm font-medium underline underline-offset-2">
          Try again
        </button>
      )}
    </div>
  )
}
