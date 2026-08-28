import { useMutation } from '@tanstack/react-query'
import { Download, FileSpreadsheet, LoaderCircle, Upload, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import type { Category } from '../categories/categoryTypes'
import { formatCurrency, formatDateTime } from '../transactions/transactionFormatters'
import type { Wallet } from '../wallets/walletTypes'
import { statementImportApi } from './statementImportApi'
import type { StatementImportInput, StatementImportPreview, StatementImportRowStatus } from './statementImportTypes'

type Props = {
  wallets: Wallet[]
  categories: Category[]
  onClose: () => void
  onCompleted: () => void
}

const maxClientFileBytes = 200_000

export function StatementImportModal({ wallets, categories, onClose, onCompleted }: Props) {
  const [walletId, setWalletId] = useState(wallets[0]?.id ?? '')
  const [incomeCategoryId, setIncomeCategoryId] = useState(categories.find((item) => item.categoryType === 'INCOME')?.id ?? '')
  const [expenseCategoryId, setExpenseCategoryId] = useState(categories.find((item) => item.categoryType === 'EXPENSE')?.id ?? '')
  const [csvText, setCsvText] = useState('')
  const [fileName, setFileName] = useState('')
  const [error, setError] = useState('')
  const [preview, setPreview] = useState<StatementImportPreview | null>(null)
  const incomeCategories = useMemo(() => categories.filter((item) => item.categoryType === 'INCOME'), [categories])
  const expenseCategories = useMemo(() => categories.filter((item) => item.categoryType === 'EXPENSE'), [categories])

  const input = (): StatementImportInput => ({
    walletId,
    incomeCategoryId: incomeCategoryId || undefined,
    expenseCategoryId: expenseCategoryId || undefined,
    csvText,
  })

  const previewMutation = useMutation({
    mutationFn: statementImportApi.preview,
    onSuccess: (nextPreview) => {
      setError('')
      setPreview(nextPreview)
    },
    onError: (cause) => setError(getApiErrorMessage(cause, 'Không thể phân tích file sao kê.')),
  })
  const confirmMutation = useMutation({
    mutationFn: statementImportApi.confirm,
    onSuccess: () => onCompleted(),
    onError: (cause) => setError(getApiErrorMessage(cause, 'Không thể nhập sao kê. Không có giao dịch nào được lưu.')),
  })

  const readFile = async (file: File | undefined) => {
    if (!file) return
    if (file.size > maxClientFileBytes) {
      setError('File CSV tối đa 200 KB và 200 dòng giao dịch.')
      return
    }
    try {
      setCsvText(await file.text())
      setFileName(file.name)
      setPreview(null)
      setError('')
    } catch {
      setError('Không thể đọc file CSV đã chọn.')
    }
  }

  const downloadTemplate = () => {
    const template = 'date,type,amount,description,notes\n2026-08-25,INCOME,15000000,Lương tháng 8,Chuyển khoản\n2026-08-26,EXPENSE,85000,Ăn trưa,\n'
    const objectUrl = URL.createObjectURL(new Blob([template], { type: 'text/csv;charset=utf-8' }))
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = 'fincore-statement-template.csv'
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  }

  const showPreview = () => {
    setError('')
    if (!walletId || !csvText.trim()) {
      setError('Chọn ví tiền và file CSV trước khi xem trước.')
      return
    }
    previewMutation.mutate(input())
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal statement-import-modal" role="dialog" aria-modal="true" aria-labelledby="statement-import-title">
      <header>
        <div><p className="eyebrow">NHẬP DỮ LIỆU AN TOÀN</p><h2 id="statement-import-title">Nhập sao kê CSV</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button>
      </header>
      <p className="statement-import-intro">File được xem trước trước khi ghi sổ cái. Dòng đã nhập sẽ được bỏ qua, không làm thay đổi số dư lần hai.</p>
      {error && <div className="form-alert" role="alert">{error}</div>}
      <div className="statement-import-settings">
        <label><span>Ví nhận sao kê</span><select value={walletId} onChange={(event) => { setWalletId(event.target.value); setPreview(null) }}>{wallets.map((wallet) => <option key={wallet.id} value={wallet.id}>{wallet.name} · {wallet.currency}</option>)}</select></label>
        <label><span>Danh mục khoản thu</span><select value={incomeCategoryId} onChange={(event) => { setIncomeCategoryId(event.target.value); setPreview(null) }}><option value="">Chọn danh mục thu</option>{incomeCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <label><span>Danh mục khoản chi</span><select value={expenseCategoryId} onChange={(event) => { setExpenseCategoryId(event.target.value); setPreview(null) }}><option value="">Chọn danh mục chi</option>{expenseCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
      </div>
      <section className="statement-import-upload">
        <FileSpreadsheet />
        <div><strong>{fileName || 'Chọn file sao kê CSV'}</strong><small>Tối đa 200 dòng. Hỗ trợ dấu phẩy hoặc chấm phẩy, trường có dấu nháy kép.</small></div>
        <label className="secondary-button"><Upload /> Chọn file<input type="file" accept=".csv,text/csv" onChange={(event) => void readFile(event.target.files?.[0])} /></label>
        <button type="button" className="plain-button" onClick={downloadTemplate}><Download /> Tải mẫu CSV</button>
      </section>
      <p className="statement-import-format"><strong>Mẫu cột bắt buộc:</strong> <code>date,type,amount,description,notes</code>. Loại giao dịch dùng <code>INCOME/THU</code> hoặc <code>EXPENSE/CHI</code>; ngày dùng ISO hoặc <code>dd/MM/yyyy</code>.</p>
      {preview && <PreviewTable preview={preview} />}
      <footer>
        <button type="button" className="plain-button" onClick={onClose}>Hủy</button>
        {!preview && <button className="secondary-button" type="button" disabled={previewMutation.isPending} onClick={showPreview}>{previewMutation.isPending ? <LoaderCircle className="spin" /> : <FileSpreadsheet />} Xem trước</button>}
        {preview && <button className="primary-button" type="button" disabled={preview.invalidCount > 0 || preview.readyCount === 0 || confirmMutation.isPending} onClick={() => confirmMutation.mutate(input())}>{confirmMutation.isPending ? <LoaderCircle className="spin" /> : <Upload />} Nhập {preview.readyCount} giao dịch</button>}
      </footer>
    </section>
  </div>
}

function PreviewTable({ preview }: { preview: StatementImportPreview }) {
  return <section className="statement-import-preview" aria-live="polite">
    <div className="statement-import-summary"><strong>Xem trước {preview.totalRows} dòng</strong><span className="statement-ready">{preview.readyCount} sẵn sàng</span><span className="statement-duplicate">{preview.duplicateCount} trùng</span><span className="statement-invalid">{preview.invalidCount} lỗi</span></div>
    <div className="statement-import-table-wrap"><table><thead><tr><th>Dòng</th><th>Trạng thái</th><th>Nội dung</th><th>Thời gian</th><th className="numeric-cell">Số tiền</th><th>Ghi chú / lý do</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.rowNumber} className={`statement-row-${row.status.toLowerCase()}`}><td>{row.rowNumber}</td><td><span className={`statement-status ${row.status.toLowerCase()}`}>{statusLabel(row.status)}</span></td><td>{row.description ?? '—'}</td><td>{row.occurredAt ? formatDateTime(row.occurredAt) : '—'}</td><td className="numeric-cell">{row.amount === null ? '—' : `${row.transactionType === 'EXPENSE' ? '-' : '+'}${formatCurrency(row.amount, preview.currency)}`}</td><td>{row.reason ?? row.notes ?? '—'}</td></tr>)}</tbody></table></div>
    {preview.invalidCount > 0 && <p className="statement-import-warning">Sửa các dòng lỗi trong file hoặc chọn đầy đủ danh mục trước khi xác nhận. Hệ thống sẽ không nhập một phần file có lỗi.</p>}
  </section>
}

function statusLabel(status: StatementImportRowStatus) {
  return { READY: 'Sẵn sàng', DUPLICATE: 'Đã có', INVALID: 'Cần sửa' }[status]
}
