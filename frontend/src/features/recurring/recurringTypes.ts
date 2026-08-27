import type { CategoryType } from '../categories/categoryTypes'

export const recurringFrequencies = ['DAILY', 'WEEKLY', 'MONTHLY'] as const
export type RecurringFrequency = typeof recurringFrequencies[number]

export type RecurringRule = {
  id: string
  name: string
  walletId: string
  walletName: string
  categoryId: string
  categoryName: string
  transactionType: CategoryType
  amount: number
  currency: string
  description: string
  notes: string | null
  frequency: RecurringFrequency
  nextRunAt: string
  autoRecord: boolean
  enabled: boolean
  applyAllocationRule: boolean
  createdAt: string
  updatedAt: string
}

export type RecurringRuleInput = {
  name: string
  walletId: string
  categoryId: string
  transactionType: CategoryType
  amount: number
  description: string
  notes?: string
  frequency: RecurringFrequency
  nextRunAt: string
  autoRecord: boolean
  enabled: boolean
  applyAllocationRule: boolean
}
