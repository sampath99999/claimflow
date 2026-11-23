import type { ClaimAction, ClaimStatus } from './types'

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const dateFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' })
const dateTimeFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export const formatMoney = (value: number | null | undefined) => (value == null ? '-' : money.format(value))

// Service dates are plain LocalDate strings; parse as local midnight so they don't shift a day.
export const formatDate = (iso: string) => dateFormat.format(new Date(`${iso}T00:00:00`))

export const formatDateTime = (iso: string) => dateTimeFormat.format(new Date(iso))

export const statusLabel: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  APPROVED: 'Approved',
  DENIED: 'Denied',
  PAID: 'Paid',
}

export const actionLabel: Record<ClaimAction, string> = {
  START_REVIEW: 'Start review',
  APPROVE: 'Approve',
  DENY: 'Deny',
  MARK_PAID: 'Mark paid',
}

export function relativeTime(iso: string, now = Date.now()) {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  const abs = Math.abs(seconds)
  if (abs < 60) return rtf.format(seconds, 'second')
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), 'hour')
  return rtf.format(Math.round(seconds / 86400), 'day')
}
