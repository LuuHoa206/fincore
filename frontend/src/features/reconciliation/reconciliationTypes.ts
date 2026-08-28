export type ReconciliationStatus = 'MATCHED' | 'DIFFERENT'

export type WalletReconciliationInput = {
  walletId: string
  statementDate: string
  statementBalance: number
}

export type WalletReconciliationPreview = {
  walletId: string
  walletName: string
  currency: string
  statementDate: string
  timeZone: string
  ledgerBalance: number
  statementBalance: number
  difference: number
  transactionCount: number
  status: ReconciliationStatus
}
