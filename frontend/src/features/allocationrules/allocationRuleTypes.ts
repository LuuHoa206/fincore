export type AllocationRuleItem = {
  jarId: string
  jarName: string
  percentage: number
}

export type AllocationRule = {
  id: string
  name: string
  currency: string
  enabled: boolean
  totalPercentage: number
  items: AllocationRuleItem[]
  createdAt: string
  updatedAt: string
}

export type AllocationRuleInput = {
  name: string
  currency: string
  enabled: boolean
  items: Array<{ jarId: string; percentage: number }>
}
