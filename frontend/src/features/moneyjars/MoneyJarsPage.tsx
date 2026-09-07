import { Archive, ArrowDownToLine, ArrowLeftRight, ArrowUpFromLine, LoaderCircle, Pencil, PiggyBank, Plus, ShieldCheck, X } from 'lucide-react'
import type { CSSProperties, FormEvent } from 'react'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { useAuth } from '../auth/authContextState'
import { formatCurrency } from '../transactions/transactionFormatters'
import { walletApi } from '../wallets/walletApi'
import { moneyJarApi } from './moneyJarApi'
import type { CreateMoneyJarInput, MoneyJar } from './moneyJarTypes'
import { TransferBetweenJarsModal } from './TransferBetweenJarsModal'

type JarForm = {
  name: string
  currency: string
  spendingLimit: string
  color: string
  icon: string
  allowNegative: boolean
}

const colorOptions = ['#0F8F72', '#1D7DB5', '#D97706', '#8B5CF6', '#D14B68']
const iconOptions = ['piggy-bank', 'shield-check', 'plane', 'home', 'graduation-cap']

export function MoneyJarsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [formTarget, setFormTarget] = useState<MoneyJar | 'new' | null>(null)
  const [allocationTarget, setAllocationTarget] = useState<MoneyJar | null>(null)
  const [transferOpen, setTransferOpen] = useState(false)
  const [actionError, setActionError] = useState('')
  const jarsQuery = useQuery({ queryKey: ['money-jars'], queryFn: moneyJarApi.list })
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const jars = jarsQuery.data ?? []
  const wallets = walletsQuery.data ?? []

  const remainingByCurrency = wallets.reduce<Record<string, number>>((totals, wallet) => {
    totals[wallet.currency] = (totals[wallet.currency] ?? 0) + wallet.currentBalance
    return totals
  }, {})
  jars.forEach((jar) => { remainingByCurrency[jar.currency] = (remainingByCurrency[jar.currency] ?? 0) - jar.allocatedBalance })
  const currencies = [...new Set(wallets.map((wallet) => wallet.currency))]
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['money-jars'] })
    void queryClient.invalidateQueries({ queryKey: ['wallets'] })
  }
  const archiveMutation = useMutation({
    mutationFn: moneyJarApi.archive,
    onSuccess: refresh,
    onError: (error) => setActionError(getApiErrorMessage(error, 'Khong the luu tru hu tien.')),
  })

  const isLoading = jarsQuery.isPending || walletsQuery.isPending
  const hasError = jarsQuery.isError || walletsQuery.isError

  return <>
    <header className="topbar wallet-topbar">
      <div><p className="eyebrow">PHAN BO MUC TIEU</p><h1>Hũ tiền</h1><p className="page-subtitle">Chia số dư đã có trong ví thành những khoản riêng cho quỹ khẩn cấp, du lịch và các mục tiêu quan trọng.</p></div>
      <div className="topbar-actions">
        {jars.length > 1 && <button className="secondary-button" onClick={() => setTransferOpen(true)}><ArrowLeftRight /> Chuyển giữa hũ</button>}
        <button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Tạo hũ tiền</button>
      </div>
    </header>

    {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}
    {isLoading && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải hũ tiền</span></div>}
    {hasError && <div className="content-state error-state"><PiggyBank /><strong>Chưa thể tải dữ liệu hũ tiền</strong><p>{getApiErrorMessage(jarsQuery.error ?? walletsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => { void jarsQuery.refetch(); void walletsQuery.refetch() }}>Thử lại</button></div>}
    {!isLoading && !hasError && <>
      <section className="jar-allocation-summary" aria-label="Số tiền còn có thể phân bổ">
        <div><span className="jar-summary-icon"><ShieldCheck /></span><div><small>TIỀN CHƯA GÁN MỤC ĐÍCH</small><strong>Phân bổ từ số dư ví, không chuyển tiền thật</strong></div></div>
        <div className="jar-available-list">{Object.entries(remainingByCurrency).map(([currency, amount]) => <span key={currency}>{formatCurrency(amount, currency)} còn có thể phân bổ</span>)}{currencies.length === 0 && <span>Hãy tạo và ghi nhận số dư cho ít nhất một ví trước.</span>}</div>
      </section>
      {jars.length === 0 ? <div className="content-state"><PiggyBank /><strong>Bạn chưa có hũ tiền nào</strong><p>Hũ tiền giúp tách bạch mục đích sử dụng mà không làm thay đổi tổng số dư ví.</p><button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Tạo hũ đầu tiên</button></div> : <section className="jar-grid">
        {jars.map((jar) => <MoneyJarCard key={jar.id} jar={jar} available={remainingByCurrency[jar.currency] ?? 0} archivePending={archiveMutation.isPending} onEdit={() => setFormTarget(jar)} onAllocate={() => setAllocationTarget(jar)} onArchive={() => archiveMutation.mutate(jar.id)} />)}
      </section>}
    </>}

    {formTarget && <MoneyJarFormModal jar={formTarget === 'new' ? null : formTarget} defaultCurrency={currencies[0] ?? user?.preferredCurrency ?? 'VND'} currencies={currencies} onClose={() => setFormTarget(null)} onSaved={() => { setFormTarget(null); refresh() }} />}
    {allocationTarget && <AllocationModal jar={allocationTarget} availableToAllocate={remainingByCurrency[allocationTarget.currency] ?? 0} onClose={() => setAllocationTarget(null)} onSaved={() => { setAllocationTarget(null); refresh() }} />}
    {transferOpen && <TransferBetweenJarsModal jars={jars} onClose={() => setTransferOpen(false)} onSaved={() => { setTransferOpen(false); refresh() }} />}
  </>
}

function MoneyJarCard({ jar, available, onEdit, onAllocate, onArchive, archivePending }: {
  jar: MoneyJar
  available: number
  onEdit: () => void
  onAllocate: () => void
  onArchive: () => void
  archivePending: boolean
}) {
  const target = jar.spendingLimit ?? 0
  const progress = target > 0 ? Math.min((jar.allocatedBalance / target) * 100, 100) : 0
  const style = { '--jar-color': jar.color ?? '#0F8F72' } as CSSProperties
  return <article className="jar-card" style={style}>
    <header><span className="jar-icon"><PiggyBank /></span><div><strong>{jar.name}</strong><small>{jar.currency}{jar.allowNegative ? ' · Cho phép âm' : ''}</small></div><div className="row-actions"><button className="icon-button" onClick={onEdit} title="Chỉnh sửa hũ" aria-label={`Chỉnh sửa ${jar.name}`}><Pencil /></button><button className="icon-button danger-icon" onClick={onArchive} title="Lưu trữ hũ" aria-label={`Lưu trữ ${jar.name}`} disabled={archivePending || jar.allocatedBalance !== 0}><Archive /></button></div></header>
    <div className="jar-balance"><small>ĐÃ PHÂN BỔ</small><strong>{formatCurrency(jar.allocatedBalance, jar.currency)}</strong></div>
    {target > 0 && <div className="jar-progress"><div><span>Mục tiêu</span><b>{formatCurrency(target, jar.currency)}</b></div><div className="progress-track"><i style={{ width: `${progress}%` }} /></div><small>{progress.toFixed(0)}% mục tiêu</small></div>}
    <footer><span>Còn có thể gán: {formatCurrency(Math.max(available, 0), jar.currency)}</span><button className="secondary-button" onClick={onAllocate}><ArrowDownToLine /> Phân bổ</button></footer>
  </article>
}

function MoneyJarFormModal({ jar, defaultCurrency, currencies, onClose, onSaved }: { jar: MoneyJar | null; defaultCurrency: string; currencies: string[]; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<JarForm>({ name: jar?.name ?? '', currency: jar?.currency ?? defaultCurrency, spendingLimit: jar?.spendingLimit?.toString() ?? '', color: jar?.color ?? '#0F8F72', icon: jar?.icon ?? 'piggy-bank', allowNegative: jar?.allowNegative ?? false })
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: async () => {
      const spendingLimit = form.spendingLimit === '' ? undefined : Number(form.spendingLimit)
      if (form.name.trim().length < 2) throw new Error('Tên hũ cần ít nhất 2 ký tự.')
      if (!Number.isFinite(spendingLimit ?? 0) || (spendingLimit ?? 0) < 0) throw new Error('Mục tiêu phải là số không âm.')
      const input: CreateMoneyJarInput = { name: form.name.trim(), currency: form.currency.trim().toUpperCase(), spendingLimit, color: form.color, icon: form.icon, allowNegative: form.allowNegative }
      return jar ? moneyJarApi.update(jar.id, input) : moneyJarApi.create(input)
    },
    onSuccess: onSaved,
    onError: (requestError) => setError(getApiErrorMessage(requestError, jar ? 'Không thể cập nhật hũ tiền.' : 'Không thể tạo hũ tiền.')),
  })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  const currenciesForForm = [...new Set([form.currency, ...currencies])]

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="jar-form-title"><header><div><p className="eyebrow">MỤC TIÊU PHÂN BỔ</p><h2 id="jar-form-title">{jar ? 'Chỉnh sửa hũ tiền' : 'Tạo hũ tiền'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}<label><span>Tên hũ</span><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Quỹ khẩn cấp" autoFocus /></label><div className="form-row"><label><span>Tiền tệ</span><select value={form.currency} disabled={!!jar} onChange={(event) => setForm({ ...form, currency: event.target.value })}>{currenciesForForm.map((currency) => <option key={currency}>{currency}</option>)}</select></label><label><span>Mục tiêu</span><input type="number" min="0" step="1" value={form.spendingLimit} onChange={(event) => setForm({ ...form, spendingLimit: event.target.value })} placeholder="Không bắt buộc" /></label></div><div className="form-row"><label><span>Màu nhận diện</span><select value={form.color} onChange={(event) => setForm({ ...form, color: event.target.value })}>{colorOptions.map((color) => <option key={color} value={color}>{color}</option>)}</select></label><label><span>Biểu tượng</span><select value={form.icon} onChange={(event) => setForm({ ...form, icon: event.target.value })}>{iconOptions.map((icon) => <option key={icon}>{icon}</option>)}</select></label></div><label className="check-row"><input type="checkbox" checked={form.allowNegative} onChange={(event) => setForm({ ...form, allowNegative: event.target.checked })} /><span><strong>Cho phép số dư âm</strong><small>Dùng cho trường hợp theo dõi nghĩa vụ hoặc khoản tạm ứng.</small></span></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : jar ? 'Lưu thay đổi' : 'Tạo hũ tiền'}</button></footer></form></section></div>
}

function AllocationModal({ jar, availableToAllocate, onClose, onSaved }: { jar: MoneyJar; availableToAllocate: number; onClose: () => void; onSaved: () => void }) {
  const [mode, setMode] = useState<'allocate' | 'release'>('allocate')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const limit = mode === 'allocate' ? Math.max(availableToAllocate, 0) : jar.allocatedBalance
  const mutation = useMutation({
    mutationFn: async () => {
      const value = Number(amount)
      if (!Number.isFinite(value) || value <= 0) throw new Error('Nhập số tiền lớn hơn 0.')
      return mode === 'allocate' ? moneyJarApi.allocate(jar.id, value) : moneyJarApi.release(jar.id, value)
    },
    onSuccess: onSaved,
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể cập nhật phân bổ.')),
  })
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal allocation-modal" role="dialog" aria-modal="true" aria-labelledby="allocation-title"><header><div><p className="eyebrow">CẬP NHẬT PHÂN BỔ</p><h2 id="allocation-title">{jar.name}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={(event) => { event.preventDefault(); setError(''); mutation.mutate() }}>{error && <div className="form-alert" role="alert">{error}</div>}<div className="segmented-control allocation-mode"><button className={mode === 'allocate' ? 'selected' : ''} type="button" onClick={() => setMode('allocate')}><ArrowDownToLine /> Phân bổ vào hũ</button><button className={mode === 'release' ? 'selected' : ''} type="button" onClick={() => setMode('release')}><ArrowUpFromLine /> Giải phóng</button></div><div className="allocation-balance"><span>{mode === 'allocate' ? 'Còn có thể phân bổ' : 'Đang nằm trong hũ'}</span><strong>{formatCurrency(limit, jar.currency)}</strong><small>Thao tác này không làm thay đổi số dư thật của ví.</small></div><label><span>Số tiền</span><input type="number" min="1" max={limit} step="1" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Nhập số tiền" autoFocus /></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending || limit <= 0}>{mutation.isPending ? <LoaderCircle className="spin" /> : mode === 'allocate' ? 'Xác nhận phân bổ' : 'Xác nhận giải phóng'}</button></footer></form></section></div>
}
