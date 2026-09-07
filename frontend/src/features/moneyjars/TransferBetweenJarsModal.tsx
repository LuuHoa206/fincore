import { LoaderCircle, X } from 'lucide-react'
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { formatCurrency } from '../transactions/transactionFormatters'
import { moneyJarApi } from './moneyJarApi'
import type { MoneyJar } from './moneyJarTypes'

type TransferBetweenJarsModalProps = {
  jars: MoneyJar[]
  onClose: () => void
  onSaved: () => void
}

export function TransferBetweenJarsModal({ jars, onClose, onSaved }: TransferBetweenJarsModalProps) {
  const [sourceJarId, setSourceJarId] = useState(jars[0]?.id ?? '')
  const sourceJar = jars.find((jar) => jar.id === sourceJarId)
  const compatibleDestinations = jars.filter((jar) => jar.id !== sourceJarId && jar.currency === sourceJar?.currency)
  const [destinationJarId, setDestinationJarId] = useState(compatibleDestinations[0]?.id ?? '')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: async () => {
      const value = Number(amount)
      if (!sourceJar || !destinationJarId) throw new Error('Chọn hai hũ cùng tiền tệ để chuyển.')
      if (!Number.isFinite(value) || value <= 0) throw new Error('Nhập số tiền lớn hơn 0.')
      return moneyJarApi.transfer(sourceJar.id, destinationJarId, value)
    },
    onSuccess: onSaved,
    onError: (requestError) => setError(getApiErrorMessage(requestError, 'Không thể chuyển tiền giữa các hũ.')),
  })
  const changeSource = (nextSourceId: string) => {
    const nextSource = jars.find((jar) => jar.id === nextSourceId)
    setSourceJarId(nextSourceId)
    setDestinationJarId(jars.find((jar) => jar.id !== nextSourceId && jar.currency === nextSource?.currency)?.id ?? '')
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal allocation-modal" role="dialog" aria-modal="true" aria-labelledby="jar-transfer-title">
      <header>
        <div><p className="eyebrow">SẮP XẾP PHÂN BỔ</p><h2 id="jar-transfer-title">Chuyển giữa hũ</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button>
      </header>
      <form className="wallet-form" onSubmit={(event) => { event.preventDefault(); setError(''); mutation.mutate() }}>
        {error && <div className="form-alert" role="alert">{error}</div>}
        <p className="form-help">Thao tác này chỉ chuyển phần tiền đã phân bổ, không làm thay đổi số dư thực trong ví.</p>
        <label><span>Từ hũ</span><select value={sourceJarId} onChange={(event) => changeSource(event.target.value)}>{jars.map((jar) => <option key={jar.id} value={jar.id}>{jar.name} · {formatCurrency(jar.allocatedBalance, jar.currency)}</option>)}</select></label>
        <label><span>Đến hũ</span><select value={destinationJarId} onChange={(event) => setDestinationJarId(event.target.value)} disabled={compatibleDestinations.length === 0}><option value="">Chọn hũ nhận tiền</option>{compatibleDestinations.map((jar) => <option key={jar.id} value={jar.id}>{jar.name} · {jar.currency}</option>)}</select></label>
        {sourceJar && <div className="allocation-balance"><span>Có thể chuyển tối đa</span><strong>{formatCurrency(sourceJar.allocatedBalance, sourceJar.currency)}</strong><small>Hai hũ phải dùng cùng một loại tiền tệ.</small></div>}
        <label><span>Số tiền chuyển</span><input type="number" min="1" max={sourceJar?.allocatedBalance ?? 0} step="1" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Nhập số tiền" autoFocus /></label>
        <footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={mutation.isPending || !destinationJarId || (sourceJar?.allocatedBalance ?? 0) <= 0}>{mutation.isPending ? <LoaderCircle className="spin" /> : 'Xác nhận chuyển'}</button></footer>
      </form>
    </section>
  </div>
}
