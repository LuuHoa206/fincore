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
}

export function WalletReconciliationModal({ wallet, onClose }: Props) {
  const [statementDate, setStatementDate] = useState(localDateValue())
  const [statementBalance, setStatementBalance] = useState('')
  const [preview, setPreview] = useState<WalletReconciliationPreview | null>(null)
  const [error, setError] = useState('')
  const previewMutation = useMutation({
    mutationFn: reconciliationApi.preview,
    onSuccess: (nextPreview) => {
      setError('')
      setPreview(nextPreview)
    },
    onError: (cause) => setError(getApiErrorMessage(cause, 'Không thể đối soát số dư lúc này.')),
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

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal reconciliation-modal" role="dialog" aria-modal="true" aria-labelledby="reconciliation-title">
      <header>
        <div><p className="eyebrow">KIỂM TRA SỐ DƯ</p><h2 id="reconciliation-title">Đối soát “{wallet.name}”</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button>
      </header>
      <p className="reconciliation-intro">So sánh số dư cuối ngày trên sao kê với sổ giao dịch của FinCore. Việc đối soát chỉ để kiểm tra, không tự tạo giao dịch hoặc thay đổi số dư.</p>
      {error && <div className="form-alert" role="alert">{error}</div>}
      <div className="reconciliation-inputs">
        <label><span>Ngày chốt trên sao kê</span><input type="date" max={localDateValue()} value={statementDate} onChange={(event) => { setStatementDate(event.target.value); setPreview(null) }} /></label>
        <label><span>Số dư cuối ngày ({wallet.currency})</span><input type="number" inputMode="decimal" step="0.0001" placeholder="Ví dụ: 1250000" value={statementBalance} onChange={(event) => { setStatementBalance(event.target.value); setPreview(null) }} /></label>
      </div>
      {preview && <ReconciliationResult preview={preview} />}
      <footer><button type="button" className="plain-button" onClick={onClose}>Đóng</button><button type="button" className="primary-button" disabled={previewMutation.isPending} onClick={compare}>{previewMutation.isPending ? <LoaderCircle className="spin" /> : <ClipboardCheck />} Kiểm tra số dư</button></footer>
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
