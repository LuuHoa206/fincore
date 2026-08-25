import axios from 'axios'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})
