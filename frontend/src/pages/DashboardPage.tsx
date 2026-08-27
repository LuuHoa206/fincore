import { useQuery } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowUpRight, ChevronRight, CircleAlert, CircleCheck, CircleDollarSign, Info, Lightbulb, LoaderCircle, Plus, Target, TrendingUp, WalletCards } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/authContextState'
import { reportingApi } from '../features/reporting/reportingApi'
import { formatCurrency, formatDateTime } from '../features/transactions/transactionFormatters'
import type { Transaction } from '../features/transactions/transactionTypes'
import type { FinancialInsight, FinancialInsightSeverity } from '../features/reporting/reportingTypes'

export function DashboardPage() {
  const { user } = useAuth()
  const dashboardQuery = useQuery({ queryKey: ['dashboard'], queryFn: () => reportingApi.dashboard() })
  const insightQuery = useQuery({ queryKey: ['monthly-insights'], queryFn: () => reportingApi.monthlyInsights() })
  const report = dashboardQuery.data
  const preferredCurrency = user?.preferredCurrency ?? 'VND'
  const summary = report?.currencySummaries.find((item) => item.currency === preferredCurrency) ?? report?.currencySummaries[0]
  const firstName = user?.displayName.trim().split(/\s+/).at(-1) ?? 'bạn'

  return <>
    <header className="topbar">
      <div><p className="eyebrow">TỔNG QUAN TÀI CHÍNH</p><h1>Chào bạn, {firstName}</h1><p className="page-subtitle">Số liệu tháng này được tổng hợp trực tiếp từ giao dịch đã ghi nhận.</p></div>
      <Link className="primary-button" to="/transactions"><Plus /> Thêm giao dịch</Link>
    </header>

    {dashboardQuery.isError && <div className="form-alert page-alert" role="alert">Không thể tải báo cáo tài chính. Hãy thử làm mới trang.</div>}
    <section className="summary-grid" id="overview">
      <SummaryCard className="balance-card" label="Tổng số dư các ví" icon={<WalletCards />} value={summary ? formatCurrency(summary.walletBalance, summary.currency) : '0 ₫'} detail={`${report?.activeWalletCount ?? 0} ví đang hoạt động`} pending={dashboardQuery.isPending} />
      <SummaryCard className="metric-card income" label="Thu nhập tháng này" icon={<ArrowDownLeft />} value={summary ? formatCurrency(summary.monthlyIncome, summary.currency) : '0 ₫'} detail={`${summary?.incomeTransactionCount ?? 0} khoản thu`} pending={dashboardQuery.isPending} />
      <SummaryCard className="metric-card expense" label="Đã chi tháng này" icon={<ArrowUpRight />} value={summary ? formatCurrency(summary.monthlyExpense, summary.currency) : '0 ₫'} detail={`${summary?.expenseTransactionCount ?? 0} khoản chi`} pending={dashboardQuery.isPending} />
      <SummaryCard className="metric-card available" label="Dòng tiền ròng" icon={<CircleDollarSign />} value={summary ? formatCurrency(summary.monthlyNet, summary.currency) : '0 ₫'} detail="Thu nhập trừ chi tiêu" pending={dashboardQuery.isPending} />
    </section>

    <div className="dashboard-grid">
      <section className="panel wallets-summary-panel"><div className="section-heading"><div><p className="eyebrow">NGUỒN TIỀN</p><h2>Phân bổ tiền của bạn</h2></div><Link className="text-button" to="/jars">Quản lý hũ <ChevronRight /></Link></div>
        {dashboardQuery.isPending && <div className="inline-loading"><LoaderCircle className="spin" /> Đang tải dữ liệu</div>}
        {!dashboardQuery.isPending && <div className="wallet-summary-list">
          <article><span><WalletCards /></span><div><strong>Sẵn sàng phân bổ</strong><small>{summary?.currency ?? preferredCurrency}</small></div><b>{formatCurrency(summary?.availableToAllocate ?? 0, summary?.currency ?? preferredCurrency)}</b></article>
          <article><span><CircleDollarSign /></span><div><strong>Đã phân bổ vào hũ</strong><small>{report?.activeJarCount ?? 0} hũ đang hoạt động</small></div><b>{formatCurrency(summary?.allocatedToJars ?? 0, summary?.currency ?? preferredCurrency)}</b></article>
        </div>}
      </section>

      <section className="panel budget-panel"><div className="section-heading"><div><p className="eyebrow">KẾ HOẠCH TÀI CHÍNH</p><h2>Ngân sách và mục tiêu</h2></div><Link className="text-button" to="/budgets">Xem ngân sách <ChevronRight /></Link></div><div className="roadmap-card"><Target /><strong>{report?.budgetAlertCount ?? 0} ngân sách cần chú ý</strong><p>{report?.openSavingGoalCount ?? 0} mục tiêu tiết kiệm đang theo dõi. Tạo ngân sách theo tháng để chủ động kiểm soát chi tiêu.</p><Link to="/goals">Xem mục tiêu tiết kiệm</Link></div></section>

      <section className="panel insights-panel">
        <div className="section-heading"><div><p className="eyebrow">TRỢ LÝ PHÂN TÍCH</p><h2>Tóm tắt tài chính tháng</h2></div><Lightbulb className="insights-heading-icon" /></div>
        <p className="insights-caption">Nhận xét được tạo từ các giao dịch, ngân sách và mục tiêu bạn đã ghi nhận.</p>
        {insightQuery.isPending && <div className="inline-loading"><LoaderCircle className="spin" /> Đang tổng hợp dữ liệu</div>}
        {insightQuery.isError && <div className="form-alert" role="alert">Không thể tải tóm tắt tài chính. Hãy thử làm mới trang.</div>}
        {!insightQuery.isPending && !insightQuery.isError && <div className="insight-list">
          {insightQuery.data?.insights.map((insight) => <InsightItem key={insight.key} insight={insight} />)}
        </div>}
      </section>

      <section className="panel transactions-panel" id="transactions"><div className="section-heading"><div><p className="eyebrow">HOẠT ĐỘNG GẦN ĐÂY</p><h2>Giao dịch mới nhất</h2></div><Link className="text-button" to="/transactions">Xem tất cả <ChevronRight /></Link></div>
        {dashboardQuery.isPending && <div className="inline-loading"><LoaderCircle className="spin" /> Đang tải giao dịch</div>}
        {!dashboardQuery.isPending && !report?.recentTransactions.length && <div className="dashboard-empty"><ArrowUpRight /><span>Chưa có giao dịch nào</span><Link to="/transactions">Ghi nhận giao dịch đầu tiên</Link></div>}
        {!!report?.recentTransactions.length && <div className="transaction-list">{report.recentTransactions.map((transaction) => <DashboardTransaction key={transaction.id} transaction={transaction} />)}</div>}
      </section>
    </div>
  </>
}

function InsightItem({ insight }: { insight: FinancialInsight }) {
  const icon = insightIcon(insight.severity)
  return <article className={`insight-item is-${insight.severity.toLowerCase()}`}>
    <span className="insight-icon">{icon}</span>
    <div><strong>{insight.title}</strong><p>{insight.message}</p></div>
  </article>
}

function insightIcon(severity: FinancialInsightSeverity) {
  if (severity === 'SUCCESS') return <CircleCheck />
  if (severity === 'WARNING' || severity === 'DANGER') return <CircleAlert />
  return <Info />
}

function SummaryCard({ className, label, icon, value, detail, pending }: { className: string; label: string; icon: ReactNode; value: string; detail: string; pending: boolean }) {
  if (className === 'balance-card') {
    return <article className={className}><div className="card-heading"><span>{label}</span>{icon}</div><strong>{pending ? 'Đang tải...' : value}</strong><p><TrendingUp /> {detail}</p></article>
  }
  return <article className={className}><span className="metric-icon">{icon}</span><div><small>{label}</small><strong>{pending ? '...' : value}</strong><p>{detail}</p></div></article>
}

function DashboardTransaction({ transaction }: { transaction: Transaction }) {
  const income = transaction.transactionType === 'INCOME'
  return <article><span className={`transaction-icon ${income ? 'is-income' : ''}`}>{income ? <ArrowDownLeft /> : <ArrowUpRight />}</span><div className="transaction-name"><strong>{transaction.description}</strong><small>{transaction.walletName} · {formatDateTime(transaction.occurredAt)}</small></div><strong className={income ? 'amount-income' : 'amount-expense'}>{income ? '+' : '-'}{formatCurrency(transaction.amount, transaction.currency)}</strong></article>
}
