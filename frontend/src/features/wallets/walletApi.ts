import { httpClient } from '../../shared/api/httpClient'
import type { CreateWalletInput, UpdateWalletInput, Wallet } from './walletTypes'

export const walletApi = {
  list: async () => {
    const { data } = await httpClient.get<Wallet[]>('/wallets')
    return data
  },
  create: async (input: CreateWalletInput) => {
    const { data } = await httpClient.post<Wallet>('/wallets', input)
    return data
  },
  update: async (walletId: string, input: UpdateWalletInput) => {
    const { data } = await httpClient.patch<Wallet>(`/wallets/${walletId}`, input)
    return data
  },
  archive: async (walletId: string) => {
    await httpClient.delete(`/wallets/${walletId}`)
  },
}
