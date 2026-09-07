import { CircleAlert, HandCoins, LoaderCircle, Plus, ReceiptText, UserRoundCheck, UsersRound, WalletCards, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { categoryApi } from '../categories/categoryApi'
import type { Category } from '../categories/categoryTypes'
import { formatCurrency } from '../transactions/transactionFormatters'
import { walletApi } from '../wallets/walletApi'
import { splitBillApi } from './splitBillApi'
import type { CreateSplitBillInput, RecordSplitBillPaymentInput, SplitBill, SplitBillParticipant, SplitBillStatus } from './splitBillTypes'

type ParticipantDraft = { id: string; name: string; contact: string; owedAmount: string }
type BillForm = { walletId: string; expenseCategoryId: string; name: string; totalAmount: string; payerShareAmount: string; description: string; notes: string; occurredAt: string; participants: ParticipantDraft[] }

const statusLabels: Record<SplitBillStatus, string> = {
  OPEN: 'Chưa thu',
  PARTIALLY_SETTLED: 'Đã thu một phần',
  SETTLED: 'Đã thu đủ',
  CANCELLED: 'Đã hủy',
}

function toLocalDateTime(instant?: string) {
  const value = instant ? new Date(instant) : new Date()
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
  return value.toISOString().slice(0, 16)
}

function newParticipant(): ParticipantDraft {
  return { id: crypto.randomUUID(), name: '', contact: '', owedAmount: '' }
}

export function SplitBillsPage() {
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [paymentTarget, setPaymentTarget] = useState<{ bill: SplitBill; participant: SplitBillParticipant } | null>(null)
  const [error, setError] = useState('')
  const billsQuery = useQuery({ queryKey: ['split-bills'], queryFn: splitBillApi.list })
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })

  const invalidateFinancialData = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['split-bills'] }),
    queryClient.invalidateQueries({ queryKey: ['transactions'] }),
    queryClient.invalidateQueries({ queryKey: ['wallets'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  ])
  const loading = billsQuery.isPending || walletsQuery.isPending || categoriesQuery.isPending
  const failed = billsQuery.isError || walletsQuery.isError || categoriesQuery.isError

  return <>
    <header className="topbar wallet-topbar">
      <div><p className="eyebrow">THEO DÕI KHOẢN CẦN THU</p><h1>Chia hóa đơn</h1><p className="page-subtitle">Ghi một khoản chi duy nhất, theo dõi từng người hoàn lại và đưa khoản thu về đúng ví tiền.</p></div>
      <button className="primary-button" onClick={() => setCreateOpen(true)}><Plus /> Tạo hóa đơn</button>
    </header>
    {error && <div className="form-alert page-alert" role="alert">{error}<button onClick={() => setError('')} aria-label="Đóng"><X /></button></div>}
    {loading && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải các hóa đơn đã chia</span></div>}
    {failed && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải hóa đơn</strong><p>{getApiErrorMessage(billsQuery.error ?? walletsQuery.error ?? categoriesQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => { void billsQuery.refetch(); void walletsQuery.refetch(); void categoriesQuery.refetch() }}>Thử lại</button></div>}
    {!loading && !failed && (billsQuery.data?.length ? <section className="split-bill-list">{billsQuery.data.map((bill) => <SplitBillRow key={bill.id} bill={bill} onRecord={(participant) => setPaymentTarget({ bill, participant })} />)}</section> : <div className="content-state"><UsersRound /><strong>Chưa có hóa đơn nào cần chia</strong><p>Tạo hóa đơn khi bạn thanh toán thay cho nhóm. Hệ thống sẽ giữ rõ phần của bạn và số tiền từng người cần hoàn lại.</p><button className="primary-button" onClick={() => setCreateOpen(true)}><Plus /> Tạo hóa đơn đầu tiên</button></div>)}
    {createOpen && <SplitBillFormModal wallets={walletsQuery.data ?? []} categories={categoriesQuery.data ?? []} onClose={() => setCreateOpen(false)} onSaved={() => { setCreateOpen(false); void invalidateFinancialData() }} />}
    {paymentTarget && <RecordPaymentModal bill={paymentTarget.bill} participant={paymentTarget.participant} wallets={walletsQuery.data ?? []} categories={categoriesQuery.data ?? []} onClose={() => setPaymentTarget(null)} onSaved={() => { setPaymentTarget(null); void invalidateFinancialData() }} />}
  </>
}

function SplitBillRow({ bill, onRecord }: { bill: SplitBill; onRecord: (participant: SplitBillParticipant) => void }) {
  return <article className="split-bill-row">
    <header><div className="split-bill-heading"><span className="split-bill-icon"><ReceiptText /></span><div><h2>{bill.name}</h2><p>{bill.description}</p></div></div><div className="split-bill-meta"><span className={`status-pill split-${bill.status.toLowerCase()}`}>{statusLabels[bill.status]}</span><time dateTime={bill.occurredAt}>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(bill.occurredAt))}</time></div></header>
    <div className="split-bill-summary"><div><span>Tổng hóa đơn</span><strong>{formatCurrency(bill.totalAmount, bill.currency)}</strong></div><div><span>Phần của bạn</span><strong>{formatCurrency(bill.payerShareAmount, bill.currency)}</strong></div><div><span>Cần thu lại</span><strong>{formatCurrency(bill.reimbursableAmount, bill.currency)}</strong></div><div><span>Đã thu</span><strong className="income-amount">{formatCurrency(bill.settledAmount, bill.currency)}</strong></div><div><span>Còn lại</span><strong className={bill.outstandingAmount > 0 ? 'expense-amount' : 'income-amount'}>{formatCurrency(bill.outstandingAmount, bill.currency)}</strong></div></div>
    <div className="split-participant-table-wrap"><table className="split-participant-table"><thead><tr><th>Người cần hoàn</th><th className="numeric-cell">Cần trả</th><th className="numeric-cell">Đã trả</th><th className="numeric-cell">Còn lại</th><th>Trạng thái</th><th aria-label="Thao tác" /></tr></thead><tbody>{bill.participants.map((participant) => <tr key={participant.id}><td><strong>{participant.name}</strong>{participant.contact && <small>{participant.contact}</small>}</td><td className="numeric-cell">{formatCurrency(participant.owedAmount, bill.currency)}</td><td className="numeric-cell income-amount">{formatCurrency(participant.settledAmount, bill.currency)}</td><td className="numeric-cell">{formatCurrency(participant.outstandingAmount, bill.currency)}</td><td><span className={`participant-status ${participant.status.toLowerCase()}`}>{participant.status === 'PAID' ? 'Đã trả đủ' : participant.status === 'PARTIALLY_PAID' ? 'Đã trả một phần' : 'Chưa trả'}</span></td><td className="split-action-cell">{participant.outstandingAmount > 0 ? <button className="secondary-button compact-button" onClick={() => onRecord(participant)}><HandCoins /> Ghi nhận</button> : <UserRoundCheck className="paid-icon" aria-label="Đã thu đủ" />}</td></tr>)}</tbody></table></div>
    {bill.notes && <footer><span>Ghi chú</span><p>{bill.notes}</p></footer>}
  </article>
}

function SplitBillFormModal({ wallets, categories, onClose, onSaved }: { wallets: Array<{ id: string; name: string; currency: string }>; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<BillForm>(() => ({ walletId: wallets[0]?.id ?? '', expenseCategoryId: categories.find((category) => category.categoryType === 'EXPENSE')?.id ?? '', name: '', totalAmount: '', payerShareAmount: '', description: '', notes: '', occurredAt: toLocalDateTime(), participants: [newParticipant()] }))
  const [error, setError] = useState('')
  const expenseCategories = useMemo(() => categories.filter((category) => category.categoryType === 'EXPENSE'), [categories])
  const participantsTotal = form.participants.reduce((sum, participant) => sum + (Number(participant.owedAmount) || 0), 0)
  const total = Number(form.totalAmount) || 0
  const payerShare = Number(form.payerShareAmount) || 0
  const reconciliation = total - payerShare - participantsTotal
  const mutation = useMutation({
    mutationFn: () => {
      if (!form.walletId || !form.expenseCategoryId) throw new Error('Hãy chọn ví tiền và danh mục chi.')
      if (!form.name.trim() || !form.description.trim()) throw new Error('Hãy nhập tên và nội dung hóa đơn.')
      if (!Number.isFinite(total) || total <= 0) throw new Error('Tổng hóa đơn phải lớn hơn 0.')
      if (!Number.isFinite(payerShare) || payerShare < 0) throw new Error('Phần của bạn không hợp lệ.')
      if (form.participants.some((participant) => !participant.name.trim() || !Number.isFinite(Number(participant.owedAmount)) || Number(participant.owedAmount) <= 0)) throw new Error('Mỗi người cần có tên và số tiền phải hoàn hợp lệ.')
      if (Math.abs(reconciliation) > 0.0001) throw new Error('Tổng hóa đơn phải bằng phần của bạn cộng với phần mọi người cần hoàn.')
      const input: CreateSplitBillInput = { walletId: form.walletId, expenseCategoryId: form.expenseCategoryId, name: form.name.trim(), totalAmount: total, payerShareAmount: payerShare, description: form.description.trim(), notes: form.notes.trim() || undefined, occurredAt: new Date(form.occurredAt).toISOString(), participants: form.participants.map((participant) => ({ name: participant.name.trim(), contact: participant.contact.trim() || undefined, owedAmount: Number(participant.owedAmount) })) }
      return splitBillApi.create(input, crypto.randomUUID())
    },
    onSuccess: onSaved,
    onError: (requestError) => setError(requestError instanceof Error ? requestError.message : getApiErrorMessage(requestError, 'Không thể tạo hóa đơn.')),
  })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  const updateParticipant = (id: string, key: keyof ParticipantDraft, value: string) => setForm((current) => ({ ...current, participants: current.participants.map((participant) => participant.id === id ? { ...participant, [key]: value } : participant) }))

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal split-bill-modal" role="dialog" aria-modal="true" aria-labelledby="split-bill-form-title"><header><div><p className="eyebrow">KHOẢN CHI CẦN HOÀN LẠI</p><h2 id="split-bill-form-title">Tạo hóa đơn chia tiền</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}<div className="form-row"><label><span>Ví đã thanh toán</span><select value={form.walletId} onChange={(event) => setForm({ ...form, walletId: event.target.value })}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {wallet.currency}</option>)}</select></label><label><span>Danh mục chi</span><select value={form.expenseCategoryId} onChange={(event) => setForm({ ...form, expenseCategoryId: event.target.value })}><option value="">Chọn danh mục</option>{expenseCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label></div><label><span>Tên hóa đơn</span><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Ăn tối cùng nhóm dự án" autoFocus /></label><div className="form-row"><label><span>Tổng hóa đơn</span><input type="number" min="0" step="0.01" value={form.totalAmount} onChange={(event) => setForm({ ...form, totalAmount: event.target.value })} placeholder="Ví dụ: 900000" /></label><label><span>Phần của bạn</span><input type="number" min="0" step="0.01" value={form.payerShareAmount} onChange={(event) => setForm({ ...form, payerShareAmount: event.target.value })} placeholder="Ví dụ: 300000" /></label></div><label><span>Nội dung giao dịch</span><input value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="Ví dụ: Thanh toán bữa tối cho cả nhóm" /></label><label><span>Thời điểm thanh toán</span><input type="datetime-local" value={form.occurredAt} onChange={(event) => setForm({ ...form, occurredAt: event.target.value })} /></label><fieldset className="split-participant-editor"><legend>Người cần hoàn lại</legend><div className="split-participant-editor-head"><span>Phần cần thu: {formatCurrency(participantsTotal, wallets.find((wallet) => wallet.id === form.walletId)?.currency ?? 'VND')}</span><span className={Math.abs(reconciliation) < 0.0001 ? 'balanced' : 'unbalanced'}>{Math.abs(reconciliation) < 0.0001 ? 'Đã khớp tổng tiền' : `Còn chênh ${formatCurrency(Math.abs(reconciliation), wallets.find((wallet) => wallet.id === form.walletId)?.currency ?? 'VND')}`}</span></div>{form.participants.map((participant, index) => <div className="split-participant-editor-row" key={participant.id}><strong>{index + 1}</strong><input value={participant.name} onChange={(event) => updateParticipant(participant.id, 'name', event.target.value)} placeholder="Tên người" /><input value={participant.owedAmount} onChange={(event) => updateParticipant(participant.id, 'owedAmount', event.target.value)} type="number" min="0" step="0.01" placeholder="Số tiền" /><input value={participant.contact} onChange={(event) => updateParticipant(participant.id, 'contact', event.target.value)} placeholder="Liên hệ (không bắt buộc)" /><button type="button" className="icon-button danger-icon" onClick={() => setForm((current) => ({ ...current, participants: current.participants.filter((item) => item.id !== participant.id) }))} disabled={form.participants.length === 1} aria-label={`Xóa ${participant.name || `người thứ ${index + 1}`}`}><X /></button></div>)}<button type="button" className="add-rule-item" onClick={() => setForm((current) => ({ ...current, participants: [...current.participants, newParticipant()] }))}><Plus /> Thêm người</button></fieldset><label><span>Ghi chú</span><textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} placeholder="Ví dụ: Đã gửi số tài khoản cho mọi người" /></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : 'Ghi chi và tạo hóa đơn'}</button></footer></form></section></div>
}

function RecordPaymentModal({ bill, participant, wallets, categories, onClose, onSaved }: { bill: SplitBill; participant: SplitBillParticipant; wallets: Array<{ id: string; name: string; currency: string }>; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const matchingWallets = wallets.filter((wallet) => wallet.currency === bill.currency)
  const incomeCategories = categories.filter((category) => category.categoryType === 'INCOME')
  const [form, setForm] = useState({ walletId: matchingWallets[0]?.id ?? '', incomeCategoryId: incomeCategories[0]?.id ?? '', amount: participant.outstandingAmount.toString(), notes: '', occurredAt: toLocalDateTime() })
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: () => {
      const amount = Number(form.amount)
      if (!form.walletId || !form.incomeCategoryId) throw new Error('Hãy chọn ví nhận tiền và danh mục thu.')
      if (!Number.isFinite(amount) || amount <= 0 || amount > participant.outstandingAmount) throw new Error('Số tiền hoàn lại phải lớn hơn 0 và không vượt số còn phải thu.')
      const input: RecordSplitBillPaymentInput = { walletId: form.walletId, incomeCategoryId: form.incomeCategoryId, amount, notes: form.notes.trim() || undefined, occurredAt: new Date(form.occurredAt).toISOString() }
      return splitBillApi.recordPayment(bill.id, participant.id, input, crypto.randomUUID())
    }, onSuccess: onSaved, onError: (requestError) => setError(requestError instanceof Error ? requestError.message : getApiErrorMessage(requestError, 'Không thể ghi nhận khoản hoàn lại.')),
  })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal payment-modal" role="dialog" aria-modal="true" aria-labelledby="payment-modal-title"><header><div><p className="eyebrow">GHI NHẬN KHOẢN HOÀN LẠI</p><h2 id="payment-modal-title">{participant.name} đã trả tiền</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}<div className="payment-context"><span>Hóa đơn</span><strong>{bill.name}</strong><span>Còn phải thu</span><strong>{formatCurrency(participant.outstandingAmount, bill.currency)}</strong></div><div className="form-row"><label><span>Ví nhận tiền</span><select value={form.walletId} onChange={(event) => setForm({ ...form, walletId: event.target.value })}><option value="">Chọn ví</option>{matchingWallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name}</option>)}</select></label><label><span>Danh mục thu</span><select value={form.incomeCategoryId} onChange={(event) => setForm({ ...form, incomeCategoryId: event.target.value })}><option value="">Chọn danh mục</option>{incomeCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label></div><label><span>Số tiền đã nhận</span><input type="number" min="0" max={participant.outstandingAmount} step="0.01" value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} /></label><label><span>Thời điểm nhận</span><input type="datetime-local" value={form.occurredAt} onChange={(event) => setForm({ ...form, occurredAt: event.target.value })} /></label><label><span>Ghi chú</span><textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} placeholder="Ví dụ: Đã nhận chuyển khoản" /></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : <><WalletCards /> Ghi nhận tiền về ví</>}</button></footer></form></section></div>
}
