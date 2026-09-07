import { httpClient } from '../../shared/api/httpClient'
import type { CreateSavingGoalInput, SavingGoal, SavingGoalStatus } from './savingGoalTypes'

export const savingGoalApi = {
  list: async () => (await httpClient.get<SavingGoal[]>('/saving-goals')).data,
  create: async (input: CreateSavingGoalInput) => (await httpClient.post<SavingGoal>('/saving-goals', input)).data,
  changeStatus: async (goalId: string, status: SavingGoalStatus) => (await httpClient.post<SavingGoal>(`/saving-goals/${goalId}/status`, { status })).data,
}
