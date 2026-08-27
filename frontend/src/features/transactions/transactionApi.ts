import { httpClient } from '../../shared/api/httpClient'
import type { CreateTransactionInput, CreateWalletTransferInput, Transaction, TransactionListParams, TransactionPage } from './transactionTypes'

export const transactionApi = {
  list: async (params: TransactionListParams = {}) => {
    const { data } = await httpClient.get<TransactionPage>('/transactions', { params })
    return data
  },
  create: async (input: CreateTransactionInput, idempotencyKey: string) => {
    const { data } = await httpClient.post<Transaction>('/transactions', input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return data
  },
  transfer: async (input: CreateWalletTransferInput, idempotencyKey: string) => {
    const { data } = await httpClient.post<Transaction>('/transactions/transfers', input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return data
  },
  reverse: async (transactionId: string) => {
    const { data } = await httpClient.post<Transaction>(`/transactions/${transactionId}/reverse`)
    return data
  },
}
