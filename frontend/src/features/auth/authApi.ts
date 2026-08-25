import axios from 'axios'
import { API_BASE_URL, httpClient } from '../../shared/api/httpClient'
import type { AuthResponse, LoginInput, RegisterInput, UserProfile } from './authTypes'

let sessionRestorePromise: Promise<AuthResponse> | null = null

export const authApi = {
  login: async (input: LoginInput) => {
    const { data } = await httpClient.post<AuthResponse>('/auth/login', input)
    return data
  },
  register: async (input: RegisterInput) => {
    const { data } = await httpClient.post<AuthResponse>('/auth/register', input)
    return data
  },
  refresh: async (refreshToken: string) => {
    const { data } = await axios.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
    return data
  },
  restoreSession: (refreshToken: string) => {
    sessionRestorePromise ??= axios
      .post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .then(({ data }) => data)
      .finally(() => {
        sessionRestorePromise = null
      })
    return sessionRestorePromise
  },
  logout: async (refreshToken: string) => {
    await axios.post(`${API_BASE_URL}/auth/logout`, { refreshToken })
  },
  me: async () => {
    const { data } = await httpClient.get<UserProfile>('/users/me')
    return data
  },
}
