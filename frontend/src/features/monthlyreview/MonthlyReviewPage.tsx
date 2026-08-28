import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, CheckCircle2, CircleAlert, Info, Lightbulb, LoaderCircle, Save, TrendingDown, TrendingUp, WalletCards } from 'lucide-react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency, formatDateTime } from '../transactions/transactionFormatters'
import { monthlyReviewApi } from './monthlyReviewApi'

const defaultPeriod = new Date().toISOString().slice(0, 7)

export function MonthlyReviewPage() {
  const [period, setPeriod] = useState(defaultPeriod)
  const reviewQuery = useQuery({ queryKey: ['monthly-review', period], queryFn: () => monthlyReviewApi.get(period) })

  return <>
    <header className="topbar monthly-review-topbar">
      <div><p className="eyebrow">TỔNG KẾT TÀI CHÍNH</p><h1>Nhìn lại tháng {monthLabel(period)}</h1><p className="page-subtitle">Số liệu được tính từ giao dịch đã ghi nhận. Bạn có thể lưu nhận xét và trọng tâm cho tháng tiếp theo, không làm thay đổi giao dịch hay số dư.</p></div>
      <label className="month-picker"><CalendarDays /><span>Tháng</span><input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label>
    </header>

    {reviewQuery.isPending && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tổng hợp tháng</span></div>}
    {reviewQuery.isError && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải tổng kết tháng</strong><p>{getApiErrorMessage(reviewQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void reviewQuery.refetch()}>Thử lại</button></div>}
    {reviewQuery.data && <MonthlyReviewContent key={period} period={period} review={reviewQuery.data} />}
  </>
}

function MonthlyReviewContent({ period, review }: { period: string; review: import('./monthlyReviewTypes').MonthlyReview }) {
  const [reflection, setReflection] = useState(review.reflection ?? '')
  const [nextMonthFocus, setNextMonthFocus] = useState(review.nextMonthFocus ?? '')
  const queryClient = useQueryClient()
  const saveMutation = useMutation({
    mutationFn: () => monthlyReviewApi.save(period, { reflection, nextMonthFocus }),
    onSuccess: (savedReview) => {
      queryClient.setQueryData(['monthly-review', period], savedReview)
      void queryClient.invalidateQueries({ queryKey: ['activity'] })
    },
  })

  return <main className="monthly-review-layout">
      <section className="monthly-review-summary" aria-label="Số liệu tháng">
        {review.currencySummaries.map((summary) => <article key={summary.currency} className="monthly-review-currency">
          <header><span><WalletCards /></span><div><strong>{summary.currency}</strong><small>Dòng tiền đã ghi nhận trong tháng</small></div></header>
          <div className="monthly-review-metrics"><Metric label="Khoản thu" value={formatCurrency(summary.monthlyIncome, summary.currency)} count={summary.incomeTransactionCount} icon={<TrendingUp />} tone="income" /><Metric label="Khoản chi" value={formatCurrency(summary.monthlyExpense, summary.currency)} count={summary.expenseTransactionCount} icon={<TrendingDown />} tone="expense" /><Metric label="Chênh lệch" value={formatCurrency(summary.monthlyNet, summary.currency)} count={null} icon={summary.monthlyNet >= 0 ? <TrendingUp /> : <TrendingDown />} tone={summary.monthlyNet >= 0 ? 'income' : 'expense'} /></div>
        </article>)}
        {review.currencySummaries.length === 0 && <article className="monthly-review-empty-summary"><WalletCards /><div><strong>Chưa có dòng tiền trong tháng này</strong><p>Ghi nhận khoản thu hoặc chi đầu tiên để xem tổng kết thực tế.</p></div></article>}
      </section>

      <section className="panel monthly-review-notes"><div className="section-heading"><div><p className="eyebrow">GHI NHẬN CỦA BẠN</p><h2>Tổng kết và tháng sau</h2></div>{review.reviewed && review.updatedAt && <span className="review-saved-state"><CheckCircle2 /> Đã lưu {formatDateTime(review.updatedAt)}</span>}</div>
        <form onSubmit={(event) => { event.preventDefault(); saveMutation.mutate() }}>
          {saveMutation.isError && <div className="form-alert" role="alert">{getApiErrorMessage(saveMutation.error, 'Chưa thể lưu tổng kết. Vui lòng thử lại.')}</div>}
          <label><span>Tháng này bạn làm tốt hoặc cần điều chỉnh điều gì?</span><textarea value={reflection} maxLength={1500} onChange={(event) => setReflection(event.target.value)} placeholder="Ví dụ: Đã giữ chi ăn uống trong ngân sách và giảm mua sắm không cần thiết." /></label>
          <label><span>Trọng tâm tháng sau</span><textarea value={nextMonthFocus} maxLength={500} onChange={(event) => setNextMonthFocus(event.target.value)} placeholder="Ví dụ: Dành trước 2.000.000 đ cho quỹ khẩn cấp vào ngày nhận lương." /></label>
          <footer><small>Chỉ lưu ghi chú cá nhân. Tổng thu, chi và số dư luôn được lấy từ dữ liệu giao dịch.</small><button className="primary-button" disabled={saveMutation.isPending}>{saveMutation.isPending ? <LoaderCircle className="spin" /> : <Save />} Lưu tổng kết</button></footer>
        </form>
      </section>

      <section className="panel monthly-review-insights"><div className="section-heading"><div><p className="eyebrow">NHẬN ĐỊNH TỪ DỮ LIỆU</p><h2>Điểm cần lưu ý</h2></div><Lightbulb /></div><p className="insights-caption">Các nhận định được tổng hợp có quy tắc từ dòng tiền, ngân sách và mục tiêu hiện có; đây không phải lời khuyên đầu tư.</p><div className="monthly-review-insight-list">{review.insights.map((insight) => <article key={insight.key} className={`insight-item is-${insight.severity.toLowerCase()}`}><span className="insight-icon">{insight.severity === 'SUCCESS' ? <CheckCircle2 /> : insight.severity === 'INFO' ? <Info /> : <CircleAlert />}</span><div><strong>{insight.title}</strong><p>{insight.message}</p></div></article>)}</div></section>
    </main>
}

function Metric({ label, value, count, icon, tone }: { label: string; value: string; count: number | null; icon: ReactNode; tone: 'income' | 'expense' }) {
  return <div className={`monthly-review-metric ${tone}`}><span>{icon}</span><small>{label}</small><strong>{value}</strong>{count !== null && <em>{count} giao dịch</em>}</div>
}

function monthLabel(period: string) {
  const [year, month] = period.split('-')
  return `tháng ${Number(month)}/${year}`
}
