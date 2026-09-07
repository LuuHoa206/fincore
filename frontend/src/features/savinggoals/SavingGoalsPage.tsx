import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CircleAlert, LoaderCircle, Pause, Play, Plus, Target, Trash2, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { useState } from 'react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { moneyJarApi } from '../moneyjars/moneyJarApi'
import { formatCurrency } from '../transactions/transactionFormatters'
import { savingGoalApi } from './savingGoalApi'
import type { SavingGoal } from './savingGoalTypes'

const labels = { ACTIVE: 'Đang theo dõi', PAUSED: 'Tạm dừng', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy' } as const

export function SavingGoalsPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [error, setError] = useState('')
  const goalsQuery = useQuery({ queryKey: ['saving-goals'], queryFn: savingGoalApi.list })
  const jarsQuery = useQuery({ queryKey: ['money-jars'], queryFn: moneyJarApi.list })
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'PAUSED' | 'CANCELLED' }) => savingGoalApi.changeStatus(id, status),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['saving-goals'] }),
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể cập nhật mục tiêu.')),
  })
  const loading = goalsQuery.isPending || jarsQuery.isPending
  const failed = goalsQuery.isError || jarsQuery.isError

  return <>
    <header className="topbar wallet-topbar"><div><p className="eyebrow">MỤC TIÊU DÀI HẠN</p><h1>Mục tiêu tiết kiệm</h1><p className="page-subtitle">Theo dõi tiến độ từ chính số tiền đã phân bổ vào Hũ. Không có số dư thủ công thứ hai để tránh lệch dữ liệu.</p></div><button className="primary-button" onClick={() => setFormOpen(true)}><Plus /> Tạo mục tiêu</button></header>
    {error && <div className="form-alert page-alert" role="alert">{error}<button onClick={() => setError('')} aria-label="Đóng"><X /></button></div>}
    {loading && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải mục tiêu</span></div>}
    {failed && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải mục tiêu</strong><p>{getApiErrorMessage(goalsQuery.error ?? jarsQuery.error, 'Vui lòng thử lại.')}</p></div>}
    {!loading && !failed && (goalsQuery.data?.length ? <section className="goal-grid">{goalsQuery.data.map((goal) => <GoalCard key={goal.id} goal={goal} pending={statusMutation.isPending} onChange={(status) => statusMutation.mutate({ id: goal.id, status })} />)}</section> : <div className="content-state"><Target /><strong>Chưa có mục tiêu tiết kiệm</strong><p>Hãy tạo một Hũ tiền, phân bổ số dư và gắn mục tiêu để theo dõi tiến độ thực tế.</p><button className="primary-button" onClick={() => setFormOpen(true)}><Plus /> Tạo mục tiêu đầu tiên</button></div>)}
    {formOpen && <GoalFormModal jars={jarsQuery.data ?? []} onClose={() => setFormOpen(false)} onSaved={() => { setFormOpen(false); void queryClient.invalidateQueries({ queryKey: ['saving-goals'] }) }} />}
  </>
}

function GoalCard({ goal, pending, onChange }: { goal: SavingGoal; pending: boolean; onChange: (status: 'ACTIVE' | 'PAUSED' | 'CANCELLED') => void }) {
  const progress = Math.min(Math.max(goal.progressPercentage, 0), 100)
  return <article className={`goal-card goal-${goal.status.toLowerCase()}`}><header><span className="goal-icon"><Target /></span><div><strong>{goal.name}</strong><small>Hũ: {goal.jarName}</small></div><b>{labels[goal.status]}</b></header><div className="goal-amount"><small>Đã tích lũy</small><strong>{formatCurrency(goal.currentAmount, goal.currency)}</strong><span>trên {formatCurrency(goal.targetAmount, goal.currency)}</span></div><div className="goal-progress"><div className="progress-track"><i style={{ width: `${progress}%` }} /></div><span>{progress.toFixed(0)}% · Còn {formatCurrency(goal.remainingAmount, goal.currency)}</span></div><dl>{goal.targetDate && <><div><dt>Đích đến</dt><dd>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(`${goal.targetDate}T00:00:00`))}</dd></div><div><dt>Cần góp mỗi tháng</dt><dd>{goal.suggestedMonthlyContribution ? formatCurrency(goal.suggestedMonthlyContribution, goal.currency) : 'Đã đạt mục tiêu'}</dd></div></>}</dl>{goal.status !== 'COMPLETED' && <footer>{goal.status === 'ACTIVE' ? <button className="secondary-button" disabled={pending} onClick={() => onChange('PAUSED')}><Pause /> Tạm dừng</button> : <button className="secondary-button" disabled={pending} onClick={() => onChange('ACTIVE')}><Play /> Tiếp tục</button>}<button className="icon-button danger-icon" title="Hủy mục tiêu" aria-label={`Hủy ${goal.name}`} disabled={pending} onClick={() => onChange('CANCELLED')}><Trash2 /></button></footer>}</article>
}

function GoalFormModal({ jars, onClose, onSaved }: { jars: { id: string; name: string; currency: string; allocatedBalance: number }[]; onClose: () => void; onSaved: () => void }) {
  const [jarId, setJarId] = useState('')
  const [name, setName] = useState('')
  const [targetAmount, setTargetAmount] = useState('')
  const [targetDate, setTargetDate] = useState('')
  const [error, setError] = useState('')
  const mutation = useMutation({ mutationFn: () => { const amount = Number(targetAmount); if (!jarId) throw new Error('Hãy chọn Hũ tiền.'); if (name.trim().length < 2) throw new Error('Tên mục tiêu cần ít nhất 2 ký tự.'); if (!Number.isFinite(amount) || amount <= 0) throw new Error('Số tiền mục tiêu phải lớn hơn 0.'); return savingGoalApi.create({ jarId, name: name.trim(), targetAmount: amount, targetDate: targetDate || undefined }) }, onSuccess: onSaved, onError: (requestError) => setError(requestError instanceof Error ? requestError.message : getApiErrorMessage(requestError, 'Không thể tạo mục tiêu.')) })
  const submit = (event: FormEvent) => { event.preventDefault(); setError(''); mutation.mutate() }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="goal-form-title"><header><div><p className="eyebrow">KẾ HOẠCH TIẾT KIỆM</p><h2 id="goal-form-title">Tạo mục tiêu</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header><form className="wallet-form" onSubmit={submit}>{error && <div className="form-alert">{error}</div>}<label><span>Hũ tiền</span><select value={jarId} onChange={(event) => setJarId(event.target.value)} autoFocus><option value="">Chọn Hũ tiền</option>{jars.map((jar) => <option key={jar.id} value={jar.id}>{jar.name} · {formatCurrency(jar.allocatedBalance, jar.currency)}</option>)}</select></label><label><span>Tên mục tiêu</span><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ví dụ: Quỹ khẩn cấp 6 tháng" /></label><div className="form-row"><label><span>Số tiền mục tiêu</span><input type="number" min="1" step="1" value={targetAmount} onChange={(event) => setTargetAmount(event.target.value)} placeholder="Ví dụ: 30000000" /></label><label><span>Ngày mong muốn đạt</span><input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} /></label></div><footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending}>{mutation.isPending ? <LoaderCircle className="spin" /> : 'Tạo mục tiêu'}</button></footer></form></section></div>
}
