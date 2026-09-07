import axios from 'axios'
import { httpClient } from '../../shared/api/httpClient'
import type { CreateTransactionInput, CreateWalletTransferInput, Transaction, TransactionDraftSuggestion, TransactionDraftSuggestionInput, TransactionListParams, TransactionPage } from './transactionTypes'

export const transactionApi = {
  suggestDraft: async (input: TransactionDraftSuggestionInput) => {
    const { data } = await httpClient.post<TransactionDraftSuggestion>('/transaction-drafts/suggestion', input)
    return data
  },
  list: async (params: TransactionListParams = {}) => {
    const { data } = await httpClient.get<TransactionPage>('/transactions', { params })
    return data
  },
  exportCsv: async (params: TransactionListParams = {}) => {
    try {
      const response = await httpClient.get<Blob>('/transactions/export', { params, responseType: 'blob' })
      const disposition = response.headers['content-disposition'] as string | undefined
      const fileName = disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? 'fincore-transactions.csv'
      return { blob: response.data, fileName }
    } catch (error) {
      if (axios.isAxiosError<Blob>(error) && error.response?.data instanceof Blob) {
        const message = await error.response.data.text()
          .then((body) => JSON.parse(body).message as string | undefined)
          .catch(() => undefined)
        if (message) throw new Error(message)
      }
      throw error
    }
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
