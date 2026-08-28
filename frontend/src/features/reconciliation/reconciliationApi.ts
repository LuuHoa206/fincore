import { httpClient } from '../../shared/api/httpClient'
import type { WalletReconciliationInput, WalletReconciliationPreview } from './reconciliationTypes'

export const reconciliationApi = {
  preview: async (input: WalletReconciliationInput) => {
    const { data } = await httpClient.post<WalletReconciliationPreview>('/wallet-reconciliations/preview', input)
    return data
  },
}
