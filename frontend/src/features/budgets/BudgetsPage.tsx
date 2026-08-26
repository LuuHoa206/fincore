import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Archive, CircleAlert, LoaderCircle, Pencil, Plus, Target, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { useAuth } from '../auth/authContextState'
import { categoryApi } from '../categories/categoryApi'
import { formatCurrency } from '../transactions/transactionFormatters'
import { budgetApi } from './budgetApi'
import type { Budget, CreateBudgetInput, UpdateBudgetInput } from './budgetTypes'

function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}

const statusLabels = {
  ON_TRACK: 'Trong hạn mức',
  WARNING: 'Sắp vượt hạn mức',
  EXCEEDED: 'Đã vượt hạn mức',
} as const

export function BudgetsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [period, setPeriod] = useState(currentMonth)
  const [formTarget, setFormTarget] = useState<Budget | 'new' | null>(null)
  const [archiveTarget, setArchiveTarget] = useState<Budget | null>(null)
  const [actionError, setActionError] = useState('')
  const budgetsQuery = useQuery({ queryKey: ['budgets', period], queryFn: () => budgetApi.list(period) })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })
  const archiveMutation = useMutation({
    mutationFn: budgetApi.archive,
    onSuccess: () => {
      setArchiveTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['budgets', period] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể lưu trữ ngân sách.')),
  })

  const expenseCategories = useMemo(
    () => (categoriesQuery.data ?? []).filter((category) => category.categoryType === 'EXPENSE'),
    [categoriesQuery.data],
  )
  const loading = budgetsQuery.isPending || categoriesQuery.isPending
  const failed = budgetsQuery.isError || categoriesQuery.isError

  return <>
    <header className="topbar wallet-topbar">
      <div><p className="eyebrow">KIỂM SOÁT CHI TIÊU</p><h1>Ngân sách tháng</h1><p className="page-subtitle">Đặt hạn mức cho từng danh mục chi. Số đã chi được tính từ giao dịch đã ghi nhận trong tháng, không nhập thủ công.</p></div>
      <div className="budget-actions"><label className="month-picker"><span>Tháng theo dõi</span><input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label><button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Thêm ngân sách</button></div>
    </header>

    {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}
    {loading && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải ngân sách</span></div>}
    {failed && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải ngân sách</strong><p>{getApiErrorMessage(budgetsQuery.error ?? categoriesQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => { void budgetsQuery.refetch(); void categoriesQuery.refetch() }}>Thử lại</button></div>}
    {!loading && !failed && <>
      {budgetsQuery.data?.length === 0 ? <div className="content-state"><Target /><strong>Chưa có ngân sách trong tháng này</strong><p>Chọn một danh mục chi và thiết lập hạn mức để bắt đầu theo dõi.</p><button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Tạo ngân sách</button></div> : <section className="budget-list">
        {budgetsQuery.data?.map((budget) => <BudgetCard key={budget.id} budget={budget} onEdit={() => setFormTarget(budget)} onArchive={() => setArchiveTarget(budget)} />)}
      </section>}
    </>}

    {formTarget && <BudgetFormModal budget={formTarget === 'new' ? null : formTarget} period={period} categories={expenseCategories} defaultCurrency={user?.preferredCurrency ?? 'VND'} onClose={() => setFormTarget(null)} onSaved={() => { setFormTarget(null); void queryClient.invalidateQueries({ queryKey: ['budgets', period] }) }} />}
    {archiveTarget && <ConfirmArchiveModal budget={archiveTarget} isPending={archiveMutation.isPending} onCancel={() => setArchiveTarget(null)} onConfirm={() => archiveMutation.mutate(archiveTarget.id)} />}
  </>
}

function BudgetCard({ budget, onEdit, onArchive }: { budget: Budget; onEdit: () => void; onArchive: () => void }) {
  const usage = Math.max(0, budget.usagePercentage)
  const progress = Math.min(usage, 100)
  return <article className={`budget-card budget-${budget.status.toLowerCase()}`}>
    <header><div className="budget-category"><span style={{ color: budget.categoryColor ?? '#0F8F72', backgroundColor: `${budget.categoryColor ?? '#0F8F72'}18` }}><Target /></span><div><strong>{budget.categoryName}</strong><small>{budget.currency} · {new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(new Date(`${budget.periodStart}T00:00:00`))}</small></div></div><div className="row-actions"><button className="icon-button" onClick={onEdit} title="Sửa ngân sách" aria-label={`Sửa ${budget.categoryName}`}><Pencil /></button><button className="icon-button danger-icon" onClick={onArchive} title="Lưu trữ ngân sách" aria-label={`Lưu trữ ${budget.categoryName}`}><Archive /></button></div></header>
    <div className="budget-card-values"><div><small>Đã chi</small><strong>{formatCurrency(budget.spentAmount, budget.currency)}</strong></div><div><small>Hạn mức</small><strong>{formatCurrency(budget.limitAmount, budget.currency)}</strong></div><div className={budget.remainingAmount < 0 ? 'negative-amount' : ''}><small>Còn lại</small><strong>{formatCurrency(budget.remainingAmount, budget.currency)}</strong></div></div>
    <div className="budget-card-progress"><div><span>{usage.toFixed(0)}% đã dùng</span><b className={`budget-status status-${budget.status.toLowerCase()}`}>{budget.status !== 'ON_TRACK' && <AlertTriangle />}{statusLabels[budget.status]}</b></div><div className="progress-track"><i style={{ width: `${progress}%` }} /></div><small>Cảnh báo khi đạt {budget.warningThreshold}% hạn mức.</small></div>
  </article>
}

function BudgetFormModal({ budget, period, categories, defaultCurrency, onClose, onSaved }: { budget: Budget | null; period: string; categories: { id: string; name: string }[]; defaultCurrency: string; onClose: () => void; onSaved: () => void }) {
  const [categoryId, setCategoryId] = useState(budget?.categoryId ?? '')
  const [limitAmount, setLimitAmount] = useState(budget?.limitAmount.toString() ?? '')
  const [currency, setCurrency] = useState(budget?.currency ?? defaultCurrency)
  const [warningThreshold, setWarningThreshold] = useState(budget?.warningThreshold.toString() ?? '80')
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: async () => {
      const limit = Number(limitAmount)
      const threshold = Number(warningThreshold)
      if (!Number.isFinite(limit) || limit <= 0) throw new Error('Hạn mức phải lớn hơn 0.')
      if (!Number.isInteger(threshold) || threshold < 1 || threshold > 100) throw new Error('Mức cảnh báo cần từ 1 đến 100%.')
      if (budget) {
        const input: UpdateBudgetInput = { limitAmount: limit, warningThreshold: threshold }
        return budgetApi.update(budget.id, input)
      }
      if (!categoryId) throw new Error('Hãy chọn danh mục chi.')
      const input: CreateBudgetInput = { categoryId, periodStart: `${period}-01`, limitAmount: limit, currency: currency.trim().toUpperCase(), warningThreshold: threshold }
      return budgetApi.create(input)
    },
    onSuccess: onSaved,
    onError: (requestError) => setError(requestError instanceof Error ? requestError.message : getApiErrorMessage(requestError, 'Không thể lưu ngân sách.')),
  })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="budget-form-title"><header><div><p className="eyebrow">KẾ HOẠCH CHI TIÊU</p><h2 id="budget-form-title">{budget ? 'Điều chỉnh ngân sách' : 'Thêm ngân sách'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}{!budget && <label><span>Danh mục chi</span><select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} autoFocus><option value="">Chọn danh mục</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>}<div className="form-row"><label><span>Hạn mức</span><input type="number" min="1" step="1" value={limitAmount} onChange={(event) => setLimitAmount(event.target.value)} placeholder="Ví dụ: 3000000" autoFocus={!!budget} /></label><label><span>Tiền tệ</span><input maxLength={3} disabled={!!budget} value={currency} onChange={(event) => setCurrency(event.target.value)} /></label></div><label><span>Cảnh báo khi dùng đến (%)</span><input type="number" min="1" max="100" step="1" value={warningThreshold} onChange={(event) => setWarningThreshold(event.target.value)} /></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : budget ? 'Lưu thay đổi' : 'Tạo ngân sách'}</button></footer></form></section></div>
}

function ConfirmArchiveModal({ budget, isPending, onCancel, onConfirm }: { budget: Budget; isPending: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop" role="presentation"><section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="archive-budget-title"><span className="confirm-icon"><Archive /></span><h2 id="archive-budget-title">Lưu trữ ngân sách “{budget.categoryName}”?</h2><p>Ngân sách sẽ không còn xuất hiện trong tháng đang xem. Giao dịch và lịch sử chi tiêu vẫn được giữ nguyên.</p><footer><button className="plain-button" onClick={onCancel}>Giữ lại</button><button className="danger-button" onClick={onConfirm} disabled={isPending}>{isPending ? <LoaderCircle className="spin" /> : 'Lưu trữ'}</button></footer></section></div>
}
