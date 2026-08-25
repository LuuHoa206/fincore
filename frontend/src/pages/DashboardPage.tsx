import {
  ArrowDownLeft, ArrowUpRight, ChevronRight, CreditCard,
  PiggyBank, Plus, TrendingUp, WalletCards,
} from 'lucide-react'
import { useAuth } from '../features/auth/authContextState'

const jars = [
  { name: 'Chi tiêu thiết yếu', amount: 6_450_000, target: 8_000_000, color: '#0f8f72' },
  { name: 'Tiết kiệm', amount: 4_200_000, target: 5_000_000, color: '#2f6fed' },
  { name: 'Học tập', amount: 1_350_000, target: 2_500_000, color: '#d99018' },
]

const transactions = [
  { name: 'Lương tháng 8', category: 'Thu nhập', amount: 18_000_000, time: 'Hôm nay, 08:30', income: true },
  { name: 'Siêu thị WinMart', category: 'Ăn uống', amount: 684_000, time: 'Hôm qua, 19:12', income: false },
  { name: 'Khóa học Spring', category: 'Học tập', amount: 1_200_000, time: '22/08, 21:05', income: false },
  { name: 'Tiền điện', category: 'Hóa đơn', amount: 423_000, time: '21/08, 10:18', income: false },
]

const formatMoney = (value: number) => `${new Intl.NumberFormat('vi-VN').format(value)} đ`

export function DashboardPage() {
  const { user } = useAuth()
  const firstName = user?.displayName.trim().split(/\s+/).at(-1) ?? 'bạn'

  return (
    <>
      <header className="topbar">
        <div><p className="eyebrow">TỔNG QUAN TÀI CHÍNH</p><h1>Chào bạn, {firstName}</h1></div>
        <div className="top-actions"><span className="demo-badge">Dữ liệu minh họa</span></div>
      </header>

      <section className="summary-grid" id="overview">
        <article className="balance-card">
          <div className="card-heading"><span>Tổng tài sản</span><WalletCards /></div>
          <strong>{formatMoney(23_480_000)}</strong>
          <p><TrendingUp /> Tăng 8,4% so với tháng trước</p>
        </article>
        <article className="metric-card income">
          <span className="metric-icon"><ArrowDownLeft /></span>
          <div><small>Thu nhập tháng này</small><strong>{formatMoney(18_000_000)}</strong><p>1 nguồn thu</p></div>
        </article>
        <article className="metric-card expense">
          <span className="metric-icon"><ArrowUpRight /></span>
          <div><small>Đã chi tháng này</small><strong>{formatMoney(6_320_000)}</strong><p>35,1% thu nhập</p></div>
        </article>
        <article className="metric-card available">
          <span className="metric-icon"><PiggyBank /></span>
          <div><small>Còn có thể phân bổ</small><strong>{formatMoney(2_150_000)}</strong><p>Chưa vào hũ tiền</p></div>
        </article>
      </section>

      <div className="dashboard-grid">
        <section className="panel jars-panel" id="jars">
          <div className="section-heading"><div><p className="eyebrow">PHÂN BỔ NGÂN SÁCH</p><h2>Hũ tiền của bạn</h2></div></div>
          <div className="jar-list">
            {jars.map((jar) => {
              const percentage = Math.round((jar.amount / jar.target) * 100)
              return <article className="jar-row" key={jar.name}>
                <div className="jar-title"><span style={{ backgroundColor: jar.color }} /><strong>{jar.name}</strong><em>{percentage}%</em></div>
                <div className="progress"><span style={{ width: `${percentage}%`, backgroundColor: jar.color }} /></div>
                <div className="jar-values"><span>{formatMoney(jar.amount)}</span><small>/ {formatMoney(jar.target)}</small></div>
              </article>
            })}
          </div>
          <button className="secondary-button" disabled title="Sẽ mở ở milestone Hũ tiền"><Plus /> Tạo hũ tiền mới</button>
        </section>

        <section className="panel budget-panel">
          <div className="section-heading"><div><p className="eyebrow">THÁNG 8</p><h2>Ngân sách chi tiêu</h2></div><span className="section-icon"><ChevronRight /></span></div>
          <div className="budget-content">
            <div className="budget-ring"><span><strong>63%</strong><small>đã sử dụng</small></span></div>
            <div className="budget-copy"><strong>{formatMoney(6_320_000)}</strong><p>trên ngân sách {formatMoney(10_000_000)}</p><div><span /><small>Còn lại {formatMoney(3_680_000)}</small></div></div>
          </div>
          <div className="budget-note"><CreditCard /><span><strong>Ăn uống đang chi nhiều nhất</strong><small>Chiếm 42% tổng chi tiêu tháng này</small></span></div>
        </section>

        <section className="panel transactions-panel" id="transactions">
          <div className="section-heading"><div><p className="eyebrow">HOẠT ĐỘNG GẦN ĐÂY</p><h2>Giao dịch mới nhất</h2></div></div>
          <div className="transaction-list">
            {transactions.map((item) => <article key={`${item.name}-${item.time}`}>
              <span className={`transaction-icon ${item.income ? 'is-income' : ''}`}>{item.income ? <ArrowDownLeft /> : <ArrowUpRight />}</span>
              <div className="transaction-name"><strong>{item.name}</strong><small>{item.category}</small></div>
              <time>{item.time}</time>
              <strong className={item.income ? 'amount-income' : 'amount-expense'}>{item.income ? '+' : '-'}{formatMoney(item.amount)}</strong>
            </article>)}
          </div>
        </section>
      </div>
    </>
  )
}
