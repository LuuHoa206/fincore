import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { queryClient } from '../../shared/api/queryClient'
import { authApi } from './authApi'
import { AuthContext, type AuthContextValue } from './authContextState'
import type { UserProfile } from './authTypes'
import { tokenStore } from './tokenStore'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(() => Boolean(tokenStore.getRefreshToken()))

  const applySession = useCallback((session: Awaited<ReturnType<typeof authApi.login>>) => {
    tokenStore.setTokens(session.accessToken, session.refreshToken)
    setUser(session.user)
  }, [])

  useEffect(() => {
    const refreshToken = tokenStore.getRefreshToken()
    if (!refreshToken) {
      return
    }

    let active = true
    authApi.restoreSession(refreshToken)
      .then((session) => active && applySession(session))
      .catch(() => active && tokenStore.clear())
      .finally(() => active && setIsLoading(false))

    return () => {
      active = false
    }
  }, [applySession])

  useEffect(() => {
    const expireSession = () => {
      queryClient.clear()
      setUser(null)
    }
    window.addEventListener('fincore:session-expired', expireSession)
    return () => window.removeEventListener('fincore:session-expired', expireSession)
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isLoading,
    login: async (input) => applySession(await authApi.login(input)),
    register: async (input) => applySession(await authApi.register(input)),
    logout: async () => {
      const refreshToken = tokenStore.getRefreshToken()
      tokenStore.clear()
      setUser(null)
      queryClient.clear()
      if (refreshToken) {
        await authApi.logout(refreshToken).catch(() => undefined)
      }
    },
  }), [applySession, isLoading, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
