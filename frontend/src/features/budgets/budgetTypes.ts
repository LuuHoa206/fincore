export type BudgetStatus = 'ON_TRACK' | 'WARNING' | 'EXCEEDED'

export type Budget = {
  id: string
  categoryId: string
  categoryName: string
  categoryIcon: string | null
  categoryColor: string | null
  periodStart: string
  limitAmount: number
  spentAmount: number
  remainingAmount: number
  usagePercentage: number
  currency: string
  warningThreshold: number
  status: BudgetStatus
  createdAt: string
  updatedAt: string
}

export type CreateBudgetInput = {
  categoryId: string
  periodStart: string
  limitAmount: number
  currency: string
  warningThreshold?: number
}

export type UpdateBudgetInput = {
  limitAmount?: number
  warningThreshold?: number
}
