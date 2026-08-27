import { httpClient } from '../../shared/api/httpClient'
import type { DashboardReport, MonthlyFinancialInsights, UnusualExpensesReport } from './reportingTypes'

export const reportingApi = {
  dashboard: async (period?: string) => {
    const { data } = await httpClient.get<DashboardReport>('/reports/dashboard', {
      params: period ? { period } : undefined,
    })
    return data
  },
  monthlyInsights: async (period?: string) => {
    const { data } = await httpClient.get<MonthlyFinancialInsights>('/reports/dashboard/insights', {
      params: period ? { period } : undefined,
    })
    return data
  },
  unusualExpenses: async (period?: string) => {
    const { data } = await httpClient.get<UnusualExpensesReport>('/reports/dashboard/unusual-expenses', {
      params: period ? { period } : undefined,
    })
    return data
  },
}
