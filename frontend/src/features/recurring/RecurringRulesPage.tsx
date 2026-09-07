import { CalendarClock, CircleAlert, Clock3, LoaderCircle, Pencil, Play, Plus, Power, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { categoryApi } from '../categories/categoryApi'
import type { CategoryType } from '../categories/categoryTypes'
import { formatCurrency } from '../transactions/transactionFormatters'
import { walletApi } from '../wallets/walletApi'
import { recurringApi } from './recurringApi'
import type { RecurringFrequency, RecurringRule, RecurringRuleInput } from './recurringTypes'

const frequencyLabels: Record<RecurringFrequency, string> = { DAILY: 'Hàng ngày', WEEKLY: 'Hàng tuần', MONTHLY: 'Hàng tháng' }

type RuleForm = { name: string; walletId: string; categoryId: string; transactionType: CategoryType; amount: string; description: string; notes: string; frequency: RecurringFrequency; nextRunAt: string; autoRecord: boolean; enabled: boolean; applyAllocationRule: boolean }

function toLocalDateTime(instant: string) {
  const value = new Date(instant)
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
  return value.toISOString().slice(0, 16)
}

function defaultDateTime() {
  const value = new Date()
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
  return value.toISOString().slice(0, 16)
}

export function RecurringRulesPage() {
  const queryClient = useQueryClient()
  const [formTarget, setFormTarget] = useState<RecurringRule | 'new' | null>(null)
  const [error, setError] = useState('')
  const [pageLoadedAt] = useState(() => Date.now())
  const rulesQuery = useQuery({ queryKey: ['recurring-rules'], queryFn: recurringApi.list })
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })
  const invalidate = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['recurring-rules'] }),
    queryClient.invalidateQueries({ queryKey: ['transactions'] }),
    queryClient.invalidateQueries({ queryKey: ['wallets'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  ])
  const recordMutation = useMutation({ mutationFn: recurringApi.record, onSuccess: () => void invalidate(), onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể ghi nhận giao dịch định kỳ.')) })
  const disableMutation = useMutation({ mutationFn: recurringApi.disable, onSuccess: () => void invalidate(), onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể tạm dừng quy tắc.')) })
  const loading = rulesQuery.isPending || walletsQuery.isPending || categoriesQuery.isPending
  const failed = rulesQuery.isError || walletsQuery.isError || categoriesQuery.isError

  return <>
    <header className="topbar wallet-topbar"><div><p className="eyebrow">THEO DÕI KHOẢN THU CHI LẶP LẠI</p><h1>Giao dịch định kỳ</h1><p className="page-subtitle">Lập lịch cho tiền thuê nhà, lương, trả góp hoặc các khoản lặp lại. Mặc định chỉ nhắc việc, không tự ý thay đổi số dư.</p></div><button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Thêm quy tắc</button></header>
    {error && <div className="form-alert page-alert" role="alert">{error}<button onClick={() => setError('')} aria-label="Đóng"><X /></button></div>}
    {loading && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải giao dịch định kỳ</span></div>}
    {failed && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải giao dịch định kỳ</strong><p>{getApiErrorMessage(rulesQuery.error ?? walletsQuery.error ?? categoriesQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => { void rulesQuery.refetch(); void walletsQuery.refetch(); void categoriesQuery.refetch() }}>Thử lại</button></div>}
    {!loading && !failed && (rulesQuery.data?.length ? <section className="recurring-rule-grid">{rulesQuery.data.map((rule) => <RuleCard key={rule.id} rule={rule} now={pageLoadedAt} isPending={recordMutation.isPending || disableMutation.isPending} onEdit={() => setFormTarget(rule)} onRecord={() => recordMutation.mutate(rule.id)} onDisable={() => disableMutation.mutate(rule.id)} />)}</section> : <div className="content-state"><CalendarClock /><strong>Chưa có giao dịch định kỳ</strong><p>Thêm các khoản lặp lại để biết kỳ tiếp theo và ghi nhận nhanh khi đến hạn.</p><button className="primary-button" onClick={() => setFormTarget('new')}><Plus /> Thêm quy tắc</button></div>)}
    {formTarget && <RuleModal rule={formTarget === 'new' ? null : formTarget} wallets={walletsQuery.data ?? []} categories={categoriesQuery.data ?? []} onClose={() => setFormTarget(null)} onSaved={() => { setFormTarget(null); void invalidate() }} />}
  </>
}

function RuleCard({ rule, now, isPending, onEdit, onRecord, onDisable }: { rule: RecurringRule; now: number; isPending: boolean; onEdit: () => void; onRecord: () => void; onDisable: () => void }) {
  const due = rule.enabled && new Date(rule.nextRunAt).getTime() <= now
  return <article className={`recurring-rule-card ${rule.enabled ? '' : 'recurring-rule-disabled'}`}>
    <header><span className={`recurring-rule-icon ${rule.transactionType.toLowerCase()}`}><CalendarClock /></span><div><strong>{rule.name}</strong><small>{rule.transactionType === 'INCOME' ? 'Khoản thu' : 'Khoản chi'} · {frequencyLabels[rule.frequency]}</small></div><div className="row-actions"><button className="icon-button" onClick={onEdit} title="Chỉnh sửa quy tắc" aria-label={`Chỉnh sửa ${rule.name}`}><Pencil /></button>{rule.enabled && <button className="icon-button danger-icon" onClick={onDisable} title="Tạm dừng quy tắc" aria-label={`Tạm dừng ${rule.name}`} disabled={isPending}><Power /></button>}</div></header>
    <div className="recurring-rule-amount"><span>{rule.categoryName} · {rule.walletName}</span><strong>{rule.transactionType === 'INCOME' ? '+' : '-'}{formatCurrency(rule.amount, rule.currency)}</strong></div>
    <dl><div><dt>Kỳ kế tiếp</dt><dd><Clock3 /><time dateTime={rule.nextRunAt}>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(rule.nextRunAt))}</time></dd></div><div><dt>Ghi nhận tự động</dt><dd>{rule.autoRecord && rule.enabled ? 'Đang bật' : 'Ghi thủ công'}</dd></div></dl>
    <p className="recurring-rule-description">{rule.description}</p>
    <footer>{due ? <><span className="due-badge">Đã đến hạn</span><button className="secondary-button" onClick={onRecord} disabled={isPending}>{isPending ? <LoaderCircle className="spin" /> : <Play />} Ghi nhận kỳ này</button></> : <span>{rule.enabled ? 'Chưa đến kỳ tiếp theo' : 'Đã tạm dừng'}</span>}</footer>
  </article>
}

function RuleModal({ rule, wallets, categories, onClose, onSaved }: { rule: RecurringRule | null; wallets: Array<{ id: string; name: string; currency: string }>; categories: Array<{ id: string; name: string; categoryType: CategoryType }>; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<RuleForm>(() => ({ name: rule?.name ?? '', walletId: rule?.walletId ?? wallets[0]?.id ?? '', categoryId: rule?.categoryId ?? '', transactionType: rule?.transactionType ?? 'EXPENSE', amount: rule?.amount.toString() ?? '', description: rule?.description ?? '', notes: rule?.notes ?? '', frequency: rule?.frequency ?? 'MONTHLY', nextRunAt: rule ? toLocalDateTime(rule.nextRunAt) : defaultDateTime(), autoRecord: rule?.autoRecord ?? false, enabled: rule?.enabled ?? true, applyAllocationRule: rule?.applyAllocationRule ?? false }))
  const [error, setError] = useState('')
  const visibleCategories = useMemo(() => categories.filter((category) => category.categoryType === form.transactionType), [categories, form.transactionType])
  const mutation = useMutation({
    mutationFn: async () => {
      const amount = Number(form.amount)
      if (!form.name.trim()) throw new Error('Hãy đặt tên cho quy tắc.')
      if (!form.walletId || !form.categoryId) throw new Error('Hãy chọn ví và danh mục phù hợp.')
      if (!Number.isFinite(amount) || amount <= 0) throw new Error('Số tiền phải lớn hơn 0.')
      if (!form.description.trim()) throw new Error('Hãy nhập nội dung giao dịch.')
      const input: RecurringRuleInput = { name: form.name.trim(), walletId: form.walletId, categoryId: form.categoryId, transactionType: form.transactionType, amount, description: form.description.trim(), notes: form.notes.trim() || undefined, frequency: form.frequency, nextRunAt: new Date(form.nextRunAt).toISOString(), autoRecord: form.autoRecord, enabled: form.enabled, applyAllocationRule: form.transactionType === 'INCOME' && form.applyAllocationRule }
      return rule ? recurringApi.update(rule.id, input) : recurringApi.create(input)
    }, onSuccess: onSaved, onError: (requestError) => setError(requestError instanceof Error ? requestError.message : getApiErrorMessage(requestError, 'Không thể lưu quy tắc.')),
  })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  const changeType = (transactionType: CategoryType) => setForm((current) => ({ ...current, transactionType, categoryId: categories.find((category) => category.categoryType === transactionType)?.id ?? '', applyAllocationRule: transactionType === 'INCOME' && current.applyAllocationRule }))

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal recurring-rule-modal" role="dialog" aria-modal="true" aria-labelledby="recurring-rule-title"><header><div><p className="eyebrow">LỊCH THU CHI</p><h2 id="recurring-rule-title">{rule ? 'Chỉnh sửa giao dịch định kỳ' : 'Thêm giao dịch định kỳ'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}<label><span>Tên quy tắc</span><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Tiền thuê nhà hàng tháng" autoFocus /></label><fieldset className="type-toggle"><legend>Loại giao dịch</legend><label className={form.transactionType === 'EXPENSE' ? 'active-expense' : ''}><input type="radio" checked={form.transactionType === 'EXPENSE'} onChange={() => changeType('EXPENSE')} />Khoản chi</label><label className={form.transactionType === 'INCOME' ? 'active-income' : ''}><input type="radio" checked={form.transactionType === 'INCOME'} onChange={() => changeType('INCOME')} />Khoản thu</label></fieldset><div className="form-row"><label><span>Ví tiền</span><select value={form.walletId} onChange={(event) => setForm({ ...form, walletId: event.target.value })}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {wallet.currency}</option>)}</select></label><label><span>Danh mục</span><select value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })}><option value="">Chọn danh mục</option>{visibleCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label></div><div className="form-row"><label><span>Số tiền</span><input type="number" min="0.0001" step="0.01" value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} placeholder="Ví dụ: 5000000" /></label><label><span>Lặp lại</span><select value={form.frequency} onChange={(event) => setForm({ ...form, frequency: event.target.value as RecurringFrequency })}>{Object.entries(frequencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div><label><span>Thời điểm kỳ đầu</span><input type="datetime-local" value={form.nextRunAt} onChange={(event) => setForm({ ...form, nextRunAt: event.target.value })} /></label><label><span>Nội dung giao dịch</span><input value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="Ví dụ: Thanh toán tiền thuê nhà" /></label><label><span>Ghi chú</span><textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} placeholder="Thông tin bổ sung, nếu có" /></label><label className="check-row"><input type="checkbox" checked={form.autoRecord} onChange={(event) => setForm({ ...form, autoRecord: event.target.checked })} /><span><strong>Tự ghi nhận khi đến hạn</strong><small>Chỉ bật khi bạn muốn hệ thống tự thay đổi số dư ví theo lịch này.</small></span></label>{form.transactionType === 'INCOME' && <label className="check-row"><input type="checkbox" checked={form.applyAllocationRule} onChange={(event) => setForm({ ...form, applyAllocationRule: event.target.checked })} /><span><strong>Áp dụng quy tắc chia tiền</strong><small>Khoản thu sẽ được chia vào các hũ theo quy tắc đang dùng.</small></span></label>}<label className="check-row"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span><strong>Kích hoạt quy tắc</strong><small>Quy tắc tạm dừng sẽ không được tự ghi nhận.</small></span></label><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : rule ? 'Lưu thay đổi' : 'Thêm quy tắc'}</button></footer></form></section></div>
}
