import { useQuery } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowUpRight, ChevronRight, CircleDollarSign, LoaderCircle, Plus, TrendingUp, WalletCards } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/authContextState'
import { transactionApi } from '../features/transactions/transactionApi'
import { formatCurrency, formatDateTime } from '../features/transactions/transactionFormatters'
import type { Transaction } from '../features/transactions/transactionTypes'
import { walletApi } from '../features/wallets/walletApi'

export function DashboardPage() {
  const { user } = useAuth()
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const transactionsQuery = useQuery({ queryKey: ['transactions'], queryFn: transactionApi.list })
  const firstName = user?.displayName.trim().split(/\s+/).at(-1) ?? 'bạn'
  const currency = user?.preferredCurrency ?? 'VND'
  const wallets = walletsQuery.data ?? []
  const transactions = transactionsQuery.data ?? []
  const currentMonth = new Date().getMonth()
  const currentYear = new Date().getFullYear()
  const currentMonthTransactions = transactions.filter((transaction) => {
    const occurredAt = new Date(transaction.occurredAt)
    return occurredAt.getMonth() === currentMonth && occurredAt.getFullYear() === currentYear && transaction.status === 'POSTED'
  })
  const totalBalance = wallets.reduce((total, wallet) => total + wallet.currentBalance, 0)
  const monthlyIncome = totalByType(currentMonthTransactions, 'INCOME')
  const monthlyExpense = totalByType(currentMonthTransactions, 'EXPENSE')
  const isLoading = walletsQuery.isPending || transactionsQuery.isPending
  const errorMessage = walletsQuery.isError || transactionsQuery.isError ? 'Không thể tải toàn bộ dữ liệu tài chính. Hãy thử làm mới trang.' : ''

  return <>
    <header className="topbar">
      <div><p className="eyebrow">TỔNG QUAN TÀI CHÍNH</p><h1>Chào bạn, {firstName}</h1><p className="page-subtitle">Theo dõi số dư, thu nhập và chi tiêu từ các giao dịch đã ghi nhận.</p></div>
      <Link className="primary-button" to="/transactions"><Plus /> Thêm giao dịch</Link>
    </header>

    {errorMessage && <div className="form-alert page-alert" role="alert">{errorMessage}</div>}
    <section className="summary-grid" id="overview">
      <article className="balance-card"><div className="card-heading"><span>Tổng số dư các ví</span><WalletCards /></div><strong>{isLoading ? 'Đang tải...' : formatCurrency(totalBalance, currency)}</strong><p><TrendingUp /> {wallets.length} ví đang hoạt động</p></article>
      <article className="metric-card income"><span className="metric-icon"><ArrowDownLeft /></span><div><small>Thu nhập tháng này</small><strong>{isLoading ? '...' : formatCurrency(monthlyIncome, currency)}</strong><p>{currentMonthTransactions.filter((item) => item.transactionType === 'INCOME').length} khoản thu</p></div></article>
      <article className="metric-card expense"><span className="metric-icon"><ArrowUpRight /></span><div><small>Đã chi tháng này</small><strong>{isLoading ? '...' : formatCurrency(monthlyExpense, currency)}</strong><p>{currentMonthTransactions.filter((item) => item.transactionType === 'EXPENSE').length} khoản chi</p></div></article>
      <article className="metric-card available"><span className="metric-icon"><CircleDollarSign /></span><div><small>Dòng tiền ròng tháng này</small><strong>{isLoading ? '...' : formatCurrency(monthlyIncome - monthlyExpense, currency)}</strong><p>Thu nhập trừ chi tiêu</p></div></article>
    </section>

    <div className="dashboard-grid">
      <section className="panel wallets-summary-panel"><div className="section-heading"><div><p className="eyebrow">NGUỒN TIỀN</p><h2>Ví tiền của bạn</h2></div><Link className="text-button" to="/wallets">Quản lý ví <ChevronRight /></Link></div>
        {walletsQuery.isPending && <div className="inline-loading"><LoaderCircle className="spin" /> Đang tải ví</div>}
        {!walletsQuery.isPending && wallets.length === 0 && <div className="dashboard-empty"><WalletCards /><span>Chưa có ví nào</span><Link to="/wallets">Tạo ví đầu tiên</Link></div>}
        {!!wallets.length && <div className="wallet-summary-list">{wallets.slice(0, 4).map((wallet) => <article key={wallet.id}><span><WalletCards /></span><div><strong>{wallet.name}</strong><small>{wallet.currency}</small></div><b>{formatCurrency(wallet.currentBalance, wallet.currency)}</b></article>)}</div>}
      </section>

      <section className="panel budget-panel"><div className="section-heading"><div><p className="eyebrow">TÍNH NĂNG TIẾP THEO</p><h2>Hũ tiền và ngân sách</h2></div></div><div className="roadmap-card"><CircleDollarSign /><strong>Đang chuẩn bị</strong><p>Sau khi dòng tiền đã được ghi nhận ổn định, bạn sẽ có thể phân bổ tiền vào hũ và theo dõi hạn mức chi tiêu.</p><span>Milestone tiếp theo</span></div></section>

      <section className="panel transactions-panel" id="transactions"><div className="section-heading"><div><p className="eyebrow">HOẠT ĐỘNG GẦN ĐÂY</p><h2>Giao dịch mới nhất</h2></div><Link className="text-button" to="/transactions">Xem tất cả <ChevronRight /></Link></div>
        {transactionsQuery.isPending && <div className="inline-loading"><LoaderCircle className="spin" /> Đang tải giao dịch</div>}
        {!transactionsQuery.isPending && transactions.length === 0 && <div className="dashboard-empty"><ArrowUpRight /><span>Chưa có giao dịch nào</span><Link to="/transactions">Ghi nhận giao dịch đầu tiên</Link></div>}
        {!!transactions.length && <div className="transaction-list">{transactions.slice(0, 5).map((transaction) => <DashboardTransaction key={transaction.id} transaction={transaction} />)}</div>}
      </section>
    </div>
  </>
}

function DashboardTransaction({ transaction }: { transaction: Transaction }) {
  const income = transaction.transactionType === 'INCOME'
  return <article><span className={`transaction-icon ${income ? 'is-income' : ''}`}>{income ? <ArrowDownLeft /> : <ArrowUpRight />}</span><div className="transaction-name"><strong>{transaction.description}</strong><small>{transaction.walletName} · {formatDateTime(transaction.occurredAt)}</small></div><strong className={income ? 'amount-income' : 'amount-expense'}>{income ? '+' : '-'}{formatCurrency(transaction.amount, transaction.currency)}</strong></article>
}

function totalByType(transactions: Transaction[], type: 'INCOME' | 'EXPENSE') {
  return transactions.filter((transaction) => transaction.transactionType === type).reduce((total, transaction) => total + transaction.amount, 0)
}
