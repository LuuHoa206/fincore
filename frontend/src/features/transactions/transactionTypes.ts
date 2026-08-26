export const transactionTypes = ['INCOME', 'EXPENSE', 'TRANSFER', 'JAR_TRANSFER', 'REFUND', 'ADJUSTMENT', 'REVERSAL'] as const

export type TransactionType = typeof transactionTypes[number]
export type TransactionStatus = 'PENDING' | 'POSTED' | 'REVERSED' | 'FAILED'

export type Transaction = {
  id: string
  walletId: string
  walletName: string
  transactionType: TransactionType
  status: TransactionStatus
  amount: number
  currency: string
  description: string
  notes: string | null
  occurredAt: string
  postedAt: string
  reversedTransactionId: string | null
}

export type CreateTransactionInput = {
  walletId: string
  transactionType: 'INCOME' | 'EXPENSE'
  amount: number
  description: string
  notes?: string
  occurredAt: string
}
