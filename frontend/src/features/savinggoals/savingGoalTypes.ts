export type SavingGoalStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'COMPLETED'

export type SavingGoal = {
  id: string
  jarId: string
  jarName: string
  currency: string
  name: string
  targetAmount: number
  currentAmount: number
  remainingAmount: number
  progressPercentage: number
  suggestedMonthlyContribution: number | null
  targetDate: string | null
  status: SavingGoalStatus
  createdAt: string
  updatedAt: string
}

export type CreateSavingGoalInput = { jarId: string; name: string; targetAmount: number; targetDate?: string }
