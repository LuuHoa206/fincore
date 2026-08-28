import { httpClient } from '../../shared/api/httpClient'
import type { FinancialNotification } from './notificationTypes'

export const notificationApi = {
  list: async () => (await httpClient.get<FinancialNotification[]>('/notifications')).data,
  markRead: async (notificationKey: string) => { await httpClient.patch('/notifications/read', { notificationKey }) },
  markUnread: async (notificationKey: string) => { await httpClient.patch('/notifications/unread', { notificationKey }) },
}
