import { httpClient } from '../../shared/api/httpClient'
import type { ActivityLog } from './activityTypes'

export const activityApi = {
  list: async (limit = 30) => {
    const { data } = await httpClient.get<ActivityLog[]>('/activity', { params: { limit } })
    return data
  },
}
