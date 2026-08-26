import { httpClient } from '../../shared/api/httpClient'
import type { AllocationRule, AllocationRuleInput } from './allocationRuleTypes'

export const allocationRuleApi = {
  list: async () => (await httpClient.get<AllocationRule[]>('/allocation-rules')).data,
  create: async (input: AllocationRuleInput) => (await httpClient.post<AllocationRule>('/allocation-rules', input)).data,
  update: async (ruleId: string, input: Omit<AllocationRuleInput, 'currency'>) => (await httpClient.patch<AllocationRule>(`/allocation-rules/${ruleId}`, input)).data,
  remove: async (ruleId: string) => { await httpClient.delete(`/allocation-rules/${ruleId}`) },
}
