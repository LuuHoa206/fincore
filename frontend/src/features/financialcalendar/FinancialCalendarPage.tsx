import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, ChevronLeft, ChevronRight, CircleAlert, Clock3, LoaderCircle, ReceiptText, Repeat2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency } from '../transactions/transactionFormatters'
import { financialCalendarApi } from './financialCalendarApi'
import type { FinancialCalendarEntry } from './financialCalendarTypes'

const weekdayLabels = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

export function FinancialCalendarPage() {
  const [period, setPeriod] = useState(currentPeriod())
  const calendarQuery = useQuery({ queryKey: ['financial-calendar', period], queryFn: () => financialCalendarApi.get(period) })
  const weeks = useMemo(() => calendarQuery.data ? calendarWeeks(period, calendarQuery.data.days) : [], [calendarQuery.data, period])

  return <>
    <header className="topbar financial-calendar-topbar">
      <div><p className="eyebrow">DÒNG TIỀN THEO THỜI GIAN</p><h1>Lịch tài chính</h1><p className="page-subtitle">Theo dõi giao dịch đã ghi nhận và các khoản thu chi định kỳ sắp tới. Khoản dự kiến chỉ để xem, chưa làm thay đổi số dư ví.</p></div>
      <div className="calendar-toolbar"><button className="icon-button" onClick={() => setPeriod(changeMonth(period, -1))} aria-label="Tháng trước"><ChevronLeft /></button><label className="month-picker"><CalendarDays /><span>Tháng</span><input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label><button className="icon-button" onClick={() => setPeriod(changeMonth(period, 1))} aria-label="Tháng sau"><ChevronRight /></button></div>
    </header>

    <section className="financial-calendar-legend panel" aria-label="Chú thích lịch tài chính"><div><span className="calendar-legend-dot actual" />Đã ghi nhận: giao dịch đã được đăng vào sổ cái và ảnh hưởng số dư.</div><div><span className="calendar-legend-dot scheduled" />Dự kiến: kỳ lặp sắp tới, chưa tạo giao dịch.</div><div className="calendar-legend-links"><Link to="/transactions"><ReceiptText /> Ghi giao dịch</Link><Link to="/recurring"><Repeat2 /> Quản lý lịch thu chi</Link></div></section>

    {calendarQuery.isPending && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải lịch tài chính</span></div>}
    {calendarQuery.isError && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải lịch tài chính</strong><p>{getApiErrorMessage(calendarQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void calendarQuery.refetch()}>Thử lại</button></div>}
    {calendarQuery.data && <section className="financial-calendar panel" aria-label={`Lịch tài chính tháng ${period}`}><div className="financial-calendar-grid">
      {weekdayLabels.map((label) => <div className="financial-calendar-weekday" key={label}>{label}</div>)}
      {weeks.flat().map((day, index) => day ? <CalendarDay key={day.date} day={day} timeZone={calendarQuery.data.timeZone} /> : <div className="financial-calendar-empty-day" key={`empty-${index}`} />)}
    </div></section>}
  </>
}

function CalendarDay({ day, timeZone }: { day: { date: string; entries: FinancialCalendarEntry[] }; timeZone: string }) {
  const today = new Date().toLocaleDateString('en-CA', { timeZone })
  return <article className={`financial-calendar-day ${day.date === today ? 'is-today' : ''}`}><header><time dateTime={day.date}>{Number(day.date.slice(-2))}</time>{day.date === today && <span>Hôm nay</span>}</header><div className="financial-calendar-day-events">{day.entries.map((entry) => <CalendarEntry key={entry.key} entry={entry} timeZone={timeZone} />)}</div></article>
}

function CalendarEntry({ entry, timeZone }: { entry: FinancialCalendarEntry; timeZone: string }) {
  const kindLabel = entry.kind === 'ACTUAL' ? 'Đã ghi nhận' : entry.autoRecord ? 'Sẽ tự ghi' : 'Dự kiến'
  const time = new Intl.DateTimeFormat('vi-VN', { timeZone, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(entry.occurredAt))
  const amountPrefix = entry.transactionType === 'INCOME' ? '+' : entry.transactionType === 'EXPENSE' ? '-' : ''
  return <div className={`financial-calendar-entry ${entry.kind.toLowerCase()} ${entry.transactionType.toLowerCase()}`} title={`${entry.title} · ${kindLabel}`}><div className="calendar-entry-heading"><span>{entry.kind === 'ACTUAL' ? <ReceiptText /> : <Clock3 />}</span><strong>{entry.title}</strong></div><small>{time} · {entry.walletName}{entry.categoryName ? ` · ${entry.categoryName}` : ''}</small><b>{amountPrefix}{formatCurrency(entry.amount, entry.currency)}</b><em>{kindLabel}</em></div>
}

function calendarWeeks(period: string, days: Array<{ date: string; entries: FinancialCalendarEntry[] }>) {
  const [year, month] = period.split('-').map(Number)
  const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
  const cells: Array<{ date: string; entries: FinancialCalendarEntry[] } | null> = [...Array(firstWeekday).fill(null), ...days]
  while (cells.length % 7 !== 0) cells.push(null)
  return Array.from({ length: cells.length / 7 }, (_, index) => cells.slice(index * 7, index * 7 + 7))
}

function currentPeriod() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function changeMonth(period: string, offset: number) {
  const [year, month] = period.split('-').map(Number)
  const next = new Date(year, month - 1 + offset, 1)
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
}
