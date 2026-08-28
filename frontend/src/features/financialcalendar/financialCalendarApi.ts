import { httpClient } from '../../shared/api/httpClient'
import type { FinancialCalendar } from './financialCalendarTypes'

export const financialCalendarApi = {
  get: async (period: string) => (await httpClient.get<FinancialCalendar>('/financial-calendar', { params: { period } })).data,
}
