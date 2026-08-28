import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, Banknote, Building2, ClipboardCheck, CreditCard, Landmark, LoaderCircle, Pencil, Plus, Smartphone, WalletCards, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { useAuth } from '../auth/authContextState'
import { walletApi } from './walletApi'
import { walletTypes, type CreateWalletInput, type Wallet, type WalletType } from './walletTypes'
import { WalletReconciliationModal } from '../reconciliation/WalletReconciliationModal'

const walletLabels: Record<WalletType, string> = {
  CASH: 'Tiền mặt',
  BANK: 'Tài khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
  CREDIT: 'Thẻ tín dụng',
  SAVINGS: 'Tài khoản tiết kiệm',
}

const walletIcons: Record<WalletType, typeof WalletCards> = {
  CASH: Banknote,
  BANK: Landmark,
  E_WALLET: Smartphone,
  CREDIT: CreditCard,
  SAVINGS: Building2,
}

const walletSchema = z.object({
  name: z.string().trim().min(2, 'Tên ví cần ít nhất 2 ký tự').max(100),
  walletType: z.enum(walletTypes),
  currency: z.string().trim().length(3, 'Mã tiền tệ gồm 3 ký tự').transform((value) => value.toUpperCase()),
  allowNegative: z.boolean(),
})

type WalletFormValues = z.input<typeof walletSchema>

export function WalletsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [editingWallet, setEditingWallet] = useState<Wallet | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [archiveTarget, setArchiveTarget] = useState<Wallet | null>(null)
  const [reconciliationWallet, setReconciliationWallet] = useState<Wallet | null>(null)
  const [actionError, setActionError] = useState('')

  const walletsQuery = useQuery({ queryKey: ['wallets'], queryFn: walletApi.list })
  const archiveMutation = useMutation({
    mutationFn: walletApi.archive,
    onSuccess: () => {
      setArchiveTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['wallets'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể lưu trữ ví.')),
  })

  const openCreate = () => {
    setEditingWallet(null)
    setFormOpen(true)
  }

  const openEdit = (wallet: Wallet) => {
    setEditingWallet(wallet)
    setFormOpen(true)
  }

  return (
    <>
      <header className="topbar wallet-topbar">
        <div><p className="eyebrow">TÀI SẢN VÀ NGUỒN TIỀN</p><h1>Ví tiền</h1><p className="page-subtitle">Quản lý nơi giữ tiền; mọi giao dịch sau này sẽ được ghi nhận vào một ví cụ thể.</p></div>
        <button className="primary-button" onClick={openCreate}><Plus /> Thêm ví</button>
      </header>

      {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}

      {walletsQuery.isPending && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải danh sách ví</span></div>}
      {walletsQuery.isError && <div className="content-state error-state"><WalletCards /><strong>Chưa thể tải ví</strong><p>{getApiErrorMessage(walletsQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void walletsQuery.refetch()}>Thử lại</button></div>}
      {walletsQuery.data?.length === 0 && <div className="content-state"><WalletCards /><strong>Bạn chưa có ví nào</strong><p>Tạo ví đầu tiên để chuẩn bị ghi nhận thu nhập và chi tiêu.</p><button className="primary-button" onClick={openCreate}><Plus /> Tạo ví đầu tiên</button></div>}

      {!!walletsQuery.data?.length && <section className="wallet-grid">
        {walletsQuery.data.map((wallet) => {
          const Icon = walletIcons[wallet.walletType]
          return <article className="wallet-card" key={wallet.id}>
            <div className="wallet-card-head"><span className="wallet-type-icon"><Icon /></span><span className="wallet-type-label">{walletLabels[wallet.walletType]}</span></div>
            <div className="wallet-balance"><small>Số dư hiện tại</small><strong>{formatCurrency(wallet.currentBalance, wallet.currency)}</strong></div>
            <div className="wallet-card-footer"><div><strong>{wallet.name}</strong><small>{wallet.currency}{wallet.allowNegative ? ' · Cho phép âm' : ''}</small></div><div className="row-actions">
              <button className="icon-button" onClick={() => setReconciliationWallet(wallet)} title="Đối soát số dư" aria-label={`Đối soát ${wallet.name}`}><ClipboardCheck /></button>
              <button className="icon-button" onClick={() => openEdit(wallet)} title="Sửa ví" aria-label={`Sửa ${wallet.name}`}><Pencil /></button>
              <button className="icon-button danger-icon" onClick={() => setArchiveTarget(wallet)} title="Lưu trữ ví" aria-label={`Lưu trữ ${wallet.name}`}><Archive /></button>
            </div></div>
          </article>
        })}
      </section>}

      {formOpen && <WalletFormModal
        wallet={editingWallet}
        defaultCurrency={user?.preferredCurrency ?? 'VND'}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          void queryClient.invalidateQueries({ queryKey: ['wallets'] })
        }}
      />}

      {archiveTarget && <ConfirmArchiveModal
        wallet={archiveTarget}
        isPending={archiveMutation.isPending}
        onCancel={() => setArchiveTarget(null)}
        onConfirm={() => archiveMutation.mutate(archiveTarget.id)}
      />}

      {reconciliationWallet && <WalletReconciliationModal wallet={reconciliationWallet} onClose={() => setReconciliationWallet(null)} />}
    </>
  )
}

function WalletFormModal({ wallet, defaultCurrency, onClose, onSaved }: {
  wallet: Wallet | null
  defaultCurrency: string
  onClose: () => void
  onSaved: () => void
}) {
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<WalletFormValues>({
    resolver: zodResolver(walletSchema),
    defaultValues: wallet ? {
      name: wallet.name,
      walletType: wallet.walletType,
      currency: wallet.currency,
      allowNegative: wallet.allowNegative,
    } : {
      name: '',
      walletType: 'BANK',
      currency: defaultCurrency,
      allowNegative: false,
    },
  })

  useEffect(() => {
    reset(wallet ? {
      name: wallet.name,
      walletType: wallet.walletType,
      currency: wallet.currency,
      allowNegative: wallet.allowNegative,
    } : { name: '', walletType: 'BANK', currency: defaultCurrency, allowNegative: false })
  }, [defaultCurrency, reset, wallet])

  const onSubmit = handleSubmit(async (rawValues) => {
    setSubmitError('')
    const values = walletSchema.parse(rawValues) as CreateWalletInput
    try {
      if (wallet) {
        await walletApi.update(wallet.id, {
          name: values.name,
          walletType: values.walletType,
          allowNegative: values.allowNegative,
        })
      } else {
        await walletApi.create(values)
      }
      onSaved()
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, wallet ? 'Không thể cập nhật ví.' : 'Không thể tạo ví.'))
    }
  })

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal" role="dialog" aria-modal="true" aria-labelledby="wallet-form-title">
      <header><div><p className="eyebrow">THÔNG TIN NGUỒN TIỀN</p><h2 id="wallet-form-title">{wallet ? 'Chỉnh sửa ví' : 'Thêm ví mới'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header>
      <form className="wallet-form" onSubmit={onSubmit}>
        {submitError && <div className="form-alert" role="alert">{submitError}</div>}
        <label><span>Tên ví</span><input placeholder="Ví dụ: Tài khoản nhận lương" {...register('name')} />{errors.name && <small className="field-error">{errors.name.message}</small>}</label>
        <div className="form-row">
          <label><span>Loại ví</span><select {...register('walletType')}>{walletTypes.map((type) => <option key={type} value={type}>{walletLabels[type]}</option>)}</select></label>
          <label><span>Tiền tệ</span><input maxLength={3} disabled={!!wallet} {...register('currency')} />{errors.currency && <small className="field-error">{errors.currency.message}</small>}</label>
        </div>
        <label className="check-row"><input type="checkbox" {...register('allowNegative')} /><span><strong>Cho phép số dư âm</strong><small>Phù hợp với thẻ tín dụng hoặc tài khoản thấu chi.</small></span></label>
        <footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={isSubmitting}>{isSubmitting ? <LoaderCircle className="spin" /> : wallet ? 'Lưu thay đổi' : 'Tạo ví'}</button></footer>
      </form>
    </section>
  </div>
}

function ConfirmArchiveModal({ wallet, isPending, onCancel, onConfirm }: { wallet: Wallet; isPending: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop" role="presentation">
    <section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="archive-title">
      <span className="confirm-icon"><Archive /></span>
      <h2 id="archive-title">Lưu trữ “{wallet.name}”?</h2>
      <p>Ví sẽ không còn xuất hiện trong danh sách hoạt động. Lịch sử giao dịch liên quan vẫn được giữ nguyên.</p>
      <footer><button className="plain-button" onClick={onCancel}>Giữ lại</button><button className="danger-button" onClick={onConfirm} disabled={isPending}>{isPending ? <LoaderCircle className="spin" /> : 'Lưu trữ ví'}</button></footer>
    </section>
  </div>
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value)
}
