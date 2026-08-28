import { httpClient } from '../../shared/api/httpClient'
import type { Transaction } from '../transactions/transactionTypes'
import type { WalletReconciliationAdjustmentInput, WalletReconciliationInput, WalletReconciliationPreview } from './reconciliationTypes'

export const reconciliationApi = {
  preview: async (input: WalletReconciliationInput) => {
    const { data } = await httpClient.post<WalletReconciliationPreview>('/wallet-reconciliations/preview', input)
    return data
  },
  confirmAdjustment: async (input: WalletReconciliationAdjustmentInput, idempotencyKey: string) => {
    const { data } = await httpClient.post<Transaction>('/wallet-reconciliations/adjustments', input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return data
  },
}
