import { httpClient } from '../../shared/api/httpClient'
import type { Category, CategoryType, CreateCategoryInput, UpdateCategoryInput } from './categoryTypes'

export const categoryApi = {
  list: async (type?: CategoryType) => {
    const { data } = await httpClient.get<Category[]>('/categories', { params: type ? { type } : undefined })
    return data
  },
  create: async (input: CreateCategoryInput) => {
    const { data } = await httpClient.post<Category>('/categories', input)
    return data
  },
  update: async (categoryId: string, input: UpdateCategoryInput) => {
    const { data } = await httpClient.patch<Category>(`/categories/${categoryId}`, input)
    return data
  },
  archive: async (categoryId: string) => {
    await httpClient.delete(`/categories/${categoryId}`)
  },
}
