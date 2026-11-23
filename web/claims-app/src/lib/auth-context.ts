import { createContext, useContext } from 'react'
import type { User } from './types'

export interface AuthState {
  user: User | null
  login: (username: string, password: string) => Promise<User>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

export const isStaff = (user: User | null) => user?.role === 'ADJUSTER' || user?.role === 'ADMIN'
