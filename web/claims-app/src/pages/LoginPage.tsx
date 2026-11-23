import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { ErrorNotice } from '../components/States'
import { useAuth } from '../lib/auth-context'

// Seeded demo accounts (see README). They only exist when the gateway runs with the "demo" profile.
const DEMO_USERS = [
  { username: 'maria', password: 'member-demo', label: 'Maria', hint: 'Member' },
  { username: 'ava', password: 'adjuster-demo', label: 'Ava', hint: 'Adjuster' },
  { username: 'admin', password: 'admin-demo', label: 'Admin', hint: 'Admin' },
]

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/claims" replace />

  async function signIn(name: string, secret: string) {
    setBusy(true)
    setError(null)
    try {
      await login(name, secret)
      navigate('/claims', { replace: true })
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void signIn(username, password)
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <img src="/favicon.svg" alt="" className="size-10" />
          <div>
            <h1 className="text-xl font-semibold tracking-tight">Sign in to ClaimFlow</h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Submit claims and follow every decision.</p>
          </div>
        </div>
        <form onSubmit={onSubmit} className="card space-y-4 p-6">
          <div>
            <label htmlFor="username" className="label">Username</label>
            <input id="username" className="input" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
          </div>
          <div>
            <label htmlFor="password" className="label">Password</label>
            <input id="password" type="password" className="input" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {error != null && <ErrorNotice error={error} />}
          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
        <div className="mt-6">
          <p className="mb-2 text-center text-xs text-slate-500 dark:text-slate-400">Demo accounts</p>
          <div className="grid grid-cols-3 gap-2">
            {DEMO_USERS.map((demo) => (
              <button key={demo.username} type="button" disabled={busy} onClick={() => void signIn(demo.username, demo.password)} className="btn-secondary flex-col gap-0 py-2">
                <span>{demo.label}</span>
                <span className="text-[11px] font-normal text-slate-500 dark:text-slate-400">{demo.hint}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
