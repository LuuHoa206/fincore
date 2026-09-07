import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, ClipboardCheck, LoaderCircle, TriangleAlert, X } from 'lucide-react'
import { useState } from 'react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency } from '../transactions/transactionFormatters'
import type { Wallet } from '../wallets/walletTypes'
import { reconciliationApi } from './reconciliationApi'
import type { WalletReconciliationPreview } from './reconciliationTypes'

type Props = {
  wallet: Wallet
  onClose: () => void
  onAdjusted: () => void
}

export function WalletReconciliationModal({ wallet, onClose, onAdjusted }: Props) {
  const [statementDate, setStatementDate] = useState(localDateValue())
  const [statementBalance, setStatementBalance] = useState('')
  const [preview, setPreview] = useState<WalletReconciliationPreview | null>(null)
  const [reason, setReason] = useState('')
  const [adjustmentIdempotencyKey, setAdjustmentIdempotencyKey] = useState<string | null>(null)
  const [error, setError] = useState('')
  const previewMutation = useMutation({
    mutationFn: reconciliationApi.preview,
    onSuccess: (nextPreview) => {
      setError('')
      setPreview(nextPreview)
      setReason('')
      setAdjustmentIdempotencyKey(nextPreview.status === 'DIFFERENT' ? crypto.randomUUID() : null)
    },
    onError: (cause) => setError(getApiErrorMessage(cause, 'Không thể đối soát số dư lúc này.')),
  })
  const adjustmentMutation = useMutation({
    mutationFn: ({ input, idempotencyKey }: {
      input: { walletId: string; statementDate: string; statementBalance: number; reason: string }
      idempotencyKey: string
    }) => reconciliationApi.confirmAdjustment(input, idempotencyKey),
    onSuccess: () => {
      setError('')
      setPreview(null)
      setReason('')
      setAdjustmentIdempotencyKey(null)
      onAdjusted()
    },
    onError: (cause) => setError(getApiErrorMessage(cause, 'Không thể tạo giao dịch điều chỉnh lúc này.')),
  })

  const compare = () => {
    const parsedBalance = Number(statementBalance)
    if (!statementDate || !statementBalance.trim() || !Number.isFinite(parsedBalance)) {
      setError('Nhập ngày sao kê và số dư cuối ngày hợp lệ.')
      return
    }
    setError('')
    previewMutation.mutate({ walletId: wallet.id, statementDate, statementBalance: parsedBalance })
  }

  const confirmAdjustment = () => {
    const parsedBalance = Number(statementBalance)
    if (!preview || preview.status !== 'DIFFERENT') return
    if (!reason.trim()) {
      setError('Nhập lý do trước khi xác nhận điều chỉnh.')
      return
    }
    setError('')
    if (!adjustmentIdempotencyKey) {
      setError('Hãy kiểm tra lại số dư trước khi xác nhận điều chỉnh.')
      return
    }
    adjustmentMutation.mutate({
      input: { walletId: wallet.id, statementDate, statementBalance: parsedBalance, reason: reason.trim() },
      idempotencyKey: adjustmentIdempotencyKey,
    })
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal reconciliation-modal" role="dialog" aria-modal="true" aria-labelledby="reconciliation-title">
      <header>
        <div><p className="eyebrow">KIỂM TRA SỐ DƯ</p><h2 id="reconciliation-title">Đối soát “{wallet.name}”</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button>
      </header>
      <p className="reconciliation-intro">So sánh số dư cuối ngày trên sao kê với sổ giao dịch của FinCore. Việc đối soát chỉ để kiểm tra, không tự tạo giao dịch hoặc thay đổi số dư.</p>
      {error && <div className="form-alert" role="alert">{error}</div>}
      <div className="reconciliation-inputs">
        <label><span>Ngày chốt trên sao kê</span><input type="date" max={localDateValue()} value={statementDate} onChange={(event) => { setStatementDate(event.target.value); setPreview(null); setAdjustmentIdempotencyKey(null) }} /></label>
        <label><span>Số dư cuối ngày ({wallet.currency})</span><input type="number" inputMode="decimal" step="0.0001" placeholder="Ví dụ: 1250000" value={statementBalance} onChange={(event) => { setStatementBalance(event.target.value); setPreview(null); setAdjustmentIdempotencyKey(null) }} /></label>
      </div>
      {preview && <ReconciliationResult preview={preview} />}
      {preview?.status === 'DIFFERENT' && <section className="reconciliation-adjustment">
        <strong>Tạo giao dịch điều chỉnh</strong>
        <p>Hành động này ghi một bút toán riêng có audit log. FinCore tự tính lại chênh lệch trước khi ghi, không dùng số tiền do trình duyệt gửi lên.</p>
        <label><span>Lý do điều chỉnh</span><textarea rows={2} maxLength={500} value={reason} placeholder="Ví dụ: Sao kê có phí ngân hàng chưa được nhập" onChange={(event) => setReason(event.target.value)} /></label>
        <button type="button" className="danger-button" disabled={adjustmentMutation.isPending} onClick={confirmAdjustment}>{adjustmentMutation.isPending ? <LoaderCircle className="spin" /> : <TriangleAlert />} Xác nhận tạo điều chỉnh</button>
      </section>}
      <footer><button type="button" className="plain-button" onClick={onClose}>Đóng</button><button type="button" className="primary-button" disabled={previewMutation.isPending || adjustmentMutation.isPending} onClick={compare}>{previewMutation.isPending ? <LoaderCircle className="spin" /> : <ClipboardCheck />} Kiểm tra số dư</button></footer>
    </section>
  </div>
}

function ReconciliationResult({ preview }: { preview: WalletReconciliationPreview }) {
  const matched = preview.status === 'MATCHED'
  const differenceLabel = preview.difference > 0 ? 'Sao kê cao hơn sổ giao dịch' : 'Sao kê thấp hơn sổ giao dịch'
  return <section className={`reconciliation-result ${matched ? 'matched' : 'different'}`} aria-live="polite">
    <div className="reconciliation-result-head">{matched ? <CheckCircle2 /> : <TriangleAlert />}<div><strong>{matched ? 'Số dư đã khớp' : 'Phát hiện chênh lệch'}</strong><p>{matched ? 'Không cần điều chỉnh gì thêm.' : `${differenceLabel}: ${formatCurrency(Math.abs(preview.difference), preview.currency)}.`}</p></div></div>
    <dl>
      <div><dt>Sổ giao dịch FinCore</dt><dd>{formatCurrency(preview.ledgerBalance, preview.currency)}</dd></div>
      <div><dt>Sao kê cuối ngày</dt><dd>{formatCurrency(preview.statementBalance, preview.currency)}</dd></div>
      <div><dt>Chênh lệch</dt><dd>{formatCurrency(preview.difference, preview.currency)}</dd></div>
      <div><dt>Giao dịch đã tính</dt><dd>{preview.transactionCount} giao dịch</dd></div>
    </dl>
  </section>
}

function localDateValue() {
  const date = new Date()
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 10)
}
