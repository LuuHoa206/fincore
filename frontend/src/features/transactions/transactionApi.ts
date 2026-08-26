import { httpClient } from '../../shared/api/httpClient'
import type { CreateTransactionInput, Transaction } from './transactionTypes'

export const transactionApi = {
  list: async () => {
    const { data } = await httpClient.get<Transaction[]>('/transactions')
    return data
  },
  create: async (input: CreateTransactionInput, idempotencyKey: string) => {
    const { data } = await httpClient.post<Transaction>('/transactions', input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return data
  },
  reverse: async (transactionId: string) => {
    const { data } = await httpClient.post<Transaction>(`/transactions/${transactionId}/reverse`)
    return data
  },
}
