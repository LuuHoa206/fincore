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
  WALLET_CREATED: { title: 'Đã tạo ví tiền', description: 'Một ví mới đã được thêm', icon: ArrowLeftRight },
  WALLET_UPDATED: { title: 'Đã cập nhật ví tiền', description: 'Thiết lập ví đã thay đổi', icon: ArrowLeftRight },
  WALLET_ARCHIVED: { title: 'Đã lưu trữ ví tiền', description: 'Ví không còn dùng cho giao dịch mới', icon: ArrowLeftRight },
  CATEGORY_CREATED: { title: 'Đã tạo danh mục', description: 'Một danh mục mới đã được thêm', icon: History },
  CATEGORY_UPDATED: { title: 'Đã cập nhật danh mục', description: 'Thông tin danh mục đã thay đổi', icon: History },
  CATEGORY_ARCHIVED: { title: 'Đã lưu trữ danh mục', description: 'Danh mục không còn dùng cho giao dịch mới', icon: History },
  MONEY_JAR_CREATED: { title: 'Đã tạo hũ tiền', description: 'Một hũ tiền mới đã được thêm', icon: History },
  MONEY_JAR_UPDATED: { title: 'Đã cập nhật hũ tiền', description: 'Thiết lập hũ tiền đã thay đổi', icon: History },
  MONEY_JAR_ARCHIVED: { title: 'Đã lưu trữ hũ tiền', description: 'Hũ tiền không còn dùng cho phân bổ mới', icon: History },
  MONEY_JAR_ALLOCATION_ADDED: { title: 'Đã phân bổ tiền vào hũ', description: 'Số tiền đã được dành riêng cho mục tiêu', icon: ArrowLeftRight },
  MONEY_JAR_ALLOCATION_RELEASED: { title: 'Đã rút tiền khỏi hũ', description: 'Số tiền đã được trả về số dư khả dụng', icon: ArrowLeftRight },
  SAVING_GOAL_CREATED: { title: 'Đã tạo mục tiêu tiết kiệm', description: 'Một mục tiêu tiết kiệm mới đã được thêm', icon: History },
  SAVING_GOAL_UPDATED: { title: 'Đã cập nhật mục tiêu tiết kiệm', description: 'Thông tin mục tiêu đã thay đổi', icon: History },
  SAVING_GOAL_STATUS_CHANGED: { title: 'Đã đổi trạng thái mục tiêu', description: 'Trạng thái theo dõi mục tiêu đã thay đổi', icon: History },
  BUDGET_CREATED: { title: 'Đã tạo ngân sách', description: 'Một ngân sách theo tháng mới đã được thêm', icon: History },
  BUDGET_UPDATED: { title: 'Đã cập nhật ngân sách', description: 'Hạn mức hoặc ngưỡng cảnh báo đã thay đổi', icon: History },
  BUDGET_ARCHIVED: { title: 'Đã lưu trữ ngân sách', description: 'Ngân sách không còn được theo dõi', icon: History },
  ALLOCATION_RULE_CREATED: { title: 'Đã tạo quy tắc chia tiền', description: 'Một quy tắc tự chia khoản thu đã được thêm', icon: ArrowLeftRight },
  ALLOCATION_RULE_UPDATED: { title: 'Đã cập nhật quy tắc chia tiền', description: 'Thiết lập chia khoản thu đã thay đổi', icon: ArrowLeftRight },
  ALLOCATION_RULE_DELETED: { title: 'Đã xóa quy tắc chia tiền', description: 'Quy tắc không còn được áp dụng cho khoản thu mới', icon: ArrowLeftRight },
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
