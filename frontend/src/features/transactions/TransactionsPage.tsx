import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, ChevronLeft, ChevronRight, CircleAlert, Download, FileText, FileUp, LoaderCircle, Plus, RotateCcw, Search, Sparkles, WalletCards, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { categoryApi } from '../categories/categoryApi'
import type { Category } from '../categories/categoryTypes'
import { walletApi } from '../wallets/walletApi'
import type { Wallet } from '../wallets/walletTypes'
import { StatementImportModal } from '../statementimports/StatementImportModal'
import { transactionApi } from './transactionApi'
import { formatCurrency, formatDateTime } from './transactionFormatters'
import type { CreateTransactionInput, CreateWalletTransferInput, Transaction, TransactionDraftSuggestion, TransactionType } from './transactionTypes'

type Filter = 'ALL' | 'INCOME' | 'EXPENSE' | 'TRANSFER'

const transactionSchema = z.object({
  walletId: z.string().uuid('Hãy chọn một ví'),
  categoryId: z.string().uuid('Hãy chọn một danh mục'),
  transactionType: z.enum(['INCOME', 'EXPENSE']),
  amount: z.coerce.number().positive('Số tiền phải lớn hơn 0').finite(),
  description: z.string().trim().min(2, 'Nội dung cần ít nhất 2 ký tự').max(255),
  notes: z.string().trim().max(4000).optional(),
  occurredAt: z.string().min(1, 'Hãy chọn thời gian giao dịch'),
  applyAllocationRule: z.boolean().optional(),
})

type TransactionForm = z.input<typeof transactionSchema>

const transferSchema = z.object({
  sourceWalletId: z.string().uuid('Hãy chọn ví nguồn'),
  destinationWalletId: z.string().uuid('Hãy chọn ví nhận'),
  amount: z.coerce.number().positive('Số tiền phải lớn hơn 0').finite(),
  description: z.string().trim().min(2, 'Nội dung cần ít nhất 2 ký tự').max(255),
  notes: z.string().trim().max(4000).optional(),
  occurredAt: z.string().min(1, 'Hãy chọn thời gian chuyển'),
}).refine((value) => value.sourceWalletId !== value.destinationWalletId, {
  path: ['destinationWalletId'],
  message: 'Ví nhận phải khác ví nguồn',
})

type TransferForm = z.input<typeof transferSchema>

export function TransactionsPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [transferFormOpen, setTransferFormOpen] = useState(false)
  const [statementImportOpen, setStatementImportOpen] = useState(false)
  const [reverseTarget, setReverseTarget] = useState<Transaction | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [page, setPage] = useState(0)
  const [actionError, setActionError] = useState('')
  const hasInvalidDateRange = Boolean(fromDate && toDate && fromDate > toDate)
  const transactionFilters = {
    transactionType: filter === 'ALL' ? undefined : filter,
    query: query.trim() || undefined,
    from: toStartOfLocalDay(fromDate),
    to: toStartOfFollowingLocalDay(toDate),
  }
  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })
  const transactionsQuery = useQuery({
    queryKey: ['transactions', filter, query.trim(), fromDate, toDate, page],
    queryFn: () => transactionApi.list({
      ...transactionFilters,
      page,
      size: 10,
    }),
    enabled: !hasInvalidDateRange,
  })
  const reverseMutation = useMutation({
    mutationFn: transactionApi.reverse,
    onSuccess: () => {
      setReverseTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể hoàn tác giao dịch.')),
  })
  const exportMutation = useMutation({
    mutationFn: () => transactionApi.exportCsv(transactionFilters),
    onSuccess: ({ blob, fileName }) => {
      const objectUrl = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = fileName
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(objectUrl)
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể xuất lịch sử giao dịch.')),
  })

  const visibleTransactions = transactionsQuery.data?.content ?? []
  const totalTransactions = transactionsQuery.data?.totalElements ?? 0

  const canCreate = Boolean(walletsQuery.data?.length && categoriesQuery.data?.length)
  const canImportStatement = Boolean(walletsQuery.data?.length && categoriesQuery.data?.some((category) => category.categoryType === 'INCOME') && categoriesQuery.data?.some((category) => category.categoryType === 'EXPENSE'))
  const canDisplayWorkspace = Boolean(walletsQuery.data?.length)
  const canTransfer = Boolean(walletsQuery.data?.some((wallet) => walletsQuery.data.some((candidate) => candidate.id !== wallet.id && candidate.currency === wallet.currency)))

  return <>
    <header className="topbar transaction-topbar">
      <div><p className="eyebrow">DÒNG TIỀN HẰNG NGÀY</p><h1>Giao dịch</h1><p className="page-subtitle">Ghi nhận thu nhập và chi tiêu theo từng ví. Số dư được cập nhật ngay sau khi giao dịch được tạo.</p></div>
      <div className="transaction-actions">
        <button className="secondary-button" disabled={!totalTransactions || hasInvalidDateRange || exportMutation.isPending} onClick={() => exportMutation.mutate()} title="Xuất toàn bộ giao dịch theo bộ lọc hiện tại, không chỉ trang đang xem"><Download /> {exportMutation.isPending ? 'Đang xuất...' : 'Xuất CSV'}</button>
        <button className="secondary-button" disabled={!canImportStatement} onClick={() => setStatementImportOpen(true)} title={canImportStatement ? 'Nhập sao kê CSV sau khi xem trước' : 'Cần ít nhất một ví, danh mục thu và danh mục chi'}><FileUp /> Nhập sao kê</button>
        <button className="secondary-button" disabled={!canTransfer} onClick={() => setTransferFormOpen(true)} title={canTransfer ? 'Chuyển tiền giữa hai ví cùng loại tiền tệ' : 'Cần ít nhất hai ví cùng tiền tệ'}><ArrowLeftRight /> Chuyển tiền</button>
        <button className="primary-button" disabled={!canCreate} onClick={() => setFormOpen(true)} title={canCreate ? 'Thêm giao dịch' : 'Hãy có ví và danh mục trước'}><Plus /> Thêm giao dịch</button>
      </div>
    </header>

    {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}
    {walletsQuery.isError && <div className="form-alert page-alert" role="alert">{getApiErrorMessage(walletsQuery.error, 'Không thể tải danh sách ví.')}</div>}
    {categoriesQuery.isError && <div className="form-alert page-alert" role="alert">{getApiErrorMessage(categoriesQuery.error, 'Không thể tải danh mục.')}</div>}
    {!walletsQuery.isPending && !walletsQuery.data?.length && <section className="content-state"><WalletCards /><strong>Chưa có ví để ghi giao dịch</strong><p>Tạo ít nhất một ví tiền trước khi thêm khoản thu hoặc chi đầu tiên.</p></section>}
    {!categoriesQuery.isPending && !categoriesQuery.data?.length && <section className="content-state"><FileText /><strong>Chưa có danh mục để ghi giao dịch</strong><p>Hãy tạo một danh mục thu hoặc chi trước khi ghi nhận giao dịch.</p></section>}

    {canDisplayWorkspace && <section className="panel transactions-workspace">
      <div className="transaction-tools">
        <label className="transaction-search"><Search /><input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Tìm theo nội dung, danh mục hoặc ghi chú" /></label>
        <div className="transaction-filter-controls" aria-label="Lọc theo ngày">
          <label className="transaction-date-filter"><span>Từ ngày</span><input type="date" value={fromDate} max={toDate || undefined} onChange={(event) => { setFromDate(event.target.value); setPage(0) }} /></label>
          <label className="transaction-date-filter"><span>Đến ngày</span><input type="date" value={toDate} min={fromDate || undefined} onChange={(event) => { setToDate(event.target.value); setPage(0) }} /></label>
          <button className="icon-button date-filter-clear" type="button" disabled={!fromDate && !toDate} onClick={() => { setFromDate(''); setToDate(''); setPage(0) }} aria-label="Xóa lọc ngày" title="Xóa lọc ngày"><X /></button>
        </div>
        <div className="segmented-control" aria-label="Lọc loại giao dịch">
          {([{ value: 'ALL', label: 'Tất cả' }, { value: 'INCOME', label: 'Thu' }, { value: 'EXPENSE', label: 'Chi' }, { value: 'TRANSFER', label: 'Chuyển tiền' }] as const).map((item) =>
            <button key={item.value} className={filter === item.value ? 'selected' : ''} onClick={() => { setFilter(item.value); setPage(0) }}>{item.label}</button>,
          )}
        </div>
      </div>

      {hasInvalidDateRange && <div className="form-alert transaction-filter-alert" role="alert">Ngày bắt đầu không thể sau ngày kết thúc.</div>}
      {!hasInvalidDateRange && <>
      {transactionsQuery.isPending && <div className="content-state compact-state"><LoaderCircle className="spin" /><span>Đang tải giao dịch</span></div>}
      {transactionsQuery.isError && <div className="content-state compact-state error-state"><CircleAlert /><strong>Chưa thể tải giao dịch</strong><p>{getApiErrorMessage(transactionsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void transactionsQuery.refetch()}>Thử lại</button></div>}
      {!transactionsQuery.isPending && !transactionsQuery.isError && visibleTransactions.length === 0 && <div className="content-state compact-state"><FileText /><strong>Chưa có giao dịch phù hợp</strong><p>{query || filter !== 'ALL' || fromDate || toDate ? 'Thử thay đổi điều kiện tìm kiếm hoặc bộ lọc.' : 'Bắt đầu bằng khoản thu hoặc chi đầu tiên của bạn.'}</p></div>}
      {!!visibleTransactions.length && <><div className="transaction-table-wrap"><table className="transaction-table"><thead><tr><th>Giao dịch</th><th>Danh mục</th><th>Ví tiền</th><th>Thời gian</th><th>Trạng thái</th><th className="numeric-cell">Số tiền</th><th aria-label="Thao tác" /></tr></thead><tbody>
        {visibleTransactions.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} onReverse={() => setReverseTarget(transaction)} />)}
      </tbody></table></div><TransactionPagination page={transactionsQuery.data?.page ?? 0} totalPages={transactionsQuery.data?.totalPages ?? 0} totalElements={totalTransactions} onChange={setPage} /></>}
      </>}
    </section>}

    {formOpen && walletsQuery.data && categoriesQuery.data && <TransactionFormModal wallets={walletsQuery.data} categories={categoriesQuery.data} onClose={() => setFormOpen(false)} onSaved={() => {
      setFormOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    }} />}
    {transferFormOpen && walletsQuery.data && <TransferFormModal wallets={walletsQuery.data} onClose={() => setTransferFormOpen(false)} onSaved={() => {
      setTransferFormOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    }} />}
    {statementImportOpen && walletsQuery.data && categoriesQuery.data && <StatementImportModal wallets={walletsQuery.data} categories={categoriesQuery.data} onClose={() => setStatementImportOpen(false)} onCompleted={() => {
      setStatementImportOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['financial-calendar'] })
      void queryClient.invalidateQueries({ queryKey: ['monthly-review'] })
    }} />}
    {reverseTarget && <ReverseModal transaction={reverseTarget} isPending={reverseMutation.isPending} onCancel={() => setReverseTarget(null)} onConfirm={() => reverseMutation.mutate(reverseTarget.id)} />}
  </>
}

function TransactionPagination({ page, totalPages, totalElements, onChange }: { page: number; totalPages: number; totalElements: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return <nav className="transaction-pagination" aria-label="Phân trang giao dịch">
    <span>{totalElements} giao dịch</span>
    <div><button className="icon-button" onClick={() => onChange(page - 1)} disabled={page === 0} aria-label="Trang trước"><ChevronLeft /></button><span>Trang {page + 1} / {totalPages}</span><button className="icon-button" onClick={() => onChange(page + 1)} disabled={page + 1 >= totalPages} aria-label="Trang sau"><ChevronRight /></button></div>
  </nav>
}

function TransactionRow({ transaction, onReverse }: { transaction: Transaction; onReverse: () => void }) {
  const isIncome = transaction.transactionType === 'INCOME'
  const isTransfer = transaction.transactionType === 'TRANSFER' || (transaction.transactionType === 'REVERSAL' && transaction.counterpartyWalletId !== null)
  const isReversal = transaction.transactionType === 'REVERSAL'
  const walletChange = transaction.walletChange ?? (isIncome ? transaction.amount : isTransfer ? 0 : -transaction.amount)
  const isPositiveChange = walletChange > 0
  const canReverse = transaction.status === 'POSTED' && !isReversal
  const typeLabel: Record<TransactionType, string> = { INCOME: 'Thu nhập', EXPENSE: 'Chi tiêu', TRANSFER: 'Chuyển tiền', JAR_TRANSFER: 'Phân bổ hũ', REFUND: 'Hoàn tiền', ADJUSTMENT: 'Điều chỉnh', REVERSAL: 'Hoàn tác' }

  return <tr>
    <td><div className="transaction-primary"><span className={`transaction-icon ${isIncome ? 'is-income' : isTransfer ? 'is-transfer' : ''}`}>{isIncome ? <ArrowDownLeft /> : isTransfer ? <ArrowLeftRight /> : <ArrowUpRight />}</span><div><strong>{transaction.description}</strong><small>{typeLabel[transaction.transactionType]}{transaction.notes ? ` · ${transaction.notes}` : ''}</small></div></div></td>
    <td><span className="category-name"><i style={{ backgroundColor: transaction.categoryColor ?? (isTransfer ? '#0f8f72' : undefined) }} />{transaction.categoryName ?? (isTransfer ? 'Chuyển nội bộ' : 'Chưa phân loại')}</span></td>
    <td>{transaction.counterpartyWalletName ? `${transaction.walletName} → ${transaction.counterpartyWalletName}` : transaction.walletName}</td>
    <td><time>{formatDateTime(transaction.occurredAt)}</time></td>
    <td><span className={`status-pill ${transaction.status.toLowerCase()}`}>{statusLabel(transaction.status)}</span></td>
    <td className={`numeric-cell amount-cell ${isPositiveChange ? 'amount-income' : isTransfer ? 'amount-transfer' : 'amount-expense'}`}>{isTransfer ? '' : isPositiveChange ? '+' : '-'}{formatCurrency(Math.abs(walletChange), transaction.currency)}</td>
    <td className="action-cell">{canReverse && <button className="icon-button" title="Hoàn tác giao dịch" aria-label={`Hoàn tác ${transaction.description}`} onClick={onReverse}><RotateCcw /></button>}</td>
  </tr>
}

function TransferFormModal({ wallets, onClose, onSaved }: { wallets: Wallet[]; onClose: () => void; onSaved: () => void }) {
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, control, setValue, formState: { errors, isSubmitting } } = useForm<TransferForm>({
    resolver: zodResolver(transferSchema),
    defaultValues: { sourceWalletId: wallets[0]?.id, destinationWalletId: '', amount: undefined, description: '', notes: '', occurredAt: localDateTimeValue() },
  })
  const sourceWalletId = useWatch({ control, name: 'sourceWalletId' })
  const destinationWalletId = useWatch({ control, name: 'destinationWalletId' })
  const sourceWallet = wallets.find((wallet) => wallet.id === sourceWalletId)
  const destinationWallets = wallets.filter((wallet) => wallet.id !== sourceWalletId && wallet.currency === sourceWallet?.currency)

  useEffect(() => {
    if (!destinationWallets.some((wallet) => wallet.id === destinationWalletId)) {
      setValue('destinationWalletId', destinationWallets[0]?.id ?? '', { shouldValidate: true })
    }
  }, [destinationWalletId, destinationWallets, setValue])

  const onSubmit = handleSubmit(async (rawValues) => {
    setSubmitError('')
    const values = transferSchema.parse(rawValues)
    const input: CreateWalletTransferInput = { ...values, occurredAt: new Date(values.occurredAt).toISOString(), notes: values.notes || undefined }
    try {
      await transactionApi.transfer(input, crypto.randomUUID())
      onSaved()
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, 'Không thể chuyển tiền giữa các ví.'))
    }
  })

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal transaction-modal" role="dialog" aria-modal="true" aria-labelledby="transfer-form-title">
    <header><div><p className="eyebrow">ĐIỀU CHUYỂN NỘI BỘ</p><h2 id="transfer-form-title">Chuyển tiền giữa các ví</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header>
    <form className="wallet-form" onSubmit={onSubmit} noValidate>
      {submitError && <div className="form-alert" role="alert">{submitError}</div>}
      <div className="form-row"><label><span>Ví nguồn</span><select {...register('sourceWalletId')}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {formatCurrency(wallet.currentBalance, wallet.currency)}</option>)}</select>{errors.sourceWalletId && <small className="field-error">{errors.sourceWalletId.message}</small>}</label><label><span>Ví nhận</span><select {...register('destinationWalletId')} disabled={!destinationWallets.length}>{destinationWallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {formatCurrency(wallet.currentBalance, wallet.currency)}</option>)}</select>{errors.destinationWalletId && <small className="field-error">{errors.destinationWalletId.message}</small>}</label></div>
      <label><span>Số tiền {sourceWallet ? `(${sourceWallet.currency})` : ''}</span><input type="number" min="1" step={sourceWallet?.currency === 'VND' ? '1' : '0.01'} placeholder="0" {...register('amount')} />{errors.amount && <small className="field-error">{errors.amount.message}</small>}</label>
      <label><span>Nội dung</span><input placeholder="Ví dụ: Nộp tiền mặt vào ngân hàng" {...register('description')} />{errors.description && <small className="field-error">{errors.description.message}</small>}</label>
      <label><span>Thời gian chuyển</span><input type="datetime-local" {...register('occurredAt')} />{errors.occurredAt && <small className="field-error">{errors.occurredAt.message}</small>}</label>
      <label><span>Ghi chú</span><textarea placeholder="Không bắt buộc" {...register('notes')} /></label>
      <footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={isSubmitting || !destinationWallets.length}>{isSubmitting ? <LoaderCircle className="spin" /> : <><ArrowLeftRight /> Xác nhận chuyển tiền</>}</button></footer>
    </form>
  </section></div>
}

function TransactionFormModal({ wallets, categories, onClose, onSaved }: { wallets: Wallet[]; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [submitError, setSubmitError] = useState('')
  const [suggestionDescription, setSuggestionDescription] = useState('')
  const [quickText, setQuickText] = useState('')
  const [draftSuggestion, setDraftSuggestion] = useState<TransactionDraftSuggestion | null>(null)
  const { register, handleSubmit, control, setValue, getValues, formState: { errors, isSubmitting } } = useForm<TransactionForm>({
    resolver: zodResolver(transactionSchema),
    defaultValues: { walletId: wallets[0]?.id, categoryId: categories.find((category) => category.categoryType === 'EXPENSE')?.id, transactionType: 'EXPENSE', amount: undefined, description: '', notes: '', occurredAt: localDateTimeValue(), applyAllocationRule: false },
  })
  const selectedWalletId = useWatch({ control, name: 'walletId' })
  const selectedWallet = wallets.find((wallet) => wallet.id === selectedWalletId)
  const selectedType = useWatch({ control, name: 'transactionType' })
  const selectedCategoryId = useWatch({ control, name: 'categoryId' })
  const description = useWatch({ control, name: 'description' })
  const availableCategories = categories.filter((category) => category.categoryType === selectedType)
  const suggestionsQuery = useQuery({
    queryKey: ['category-suggestions', selectedType, suggestionDescription],
    queryFn: () => categoryApi.suggest(selectedType, suggestionDescription),
    enabled: suggestionDescription.length >= 2,
    staleTime: 60_000,
  })
  const draftSuggestionMutation = useMutation({
    mutationFn: transactionApi.suggestDraft,
    onSuccess: setDraftSuggestion,
    onError: (error) => setSubmitError(getApiErrorMessage(error, 'Khong the phan tich noi dung nhanh.')),
  })

  useEffect(() => {
    const timer = window.setTimeout(() => setSuggestionDescription(description.trim()), 350)
    return () => window.clearTimeout(timer)
  }, [description])

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

  const requestDraftSuggestion = () => {
    const text = quickText.trim()
    if (!text || !selectedWallet) return
    setSubmitError('')
    setDraftSuggestion(null)
    draftSuggestionMutation.mutate({ text, currency: selectedWallet.currency, currentTransactionType: selectedType })
  }

  const applyDraftSuggestion = () => {
    if (!draftSuggestion) return
    setValue('description', draftSuggestion.description, { shouldDirty: true, shouldValidate: true })
    setValue('transactionType', draftSuggestion.suggestedTransactionType, { shouldDirty: true, shouldValidate: true })
    if (draftSuggestion.suggestedAmount !== null) {
      setValue('amount', draftSuggestion.suggestedAmount, { shouldDirty: true, shouldValidate: true })
    }
    if (draftSuggestion.suggestedDate) {
      const time = getValues('occurredAt')?.slice(11) || localDateTimeValue().slice(11)
      setValue('occurredAt', `${draftSuggestion.suggestedDate}T${time}`, { shouldDirty: true, shouldValidate: true })
    }
    const category = draftSuggestion.categorySuggestions[0]
    if (category) {
      setValue('categoryId', category.categoryId, { shouldDirty: true, shouldValidate: true })
    }
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal transaction-modal" role="dialog" aria-modal="true" aria-labelledby="transaction-form-title">
    <header><div><p className="eyebrow">GHI NHẬN DÒNG TIỀN</p><h2 id="transaction-form-title">Thêm giao dịch</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header>
    <form className="wallet-form" onSubmit={onSubmit} noValidate>
      {submitError && <div className="form-alert" role="alert">{submitError}</div>}
      <section className="transaction-draft-assistant" aria-labelledby="transaction-draft-assistant-title">
        <div className="transaction-draft-assistant-heading"><Sparkles /><div><strong id="transaction-draft-assistant-title">Trợ lý nhập nhanh</strong><small>Chỉ gợi ý; bạn xem và áp dụng trước khi lưu giao dịch.</small></div></div>
        <div className="transaction-draft-assistant-controls"><input value={quickText} onChange={(event) => { setQuickText(event.target.value); setDraftSuggestion(null) }} placeholder="VD: Cà phê 45k hôm nay" maxLength={255} /><button type="button" className="secondary-button" onClick={requestDraftSuggestion} disabled={!quickText.trim() || !selectedWallet || draftSuggestionMutation.isPending}>{draftSuggestionMutation.isPending ? <LoaderCircle className="spin" /> : <Sparkles />} Gợi ý</button></div>
        {draftSuggestion && <div className="transaction-draft-result" aria-live="polite"><div><strong>Đề xuất nháp</strong><p>{draftSuggestion.suggestedTransactionType === 'INCOME' ? 'Khoản thu' : 'Khoản chi'}{draftSuggestion.suggestedAmount !== null ? ` · ${formatCurrency(draftSuggestion.suggestedAmount, selectedWallet?.currency ?? 'VND')}` : ''}{draftSuggestion.suggestedDate ? ` · ${draftSuggestion.suggestedDate}` : ''}</p>{draftSuggestion.signals.length > 0 && <small>{draftSuggestion.signals.join(' · ')}</small>}</div><button type="button" className="plain-button" onClick={applyDraftSuggestion}>Áp dụng vào biểu mẫu</button></div>}
      </section>
      <fieldset className="type-toggle"><legend>Loại giao dịch</legend><label className={selectedType === 'EXPENSE' ? 'active-expense' : ''}><input type="radio" value="EXPENSE" {...register('transactionType')} /><ArrowUpRight /> Chi tiền</label><label className={selectedType === 'INCOME' ? 'active-income' : ''}><input type="radio" value="INCOME" {...register('transactionType')} /><ArrowDownLeft /> Thu tiền</label></fieldset>
      <div className="form-row"><label><span>Ví tiền</span><select {...register('walletId')}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {formatCurrency(wallet.currentBalance, wallet.currency)}</option>)}</select>{errors.walletId && <small className="field-error">{errors.walletId.message}</small>}</label><label><span>Số tiền {selectedWallet ? `(${selectedWallet.currency})` : ''}</span><input type="number" min="1" step={selectedWallet?.currency === 'VND' ? '1' : '0.01'} placeholder="0" {...register('amount')} />{errors.amount && <small className="field-error">{errors.amount.message}</small>}</label></div>
      <label><span>Danh mục</span><select {...register('categoryId')}>{availableCategories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.systemCategory ? ' · Mặc định' : ''}</option>)}</select>{errors.categoryId && <small className="field-error">{errors.categoryId.message}</small>}</label>
      <label><span>Nội dung</span><input placeholder={selectedType === 'INCOME' ? 'Ví dụ: Lương tháng 8' : 'Ví dụ: Ăn trưa'} {...register('description')} />{errors.description && <small className="field-error">{errors.description.message}</small>}</label>
      {!!suggestionsQuery.data?.length && <section className="category-suggestions" aria-label="Gợi ý danh mục" aria-live="polite">
        <div><strong>Gợi ý danh mục</strong><small>Chọn một gợi ý để áp dụng, bạn vẫn có thể đổi lại.</small></div>
        <div className="category-suggestion-list">{suggestionsQuery.data.map((suggestion) => <button key={suggestion.categoryId} type="button" className={selectedCategoryId === suggestion.categoryId ? 'selected' : ''} onClick={() => setValue('categoryId', suggestion.categoryId, { shouldDirty: true, shouldValidate: true })}>
          <strong>{suggestion.categoryName}</strong><small>{suggestion.reason}</small>
        </button>)}</div>
      </section>}
      {selectedType === 'INCOME' && <label className="check-row"><input type="checkbox" {...register('applyAllocationRule')} /><span><strong>Tự chia vào các hũ theo quy tắc</strong><small>Nếu ví có quy tắc đang bật, phần trăm đã thiết lập sẽ được ghi nhận cùng khoản thu này.</small></span></label>}
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

function toStartOfLocalDay(value: string) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined
}

function toStartOfFollowingLocalDay(value: string) {
  if (!value) return undefined
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + 1)
  return date.toISOString()
}
