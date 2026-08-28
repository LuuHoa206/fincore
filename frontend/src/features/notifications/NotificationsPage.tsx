import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, Check, CircleAlert, Clock3, ExternalLink, Info, LoaderCircle, RotateCcw, ShieldAlert } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatDateTime } from '../transactions/transactionFormatters'
import { notificationApi } from './notificationApi'
import type { FinancialNotification } from './notificationTypes'

const priorityMeta = {
  INFO: { label: 'Sắp đến hạn', icon: Info },
  WARNING: { label: 'Cần chú ý', icon: BellRing },
  CRITICAL: { label: 'Cần xử lý', icon: ShieldAlert },
} as const

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const notificationsQuery = useQuery({ queryKey: ['notifications'], queryFn: notificationApi.list })
  const updateReadState = useMutation({
    mutationFn: async ({ notificationKey, read }: { notificationKey: string; read: boolean }) => {
      if (read) return notificationApi.markRead(notificationKey)
      return notificationApi.markUnread(notificationKey)
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const notifications = notificationsQuery.data ?? []
  const unreadCount = notifications.filter((notification) => !notification.read).length

  return <>
    <header className="topbar notification-topbar">
      <div><p className="eyebrow">NHẮC VIỆC TÀI CHÍNH</p><h1>Thông báo cần chú ý</h1><p className="page-subtitle">Các nhắc việc được tổng hợp từ lịch thu chi và ngân sách hiện tại. FinCore không tự ghi giao dịch hoặc gửi email thay bạn.</p></div>
      <div className="notification-summary"><BellRing /><span><strong>{unreadCount}</strong> chưa đọc</span></div>
    </header>

    {notificationsQuery.isPending && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tổng hợp nhắc việc</span></div>}
    {notificationsQuery.isError && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải thông báo</strong><p>{getApiErrorMessage(notificationsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void notificationsQuery.refetch()}>Thử lại</button></div>}
    {!notificationsQuery.isPending && !notificationsQuery.isError && notifications.length === 0 && <div className="content-state notification-empty"><BellRing /><strong>Không có nhắc việc nào</strong><p>Khi lịch thu chi gần đến hạn hoặc ngân sách cần xem lại, thông tin sẽ xuất hiện ở đây.</p></div>}
    {!notificationsQuery.isPending && !notificationsQuery.isError && notifications.length > 0 && <section className="notification-list" aria-label="Danh sách thông báo">
      {notifications.map((notification) => <NotificationItem key={notification.key} notification={notification} isPending={updateReadState.isPending} onOpen={() => navigate(notification.destination)} onReadStateChange={(read) => updateReadState.mutate({ notificationKey: notification.key, read })} />)}
    </section>}
  </>
}

function NotificationItem({ notification, isPending, onOpen, onReadStateChange }: { notification: FinancialNotification; isPending: boolean; onOpen: () => void; onReadStateChange: (read: boolean) => void }) {
  const meta = priorityMeta[notification.priority]
  const Icon = meta.icon
  return <article className={`notification-item priority-${notification.priority.toLowerCase()}${notification.read ? ' is-read' : ''}`}>
    <span className="notification-icon"><Icon /></span>
    <div className="notification-content"><div className="notification-heading"><strong>{notification.title}</strong><span>{meta.label}</span></div><p>{notification.message}</p><time dateTime={notification.occurredAt}><Clock3 /> {formatDateTime(notification.occurredAt)}</time></div>
    <div className="notification-actions"><button className="secondary-button compact-button" onClick={onOpen}><ExternalLink /> Mở</button><button className="icon-button" title={notification.read ? 'Đánh dấu chưa đọc' : 'Đánh dấu đã đọc'} aria-label={notification.read ? `Đánh dấu ${notification.title} chưa đọc` : `Đánh dấu ${notification.title} đã đọc`} onClick={() => onReadStateChange(!notification.read)} disabled={isPending}>{notification.read ? <RotateCcw /> : <Check />}</button></div>
  </article>
}
