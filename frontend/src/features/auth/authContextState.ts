import { createContext, useContext } from 'react'
import type { LoginInput, RegisterInput, UpdateProfileInput, UserProfile } from './authTypes'

export type AuthContextValue = {
  user: UserProfile | null
  isLoading: boolean
  login: (input: LoginInput) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  updateProfile: (input: UpdateProfileInput) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
