import { httpClient } from '../../shared/api/httpClient'
import type { RecurringRule, RecurringRuleInput } from './recurringTypes'

export const recurringApi = {
  list: async () => (await httpClient.get<RecurringRule[]>('/recurring-rules')).data,
  upcoming: async (limit = 4) => (await httpClient.get<RecurringRule[]>('/recurring-rules/upcoming', { params: { limit } })).data,
  create: async (input: RecurringRuleInput) => (await httpClient.post<RecurringRule>('/recurring-rules', input)).data,
  update: async (ruleId: string, input: RecurringRuleInput) => (await httpClient.patch<RecurringRule>(`/recurring-rules/${ruleId}`, input)).data,
  record: async (ruleId: string) => (await httpClient.post(`/recurring-rules/${ruleId}/record`)).data,
  disable: async (ruleId: string) => { await httpClient.delete(`/recurring-rules/${ruleId}`) },
}
