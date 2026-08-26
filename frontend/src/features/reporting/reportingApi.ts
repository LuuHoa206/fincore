import { httpClient } from '../../shared/api/httpClient'
import type { DashboardReport } from './reportingTypes'

export const reportingApi = {
  dashboard: async (period?: string) => {
    const { data } = await httpClient.get<DashboardReport>('/reports/dashboard', {
      params: period ? { period } : undefined,
    })
    return data
  },
}
