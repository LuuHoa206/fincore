import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '../../features/auth/authTypes'
import { tokenStore } from '../../features/auth/tokenStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

export const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean }

let refreshPromise: Promise<string> | null = null

httpClient.interceptors.request.use((config) => {
  const accessToken = tokenStore.getAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined
    const refreshToken = tokenStore.getRefreshToken()
    const isAuthRequest = request?.url?.startsWith('/auth/')

    if (error.response?.status !== 401 || !request || request._retry || !refreshToken || isAuthRequest) {
      return Promise.reject(error)
    }

    request._retry = true
    refreshPromise ??= axios
      .post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .then(({ data }) => {
        tokenStore.setTokens(data.accessToken, data.refreshToken)
        return data.accessToken
      })
      .catch((refreshError: unknown) => {
        tokenStore.clear()
        window.dispatchEvent(new Event('fincore:session-expired'))
        return Promise.reject(refreshError)
      })
      .finally(() => {
        refreshPromise = null
      })

    request.headers.Authorization = `Bearer ${await refreshPromise}`
    return httpClient(request)
  },
)
