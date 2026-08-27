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

export type FinancialInsightSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'DANGER'

export type FinancialInsight = {
  key: string
  severity: FinancialInsightSeverity
  title: string
  message: string
  currency: string | null
  amount: number | null
}

export type MonthlyFinancialInsights = {
  period: string
  timeZone: string
  insights: FinancialInsight[]
}

export type ExpenseAnomalySeverity = 'MEDIUM' | 'HIGH'

export type UnusualExpense = {
  transactionId: string
  description: string
  categoryName: string
  currency: string
  amount: number
  historicalAverageAmount: number
  historicalTransactionCount: number
  multipleOfAverage: number
  severity: ExpenseAnomalySeverity
  reason: string
  occurredAt: string
}

export type UnusualExpensesReport = {
  period: string
  timeZone: string
  findings: UnusualExpense[]
}
