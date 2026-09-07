export const categoryTypes = ['INCOME', 'EXPENSE'] as const

export type CategoryType = typeof categoryTypes[number]

export type Category = {
  id: string
  name: string
  categoryType: CategoryType
  icon: string | null
  color: string | null
  systemCategory: boolean
  createdAt: string
}

export type CategorySuggestion = {
  categoryId: string
  categoryName: string
  categoryType: CategoryType
  systemCategory: boolean
  reason: string
  score: number
}

export type CreateCategoryInput = {
  name: string
  categoryType: CategoryType
  icon?: string
  color?: string
}

export type UpdateCategoryInput = Pick<CreateCategoryInput, 'name' | 'icon' | 'color'>
