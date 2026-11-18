import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/auth'
import { refreshSession, setAccessToken, setSessionHandlers } from '../api/client'

const AuthContext = createContext(null)

/**
 * Holds the logged-in user. On page load it tries to restore the session from the refresh
 * cookie (status 'loading' until that finishes).
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading') // 'loading' | 'authenticated' | 'anonymous'

  useEffect(() => {
    setSessionHandlers({
      expired: () => {
        setUser(null)
        setStatus('anonymous')
      },
      refreshed: (u) => setUser(u),
    })
    refreshSession()
      .then((data) => {
        setUser(data.user)
        setStatus('authenticated')
      })
      .catch(() => setStatus('anonymous'))
  }, [])

  const signIn = useCallback((u) => {
    setUser(u)
    setStatus('authenticated')
  }, [])

  const login = useCallback(async (email, password) => signIn(await authApi.login(email, password)), [signIn])
  const register = useCallback(async (body) => signIn(await authApi.register(body)), [signIn])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // Even if the server call fails, forget the session locally
    }
    setAccessToken(null)
    setUser(null)
    setStatus('anonymous')
  }, [])

  const reloadUser = useCallback(async () => setUser(await authApi.getMe()), [])

  const value = useMemo(
    () => ({ user, status, login, register, logout, setUser, reloadUser }),
    [user, status, login, register, logout, reloadUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
