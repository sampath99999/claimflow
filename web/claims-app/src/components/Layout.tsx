import { NavLink, Outlet } from 'react-router'
import { isStaff, useAuth } from '../lib/auth-context'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-1.5 text-sm font-medium transition ${
    isActive
      ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900'
      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'
  }`

export function Layout() {
  const { user, logout } = useAuth()
  const staff = isStaff(user)

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-950/80">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <NavLink to="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <img src="/favicon.svg" alt="" className="size-6" />
            ClaimFlow
          </NavLink>
          <nav className="flex items-center gap-1">
            <NavLink to="/claims" className={linkClass} end>
              {staff ? 'Review queue' : 'My claims'}
            </NavLink>
            <NavLink to="/claims/new" className={linkClass}>
              New claim
            </NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm leading-tight font-medium">{user?.displayName}</p>
              <p className="text-xs leading-tight text-slate-500 dark:text-slate-400">{user?.role.toLowerCase()}</p>
            </div>
            <button type="button" onClick={logout} className="btn-secondary px-2.5 py-1.5 text-xs">
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
