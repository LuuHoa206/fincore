import { httpClient } from '../../shared/api/httpClient'
import type { FinancialNotification } from './notificationTypes'

export const notificationApi = {
  list: async () => (await httpClient.get<FinancialNotification[]>('/notifications')).data,
  unreadCount: async () => (await httpClient.get<{ count: number }>('/notifications/unread-count')).data.count,
  markRead: async (notificationKey: string) => { await httpClient.patch('/notifications/read', { notificationKey }) },
  markUnread: async (notificationKey: string) => { await httpClient.patch('/notifications/unread', { notificationKey }) },
}
