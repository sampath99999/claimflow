import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from './api'
import { AuthContext } from './auth-context'
import type { User } from './types'

const USER_KEY = 'claimflow.user'

function storedUser(): User | null {
  if (!tokenStore.get()) return null
  const raw = sessionStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as User) : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(storedUser)

  const logout = useCallback(() => {
    tokenStore.clear()
    sessionStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  useEffect(() => setUnauthorizedHandler(logout), [logout])

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.login(username, password)
    tokenStore.set(response.accessToken)
    sessionStorage.setItem(USER_KEY, JSON.stringify(response.user))
    setUser(response.user)
    return response.user
  }, [])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
