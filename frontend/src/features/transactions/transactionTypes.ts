export const transactionTypes = ['INCOME', 'EXPENSE', 'TRANSFER', 'JAR_TRANSFER', 'REFUND', 'ADJUSTMENT', 'REVERSAL'] as const

export type TransactionType = typeof transactionTypes[number]
export type TransactionStatus = 'PENDING' | 'POSTED' | 'REVERSED' | 'FAILED'

export type Transaction = {
  id: string
  walletId: string
  walletName: string
  categoryId: string | null
  categoryName: string | null
  categoryIcon: string | null
  categoryColor: string | null
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
  categoryId: string
  transactionType: 'INCOME' | 'EXPENSE'
  amount: number
  description: string
  notes?: string
  occurredAt: string
}

export type TransactionListParams = {
  transactionType?: 'INCOME' | 'EXPENSE'
  query?: string
  page?: number
  size?: number
}

export type TransactionPage = {
  content: Transaction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
