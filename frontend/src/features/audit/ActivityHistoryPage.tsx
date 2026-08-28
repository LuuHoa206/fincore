import { ArrowLeftRight, CircleAlert, History, LoaderCircle, RotateCcw, UserRound } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency, formatDateTime } from '../transactions/transactionFormatters'
import { activityApi } from './activityApi'
import type { ActivityLog } from './activityTypes'

const activityCopy: Record<string, { title: string; description: string; icon: typeof History }> = {
  ACCOUNT_REGISTERED: { title: 'Đã tạo tài khoản', description: 'Tài khoản FinCore được khởi tạo', icon: UserRound },
  LOGIN_SUCCEEDED: { title: 'Đã đăng nhập', description: 'Đăng nhập bằng mật khẩu thành công', icon: UserRound },
  PROFILE_UPDATED: { title: 'Cập nhật hồ sơ', description: 'Đã thay đổi thiết lập tài khoản', icon: UserRound },
  TRANSACTION_CREATED: { title: 'Đã ghi nhận giao dịch', description: 'Khoản thu hoặc chi mới', icon: ArrowLeftRight },
  TRANSFER_CREATED: { title: 'Đã chuyển tiền giữa các ví', description: 'Số dư ví đã được cập nhật', icon: ArrowLeftRight },
  TRANSACTION_REVERSED: { title: 'Đã hoàn tác giao dịch', description: 'Giao dịch gốc được đánh dấu hoàn tác', icon: RotateCcw },
}

function formatDetails(activity: ActivityLog) {
  const { amount, currency, type, preferredCurrency, timeZone } = activity.details
  if (typeof amount === 'string' && typeof currency === 'string') return formatCurrency(Number(amount), currency)
  if (typeof type === 'string') return type === 'INCOME' ? 'Khoản thu' : type === 'EXPENSE' ? 'Khoản chi' : type
  if (typeof preferredCurrency === 'string' && typeof timeZone === 'string') return `${preferredCurrency} · ${timeZone}`
  return null
}

export function ActivityHistoryPage() {
  const activityQuery = useQuery({ queryKey: ['activity'], queryFn: () => activityApi.list() })
  const activities = activityQuery.data ?? []

  return <>
    <header className="topbar">
      <div><p className="eyebrow">NHẬT KÝ CÁ NHÂN</p><h1>Lịch sử hoạt động</h1><p className="page-subtitle">Theo dõi các thay đổi quan trọng của tài khoản và giao dịch của bạn.</p></div>
    </header>
    <section className="panel activity-panel">
      {activityQuery.isPending && <div className="content-state compact-state"><LoaderCircle className="spin" /><span>Đang tải lịch sử hoạt động</span></div>}
      {activityQuery.isError && <div className="content-state compact-state error-state"><CircleAlert /><strong>Chưa thể tải lịch sử hoạt động</strong><p>{getApiErrorMessage(activityQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void activityQuery.refetch()}>Thử lại</button></div>}
      {!activityQuery.isPending && !activityQuery.isError && activities.length === 0 && <div className="content-state compact-state"><History /><strong>Chưa có hoạt động nào</strong><p>Những thay đổi tài khoản và giao dịch mới sẽ được ghi lại tại đây.</p></div>}
      {!activityQuery.isPending && !activityQuery.isError && activities.length > 0 && <div className="activity-list">
        {activities.map((activity) => {
          const copy = activityCopy[activity.action] ?? { title: activity.action, description: 'Hoạt động tài khoản', icon: History }
          const Icon = copy.icon
          const detail = formatDetails(activity)
          return <article key={activity.id}>
            <span className={`activity-icon action-${activity.action.toLowerCase()}`}><Icon /></span>
            <div><strong>{copy.title}</strong><small>{detail ?? copy.description}</small></div>
            <time dateTime={activity.createdAt}>{formatDateTime(activity.createdAt)}</time>
          </article>
        })}
      </div>}
    </section>
  </>
}
