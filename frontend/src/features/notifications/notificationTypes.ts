export type NotificationPriority = 'INFO' | 'WARNING' | 'CRITICAL'

export type FinancialNotification = {
  key: string
  priority: NotificationPriority
  title: string
  message: string
  destination: string
  occurredAt: string
  read: boolean
}
