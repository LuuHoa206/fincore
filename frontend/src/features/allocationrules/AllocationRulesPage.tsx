import { CircleAlert, LoaderCircle, Pencil, Plus, SlidersHorizontal, Trash2, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency } from '../transactions/transactionFormatters'
import { moneyJarApi } from '../moneyjars/moneyJarApi'
import type { MoneyJar } from '../moneyjars/moneyJarTypes'
import { allocationRuleApi } from './allocationRuleApi'
import type { AllocationRule, AllocationRuleInput } from './allocationRuleTypes'

type RuleItemForm = { jarId: string; percentage: string }
type RuleForm = { name: string; currency: string; enabled: boolean; items: RuleItemForm[] }

export function AllocationRulesPage() {
  const queryClient = useQueryClient()
  const [formTarget, setFormTarget] = useState<AllocationRule | 'new' | null>(null)
  const [error, setError] = useState('')
  const rulesQuery = useQuery({ queryKey: ['allocation-rules'], queryFn: allocationRuleApi.list })
  const jarsQuery = useQuery({ queryKey: ['money-jars'], queryFn: moneyJarApi.list })
  const removeMutation = useMutation({
    mutationFn: allocationRuleApi.remove,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['allocation-rules'] }),
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể xóa quy tắc.')),
  })
  const rules = rulesQuery.data ?? []
  const jars = jarsQuery.data ?? []

  return <>
    <header className="topbar wallet-topbar">
      <div><p className="eyebrow">TỰ ĐỘNG PHÂN BỔ KHOẢN THU</p><h1>Quy tắc chia tiền</h1><p className="page-subtitle">Thiết lập tỷ lệ cho từng hũ. Khi ghi nhận thu nhập, bạn có thể chọn áp dụng quy tắc tương ứng với tiền tệ của ví.</p></div>
      <button className="primary-button" disabled={!jars.length} onClick={() => setFormTarget('new')}><Plus /> Tạo quy tắc</button>
    </header>
    {error && <div className="form-alert page-alert" role="alert">{error}<button onClick={() => setError('')} aria-label="Đóng"><X /></button></div>}
    {(rulesQuery.isPending || jarsQuery.isPending) && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải quy tắc phân bổ</span></div>}
    {(rulesQuery.isError || jarsQuery.isError) && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải quy tắc</strong><p>{getApiErrorMessage(rulesQuery.error ?? jarsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => { void rulesQuery.refetch(); void jarsQuery.refetch() }}>Thử lại</button></div>}
    {!rulesQuery.isPending && !jarsQuery.isPending && !rulesQuery.isError && !jarsQuery.isError && <RulesContent rules={rules} jars={jars} isDeleting={removeMutation.isPending} onCreate={() => setFormTarget('new')} onEdit={setFormTarget} onDelete={(id) => removeMutation.mutate(id)} />}
    {formTarget && <AllocationRuleModal rule={formTarget === 'new' ? null : formTarget} jars={jars} onClose={() => setFormTarget(null)} onSaved={() => { setFormTarget(null); void queryClient.invalidateQueries({ queryKey: ['allocation-rules'] }) }} />}
  </>
}

function RulesContent({ rules, jars, isDeleting, onCreate, onEdit, onDelete }: { rules: AllocationRule[]; jars: MoneyJar[]; isDeleting: boolean; onCreate: () => void; onEdit: (rule: AllocationRule) => void; onDelete: (id: string) => void }) {
  const activeRules = rules.filter((rule) => rule.enabled).length
  return <><section className="allocation-rule-summary"><span><SlidersHorizontal /></span><div><small>QUY TẮC ĐANG BẬT</small><strong>{activeRules} quy tắc tự chia tiền</strong></div><p>Mỗi tiền tệ chỉ có một quy tắc đang bật để khoản thu được phân bổ rõ ràng, không chồng chéo.</p></section>
    {!jars.length ? <div className="content-state"><SlidersHorizontal /><strong>Cần có hũ tiền trước</strong><p>Tạo ít nhất một hũ tiền để thiết lập tỷ lệ phân bổ cho khoản thu.</p></div> : rules.length === 0 ? <div className="content-state"><SlidersHorizontal /><strong>Chưa có quy tắc chia tiền</strong><p>Tạo quy tắc đầu tiên để tự phân bổ lương, tiền thưởng hoặc các khoản thu khác vào từng hũ.</p><button className="primary-button" onClick={onCreate}><Plus /> Tạo quy tắc</button></div> : <section className="allocation-rule-grid">{rules.map((rule) => <article className="allocation-rule-card" key={rule.id}><header><div><span className="rule-currency">{rule.currency}</span><div><strong>{rule.name}</strong><small>{rule.enabled ? 'Đang áp dụng cho khoản thu mới' : 'Đang tạm dừng'}</small></div></div><div className="row-actions"><button className="icon-button" onClick={() => onEdit(rule)} title="Chỉnh sửa quy tắc" aria-label={`Chỉnh sửa ${rule.name}`}><Pencil /></button><button className="icon-button danger-icon" onClick={() => onDelete(rule.id)} title="Xóa quy tắc" aria-label={`Xóa ${rule.name}`} disabled={isDeleting}><Trash2 /></button></div></header><div className="rule-total"><span>Tỷ lệ đã phân bổ</span><b>{rule.totalPercentage}%</b></div><ul>{rule.items.map((item) => <li key={item.jarId}><span>{item.jarName}</span><strong>{item.percentage}%</strong></li>)}</ul></article>)}</section>}</>
}

function AllocationRuleModal({ rule, jars, onClose, onSaved }: { rule: AllocationRule | null; jars: MoneyJar[]; onClose: () => void; onSaved: () => void }) {
  const availableCurrencies = [...new Set(jars.map((jar) => jar.currency))]
  const initialCurrency = rule?.currency ?? availableCurrencies[0] ?? 'VND'
  const [form, setForm] = useState<RuleForm>(() => ({ name: rule?.name ?? '', currency: initialCurrency, enabled: rule?.enabled ?? true, items: rule?.items.map((item) => ({ jarId: item.jarId, percentage: String(item.percentage) })) ?? [{ jarId: jars.find((jar) => jar.currency === initialCurrency)?.id ?? '', percentage: '' }] }))
  const [error, setError] = useState('')
  const selectableJars = jars.filter((jar) => jar.currency === form.currency)
  const total = useMemo(() => form.items.reduce((sum, item) => sum + (Number(item.percentage) || 0), 0), [form.items])
  const mutation = useMutation({
    mutationFn: async () => {
      const name = form.name.trim()
      const usedJars = new Set<string>()
      if (name.length < 2) throw new Error('Tên quy tắc cần ít nhất 2 ký tự.')
      if (!form.items.length || form.items.some((item) => !item.jarId || !Number.isFinite(Number(item.percentage)) || Number(item.percentage) <= 0)) throw new Error('Mỗi hũ cần có tỷ lệ lớn hơn 0.')
      if (form.items.some((item) => usedJars.has(item.jarId) || !usedJars.add(item.jarId))) throw new Error('Không chọn một hũ nhiều lần trong cùng quy tắc.')
      if (total > 100) throw new Error('Tổng tỷ lệ không được vượt quá 100%.')
      const input: AllocationRuleInput = { name, currency: form.currency, enabled: form.enabled, items: form.items.map((item) => ({ jarId: item.jarId, percentage: Number(item.percentage) })) }
      return rule ? allocationRuleApi.update(rule.id, { name: input.name, enabled: input.enabled, items: input.items }) : allocationRuleApi.create(input)
    }, onSuccess: onSaved, onError: (requestError) => setError(getApiErrorMessage(requestError, rule ? 'Không thể cập nhật quy tắc.' : 'Không thể tạo quy tắc.')),
  })
  const updateItem = (index: number, next: Partial<RuleItemForm>) => setForm((current) => ({ ...current, items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...next } : item) }))
  const changeCurrency = (currency: string) => setForm((current) => ({ ...current, currency, items: [{ jarId: jars.find((jar) => jar.currency === currency)?.id ?? '', percentage: '' }] }))
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal allocation-rule-modal" role="dialog" aria-modal="true" aria-labelledby="rule-form-title"><header><div><p className="eyebrow">PHÂN BỔ KHOẢN THU</p><h2 id="rule-form-title">{rule ? 'Chỉnh sửa quy tắc' : 'Tạo quy tắc chia tiền'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert" role="alert">{error}</div>}<div className="form-row"><label><span>Tên quy tắc</span><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: Chia lương tháng" autoFocus /></label><label><span>Tiền tệ</span><select value={form.currency} disabled={!!rule} onChange={(event) => changeCurrency(event.target.value)}>{availableCurrencies.map((currency) => <option key={currency}>{currency}</option>)}</select></label></div><label className="check-row"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span><strong>Kích hoạt quy tắc</strong><small>Khoản thu cùng tiền tệ có thể chọn áp dụng quy tắc này.</small></span></label><div className="rule-item-editor"><div className="rule-item-heading"><span>Hũ tiền và tỷ lệ</span><b>{total.toFixed(2)}%</b></div>{form.items.map((item, index) => <div className="rule-item-row" key={`${item.jarId}-${index}`}><select value={item.jarId} onChange={(event) => updateItem(index, { jarId: event.target.value })}>{selectableJars.map((jar) => <option key={jar.id} value={jar.id}>{jar.name} · {formatCurrency(jar.allocatedBalance, jar.currency)}</option>)}</select><input type="number" min="0.01" max="100" step="0.01" value={item.percentage} onChange={(event) => updateItem(index, { percentage: event.target.value })} placeholder="Tỷ lệ" /><span>%</span><button className="icon-button danger-icon" type="button" onClick={() => setForm((current) => ({ ...current, items: current.items.filter((_, itemIndex) => itemIndex !== index) }))} disabled={form.items.length === 1} aria-label="Bỏ hũ"><Trash2 /></button></div>)}<button className="add-rule-item" type="button" onClick={() => setForm((current) => ({ ...current, items: [...current.items, { jarId: selectableJars.find((jar) => !current.items.some((item) => item.jarId === jar.id))?.id ?? '', percentage: '' }] }))} disabled={form.items.length >= selectableJars.length}><Plus /> Thêm hũ</button></div><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending || total > 100}>{mutation.isPending ? <LoaderCircle className="spin" /> : rule ? 'Lưu thay đổi' : 'Tạo quy tắc'}</button></footer></form></section></div>
}
