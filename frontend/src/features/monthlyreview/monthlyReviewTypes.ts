import type { CurrencyDashboardSummary, FinancialInsight } from '../reporting/reportingTypes'

export type MonthlyReview = {
  period: string
  timeZone: string
  currencySummaries: CurrencyDashboardSummary[]
  insights: FinancialInsight[]
  reviewed: boolean
  reflection: string | null
  nextMonthFocus: string | null
  reviewedAt: string | null
  updatedAt: string | null
}

export type SaveMonthlyReviewInput = {
  reflection: string
  nextMonthFocus: string
}
