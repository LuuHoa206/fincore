export type MoneyJar = {
  id: string
  name: string
  currency: string
  allocatedBalance: number
  spendingLimit: number | null
  color: string | null
  icon: string | null
  allowNegative: boolean
  createdAt: string
  updatedAt: string
}

export type CreateMoneyJarInput = {
  name: string
  currency: string
  spendingLimit?: number
  color?: string
  icon?: string
  allowNegative: boolean
}

export type UpdateMoneyJarInput = Omit<Partial<CreateMoneyJarInput>, 'currency'>

export type JarAllocationResult = {
  jar: MoneyJar
  availableToAllocate: number
}

export type JarTransferResult = {
  sourceJar: MoneyJar
  destinationJar: MoneyJar
  amount: number
}
