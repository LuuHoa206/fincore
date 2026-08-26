import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowUpRight, CircleAlert, FileText, LoaderCircle, Plus, RotateCcw, Search, WalletCards, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { categoryApi } from '../categories/categoryApi'
import type { Category } from '../categories/categoryTypes'
import { walletApi } from '../wallets/walletApi'
import type { Wallet } from '../wallets/walletTypes'
import { transactionApi } from './transactionApi'
import { formatCurrency, formatDateTime } from './transactionFormatters'
import type { CreateTransactionInput, Transaction, TransactionType } from './transactionTypes'

type Filter = 'ALL' | 'INCOME' | 'EXPENSE'

const transactionSchema = z.object({
  walletId: z.string().uuid('Hãy chọn một ví'),
  categoryId: z.string().uuid('Hãy chọn một danh mục'),
  transactionType: z.enum(['INCOME', 'EXPENSE']),
  amount: z.coerce.number().positive('Số tiền phải lớn hơn 0').finite(),
  description: z.string().trim().min(2, 'Nội dung cần ít nhất 2 ký tự').max(255),
  notes: z.string().trim().max(4000).optional(),
  occurredAt: z.string().min(1, 'Hãy chọn thời gian giao dịch'),
})

type TransactionForm = z.input<typeof transactionSchema>

export function TransactionsPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [reverseTarget, setReverseTarget] = useState<Transaction | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')
  const [actionError, setActionError] = useState('')
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })
  const transactionsQuery = useQuery({ queryKey: ['transactions'], queryFn: transactionApi.list })
  const reverseMutation = useMutation({
    mutationFn: transactionApi.reverse,
    onSuccess: () => {
      setReverseTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể hoàn tác giao dịch.')),
  })

  const visibleTransactions = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('vi')
    return (transactionsQuery.data ?? []).filter((transaction) => {
      const matchesFilter = filter === 'ALL' || transaction.transactionType === filter
      const matchesQuery = !normalized || [transaction.description, transaction.walletName, transaction.notes ?? '']
        .some((value) => value.toLocaleLowerCase('vi').includes(normalized))
      return matchesFilter && matchesQuery
    })
  }, [filter, query, transactionsQuery.data])

  const canCreate = Boolean(walletsQuery.data?.length && categoriesQuery.data?.length)

  return <>
    <header className="topbar transaction-topbar">
      <div><p className="eyebrow">DÒNG TIỀN HẰNG NGÀY</p><h1>Giao dịch</h1><p className="page-subtitle">Ghi nhận thu nhập và chi tiêu theo từng ví. Số dư được cập nhật ngay sau khi giao dịch được tạo.</p></div>
      <button className="primary-button" disabled={!canCreate} onClick={() => setFormOpen(true)} title={canCreate ? 'Thêm giao dịch' : 'Hãy có ví và danh mục trước'}><Plus /> Thêm giao dịch</button>
    </header>

    {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}
    {walletsQuery.isError && <div className="form-alert page-alert" role="alert">{getApiErrorMessage(walletsQuery.error, 'Không thể tải danh sách ví.')}</div>}
    {categoriesQuery.isError && <div className="form-alert page-alert" role="alert">{getApiErrorMessage(categoriesQuery.error, 'Không thể tải danh mục.')}</div>}
    {!walletsQuery.isPending && !walletsQuery.data?.length && <section className="content-state"><WalletCards /><strong>Chưa có ví để ghi giao dịch</strong><p>Tạo ít nhất một ví tiền trước khi thêm khoản thu hoặc chi đầu tiên.</p></section>}
    {!categoriesQuery.isPending && !categoriesQuery.data?.length && <section className="content-state"><FileText /><strong>Chưa có danh mục để ghi giao dịch</strong><p>Hãy tạo một danh mục thu hoặc chi trước khi ghi nhận giao dịch.</p></section>}

    {canCreate && <section className="panel transactions-workspace">
      <div className="transaction-tools">
        <label className="transaction-search"><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo nội dung, ví tiền hoặc ghi chú" /></label>
        <div className="segmented-control" aria-label="Lọc loại giao dịch">
          {([{ value: 'ALL', label: 'Tất cả' }, { value: 'INCOME', label: 'Thu' }, { value: 'EXPENSE', label: 'Chi' }] as const).map((item) =>
            <button key={item.value} className={filter === item.value ? 'selected' : ''} onClick={() => setFilter(item.value)}>{item.label}</button>,
          )}
        </div>
      </div>

      {transactionsQuery.isPending && <div className="content-state compact-state"><LoaderCircle className="spin" /><span>Đang tải giao dịch</span></div>}
      {transactionsQuery.isError && <div className="content-state compact-state error-state"><CircleAlert /><strong>Chưa thể tải giao dịch</strong><p>{getApiErrorMessage(transactionsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void transactionsQuery.refetch()}>Thử lại</button></div>}
      {!transactionsQuery.isPending && !transactionsQuery.isError && visibleTransactions.length === 0 && <div className="content-state compact-state"><FileText /><strong>Chưa có giao dịch phù hợp</strong><p>{query || filter !== 'ALL' ? 'Thử thay đổi điều kiện tìm kiếm hoặc bộ lọc.' : 'Bắt đầu bằng khoản thu hoặc chi đầu tiên của bạn.'}</p></div>}
      {!!visibleTransactions.length && <div className="transaction-table-wrap"><table className="transaction-table"><thead><tr><th>Giao dịch</th><th>Danh mục</th><th>Ví tiền</th><th>Thời gian</th><th>Trạng thái</th><th className="numeric-cell">Số tiền</th><th aria-label="Thao tác" /></tr></thead><tbody>
        {visibleTransactions.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} onReverse={() => setReverseTarget(transaction)} />)}
      </tbody></table></div>}
    </section>}

    {formOpen && walletsQuery.data && categoriesQuery.data && <TransactionFormModal wallets={walletsQuery.data} categories={categoriesQuery.data} onClose={() => setFormOpen(false)} onSaved={() => {
      setFormOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    }} />}
    {reverseTarget && <ReverseModal transaction={reverseTarget} isPending={reverseMutation.isPending} onCancel={() => setReverseTarget(null)} onConfirm={() => reverseMutation.mutate(reverseTarget.id)} />}
  </>
}

function TransactionRow({ transaction, onReverse }: { transaction: Transaction; onReverse: () => void }) {
  const isIncome = transaction.transactionType === 'INCOME'
  const isReversal = transaction.transactionType === 'REVERSAL'
  const canReverse = transaction.status === 'POSTED' && !isReversal
  const typeLabel: Record<TransactionType, string> = { INCOME: 'Thu nhập', EXPENSE: 'Chi tiêu', TRANSFER: 'Chuyển tiền', JAR_TRANSFER: 'Phân bổ hũ', REFUND: 'Hoàn tiền', ADJUSTMENT: 'Điều chỉnh', REVERSAL: 'Hoàn tác' }

  return <tr>
    <td><div className="transaction-primary"><span className={`transaction-icon ${isIncome ? 'is-income' : ''}`}>{isIncome ? <ArrowDownLeft /> : <ArrowUpRight />}</span><div><strong>{transaction.description}</strong><small>{typeLabel[transaction.transactionType]}{transaction.notes ? ` · ${transaction.notes}` : ''}</small></div></div></td>
    <td><span className="category-name"><i style={{ backgroundColor: transaction.categoryColor ?? undefined }} />{transaction.categoryName ?? 'Chưa phân loại'}</span></td>
    <td>{transaction.walletName}</td>
    <td><time>{formatDateTime(transaction.occurredAt)}</time></td>
    <td><span className={`status-pill ${transaction.status.toLowerCase()}`}>{statusLabel(transaction.status)}</span></td>
    <td className={`numeric-cell amount-cell ${isIncome ? 'amount-income' : 'amount-expense'}`}>{isIncome ? '+' : '-'}{formatCurrency(transaction.amount, transaction.currency)}</td>
    <td className="action-cell">{canReverse && <button className="icon-button" title="Hoàn tác giao dịch" aria-label={`Hoàn tác ${transaction.description}`} onClick={onReverse}><RotateCcw /></button>}</td>
  </tr>
}

function TransactionFormModal({ wallets, categories, onClose, onSaved }: { wallets: Wallet[]; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, watch, setValue, formState: { errors, isSubmitting } } = useForm<TransactionForm>({
    resolver: zodResolver(transactionSchema),
    defaultValues: { walletId: wallets[0]?.id, categoryId: categories.find((category) => category.categoryType === 'EXPENSE')?.id, transactionType: 'EXPENSE', amount: undefined, description: '', notes: '', occurredAt: localDateTimeValue() },
  })
  const selectedWalletId = watch('walletId')
  const selectedWallet = wallets.find((wallet) => wallet.id === selectedWalletId)
  const selectedType = watch('transactionType')
  const selectedCategoryId = watch('categoryId')
  const availableCategories = categories.filter((category) => category.categoryType === selectedType)

  useEffect(() => {
    if (!availableCategories.some((category) => category.id === selectedCategoryId)) {
      setValue('categoryId', availableCategories[0]?.id ?? '', { shouldValidate: true })
    }
  }, [availableCategories, selectedCategoryId, setValue])

  const onSubmit = handleSubmit(async (rawValues) => {
    setSubmitError('')
    const values = transactionSchema.parse(rawValues)
    const input: CreateTransactionInput = { ...values, occurredAt: new Date(values.occurredAt).toISOString(), notes: values.notes || undefined }
    try {
      await transactionApi.create(input, crypto.randomUUID())
      onSaved()
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, 'Không thể tạo giao dịch.'))
    }
  })

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal transaction-modal" role="dialog" aria-modal="true" aria-labelledby="transaction-form-title">
    <header><div><p className="eyebrow">GHI NHẬN DÒNG TIỀN</p><h2 id="transaction-form-title">Thêm giao dịch</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header>
    <form className="wallet-form" onSubmit={onSubmit} noValidate>
      {submitError && <div className="form-alert" role="alert">{submitError}</div>}
      <fieldset className="type-toggle"><legend>Loại giao dịch</legend><label className={selectedType === 'EXPENSE' ? 'active-expense' : ''}><input type="radio" value="EXPENSE" {...register('transactionType')} /><ArrowUpRight /> Chi tiền</label><label className={selectedType === 'INCOME' ? 'active-income' : ''}><input type="radio" value="INCOME" {...register('transactionType')} /><ArrowDownLeft /> Thu tiền</label></fieldset>
      <div className="form-row"><label><span>Ví tiền</span><select {...register('walletId')}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {formatCurrency(wallet.currentBalance, wallet.currency)}</option>)}</select>{errors.walletId && <small className="field-error">{errors.walletId.message}</small>}</label><label><span>Số tiền {selectedWallet ? `(${selectedWallet.currency})` : ''}</span><input type="number" min="1" step={selectedWallet?.currency === 'VND' ? '1' : '0.01'} placeholder="0" {...register('amount')} />{errors.amount && <small className="field-error">{errors.amount.message}</small>}</label></div>
      <label><span>Danh mục</span><select {...register('categoryId')}>{availableCategories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.systemCategory ? ' · Mặc định' : ''}</option>)}</select>{errors.categoryId && <small className="field-error">{errors.categoryId.message}</small>}</label>
      <label><span>Nội dung</span><input placeholder={selectedType === 'INCOME' ? 'Ví dụ: Lương tháng 8' : 'Ví dụ: Ăn trưa'} {...register('description')} />{errors.description && <small className="field-error">{errors.description.message}</small>}</label>
      <label><span>Thời gian giao dịch</span><input type="datetime-local" {...register('occurredAt')} />{errors.occurredAt && <small className="field-error">{errors.occurredAt.message}</small>}</label>
      <label><span>Ghi chú</span><textarea placeholder="Không bắt buộc" {...register('notes')} /></label>
      <footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={isSubmitting}>{isSubmitting ? <LoaderCircle className="spin" /> : <>{selectedType === 'INCOME' ? 'Ghi nhận khoản thu' : 'Ghi nhận khoản chi'}</>}</button></footer>
    </form>
  </section></div>
}

function ReverseModal({ transaction, isPending, onCancel, onConfirm }: { transaction: Transaction; isPending: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop" role="presentation"><section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="reverse-title"><span className="confirm-icon"><RotateCcw /></span><h2 id="reverse-title">Hoàn tác giao dịch?</h2><p>Hệ thống sẽ tạo một giao dịch đối ứng cho “{transaction.description}” và đưa số dư ví về trạng thái trước đó. Lịch sử ban đầu vẫn được giữ để đối soát.</p><footer><button className="plain-button" onClick={onCancel}>Quay lại</button><button className="danger-button" onClick={onConfirm} disabled={isPending}>{isPending ? <LoaderCircle className="spin" /> : 'Xác nhận hoàn tác'}</button></footer></section></div>
}

function statusLabel(status: Transaction['status']) {
  return { PENDING: 'Đang chờ', POSTED: 'Đã ghi nhận', REVERSED: 'Đã hoàn tác', FAILED: 'Không thành công' }[status]
}

function localDateTimeValue() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 16)
}
