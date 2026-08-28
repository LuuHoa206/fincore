export type FinancialCalendarEntryKind = 'ACTUAL' | 'SCHEDULED'
export type FinancialCalendarTransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REVERSAL'

export interface FinancialCalendarEntry {
  key: string
  sourceId: string
  kind: FinancialCalendarEntryKind
  occurredAt: string
  transactionType: FinancialCalendarTransactionType
  amount: number
  currency: string
  title: string
  categoryName: string | null
  walletName: string
  autoRecord: boolean
}

export interface FinancialCalendarDay {
  date: string
  entries: FinancialCalendarEntry[]
}

export interface FinancialCalendar {
  period: string
  timeZone: string
  days: FinancialCalendarDay[]
}
