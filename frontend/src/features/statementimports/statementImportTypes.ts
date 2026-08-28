import type { Transaction, TransactionType } from '../transactions/transactionTypes'

export type StatementImportRowStatus = 'READY' | 'DUPLICATE' | 'INVALID'

export type StatementImportInput = {
  walletId: string
  incomeCategoryId?: string
  expenseCategoryId?: string
  csvText: string
}

export type StatementImportRow = {
  rowNumber: number
  status: StatementImportRowStatus
  transactionType: TransactionType | null
  amount: number | null
  description: string | null
  notes: string | null
  occurredAt: string | null
  reason: string | null
}

export type StatementImportPreview = {
  walletId: string
  currency: string
  totalRows: number
  readyCount: number
  duplicateCount: number
  invalidCount: number
  rows: StatementImportRow[]
}

export type StatementImportResult = {
  importedCount: number
  skippedDuplicateCount: number
  transactions: Transaction[]
}
