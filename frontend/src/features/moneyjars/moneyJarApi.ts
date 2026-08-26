import { httpClient } from '../../shared/api/httpClient'
import type { CreateMoneyJarInput, JarAllocationResult, MoneyJar, UpdateMoneyJarInput } from './moneyJarTypes'

export const moneyJarApi = {
  list: async () => {
    const { data } = await httpClient.get<MoneyJar[]>('/jars')
    return data
  },
  create: async (input: CreateMoneyJarInput) => {
    const { data } = await httpClient.post<MoneyJar>('/jars', input)
    return data
  },
  update: async (jarId: string, input: UpdateMoneyJarInput) => {
    const { data } = await httpClient.patch<MoneyJar>(`/jars/${jarId}`, input)
    return data
  },
  archive: async (jarId: string) => {
    await httpClient.delete(`/jars/${jarId}`)
  },
  allocate: async (jarId: string, amount: number) => {
    const { data } = await httpClient.post<JarAllocationResult>(`/jars/${jarId}/allocate`, { amount })
    return data
  },
  release: async (jarId: string, amount: number) => {
    const { data } = await httpClient.post<JarAllocationResult>(`/jars/${jarId}/release`, { amount })
    return data
  },
}
