import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { ErrorNotice } from '../components/States'
import { api } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth-context'
import { formatMoney } from '../lib/format'
import type { NewClaim, NewClaimLine } from '../lib/types'

const emptyLine = (): NewClaimLine => ({ procedureCode: '', description: '', units: 1, chargeAmount: '' })

function daysAgo(days: number) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return d.toISOString().slice(0, 10)
}

export function NewClaimPage() {
  const { user } = useAuth()
  const staff = isStaff(user)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [memberId, setMemberId] = useState('')
  const [providerNpi, setProviderNpi] = useState('1234567893')
  const [providerName, setProviderName] = useState('Lakeside Family Clinic')
  const [serviceDate, setServiceDate] = useState(daysAgo(3))
  const [diagnosisCode, setDiagnosisCode] = useState('J02.9')
  const [lines, setLines] = useState<NewClaimLine[]>([
    { procedureCode: '99213', description: 'Office visit, established patient', units: 1, chargeAmount: '145.00' },
    { procedureCode: '87880', description: 'Rapid strep test', units: 1, chargeAmount: '38.50' },
  ])

  const submit = useMutation({
    mutationFn: (claim: NewClaim) => api.submitClaim(claim),
    onSuccess: (claim) => {
      void queryClient.invalidateQueries({ queryKey: ['claims'] })
      navigate(`/claims/${claim.id}`)
    },
  })

  const total = lines.reduce((sum, line) => sum + (Number(line.chargeAmount) || 0), 0)

  function updateLine(index: number, patch: Partial<NewClaimLine>) {
    setLines((current) => current.map((line, i) => (i === index ? { ...line, ...patch } : line)))
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    submit.mutate({
      memberId: staff ? memberId.trim() : undefined,
      providerNpi,
      providerName,
      serviceDate,
      diagnosisCode: diagnosisCode.toUpperCase(),
      lines,
    })
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">New claim</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Eligibility is checked against the member's plan for the service date before the claim is accepted.
        </p>
      </div>

      <section className="card grid gap-4 p-6 sm:grid-cols-2">
        {staff && (
          <div className="sm:col-span-2">
            <label htmlFor="memberId" className="label">Member ID</label>
            <input id="memberId" className="input font-mono" placeholder="UUID of the member" value={memberId} onChange={(e) => setMemberId(e.target.value)} required />
          </div>
        )}
        <div>
          <label htmlFor="npi" className="label">Provider NPI</label>
          <input id="npi" className="input font-mono" inputMode="numeric" maxLength={10} value={providerNpi} onChange={(e) => setProviderNpi(e.target.value)} required />
        </div>
        <div>
          <label htmlFor="providerName" className="label">Provider name</label>
          <input id="providerName" className="input" value={providerName} onChange={(e) => setProviderName(e.target.value)} required />
        </div>
        <div>
          <label htmlFor="serviceDate" className="label">Service date</label>
          <input id="serviceDate" type="date" className="input" value={serviceDate} onChange={(e) => setServiceDate(e.target.value)} required />
        </div>
        <div>
          <label htmlFor="dx" className="label">Diagnosis (ICD-10)</label>
          <input id="dx" className="input font-mono" value={diagnosisCode} onChange={(e) => setDiagnosisCode(e.target.value)} required />
        </div>
      </section>

      <section className="card">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-800">
          <h2 className="font-medium">Service lines</h2>
          <button type="button" className="btn-secondary px-2.5 py-1 text-xs" onClick={() => setLines((l) => [...l, emptyLine()])} disabled={lines.length >= 25}>
            Add line
          </button>
        </div>
        <div className="space-y-3 p-6">
          {lines.map((line, index) => (
            <div key={index} className="grid grid-cols-12 items-end gap-2">
              <div className="col-span-3 sm:col-span-2">
                {index === 0 && <span className="label">CPT</span>}
                <input aria-label={`Line ${index + 1} procedure code`} className="input font-mono" maxLength={5} value={line.procedureCode} onChange={(e) => updateLine(index, { procedureCode: e.target.value.toUpperCase() })} required />
              </div>
              <div className="col-span-9 sm:col-span-5">
                {index === 0 && <span className="label">Description</span>}
                <input aria-label={`Line ${index + 1} description`} className="input" value={line.description} onChange={(e) => updateLine(index, { description: e.target.value })} required />
              </div>
              <div className="col-span-3 sm:col-span-1">
                {index === 0 && <span className="label">Units</span>}
                <input aria-label={`Line ${index + 1} units`} type="number" min={1} max={99} className="input" value={line.units} onChange={(e) => updateLine(index, { units: Number(e.target.value) })} required />
              </div>
              <div className="col-span-6 sm:col-span-3">
                {index === 0 && <span className="label">Charge (USD)</span>}
                <input aria-label={`Line ${index + 1} charge`} type="number" min="0.01" step="0.01" className="input text-right tabular-nums" value={line.chargeAmount} onChange={(e) => updateLine(index, { chargeAmount: e.target.value })} required />
              </div>
              <div className="col-span-3 sm:col-span-1">
                <button type="button" aria-label={`Remove line ${index + 1}`} className="btn-secondary w-full px-0" onClick={() => setLines((l) => l.filter((_, i) => i !== index))} disabled={lines.length === 1}>
                  &times;
                </button>
              </div>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-between border-t border-slate-200 px-6 py-4 text-sm dark:border-slate-800">
          <span className="text-slate-500 dark:text-slate-400">{lines.length} line{lines.length === 1 ? '' : 's'}</span>
          <span className="font-medium tabular-nums">Total billed {formatMoney(total)}</span>
        </div>
      </section>

      {submit.isError && <ErrorNotice error={submit.error} />}

      <div className="flex justify-end gap-3">
        <button type="button" className="btn-secondary" onClick={() => navigate('/claims')}>Cancel</button>
        <button type="submit" className="btn-primary" disabled={submit.isPending}>
          {submit.isPending ? 'Checking eligibility...' : 'Submit claim'}
        </button>
      </div>
    </form>
  )
}
