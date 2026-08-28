import { httpClient } from '../../shared/api/httpClient'
import type { MonthlyReview, SaveMonthlyReviewInput } from './monthlyReviewTypes'

export const monthlyReviewApi = {
  get: async (period: string) => (await httpClient.get<MonthlyReview>('/monthly-reviews', { params: { period } })).data,
  save: async (period: string, input: SaveMonthlyReviewInput) => (await httpClient.put<MonthlyReview>('/monthly-reviews', input, { params: { period } })).data,
}
