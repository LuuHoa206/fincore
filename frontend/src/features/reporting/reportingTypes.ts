import type { Transaction } from '../transactions/transactionTypes'

export type CurrencyDashboardSummary = {
  currency: string
  walletBalance: number
  allocatedToJars: number
  availableToAllocate: number
  monthlyIncome: number
  incomeTransactionCount: number
  monthlyExpense: number
  expenseTransactionCount: number
  monthlyNet: number
}

export type DashboardReport = {
  period: string
  timeZone: string
  currencySummaries: CurrencyDashboardSummary[]
  activeWalletCount: number
  activeJarCount: number
  budgetAlertCount: number
  openSavingGoalCount: number
  recentTransactions: Transaction[]
}
