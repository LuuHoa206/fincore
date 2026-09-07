import { httpClient } from '../../shared/api/httpClient'
import type { Budget, BudgetLimitSuggestion, CreateBudgetInput, UpdateBudgetInput } from './budgetTypes'

export const budgetApi = {
  list: async (period: string) => {
    const { data } = await httpClient.get<Budget[]>('/budgets', { params: { period } })
    return data
  },
  suggestions: async (period: string, currency: string) => {
    const { data } = await httpClient.get<BudgetLimitSuggestion[]>('/budgets/suggestions', { params: { period, currency } })
    return data
  },
  create: async (input: CreateBudgetInput) => {
    const { data } = await httpClient.post<Budget>('/budgets', input)
    return data
  },
  update: async (budgetId: string, input: UpdateBudgetInput) => {
    const { data } = await httpClient.patch<Budget>(`/budgets/${budgetId}`, input)
    return data
  },
  archive: async (budgetId: string) => {
    await httpClient.delete(`/budgets/${budgetId}`)
  },
}
