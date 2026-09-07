import { Check, Globe2, LoaderCircle, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { useAuth } from './authContextState'

const timeZones = [
  'Asia/Ho_Chi_Minh', 'Asia/Bangkok', 'Asia/Singapore', 'Asia/Tokyo',
  'Europe/London', 'America/New_York', 'America/Los_Angeles',
]

export function SettingsPage() {
  const { user, updateProfile } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [preferredCurrency, setPreferredCurrency] = useState(user?.preferredCurrency ?? 'VND')
  const [timeZone, setTimeZone] = useState(user?.timeZone ?? 'Asia/Ho_Chi_Minh')
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setSaved(false)
    setSaving(true)
    try {
      await updateProfile({ displayName, preferredCurrency, timeZone })
      setSaved(true)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Không thể cập nhật hồ sơ. Vui lòng thử lại.'))
    } finally {
      setSaving(false)
    }
  }

  return <>
    <header className="topbar">
      <div><p className="eyebrow">TÀI KHOẢN CÁ NHÂN</p><h1>Hồ sơ & thiết lập</h1><p className="page-subtitle">Thiết lập thông tin hiển thị và múi giờ để báo cáo, lịch thu chi hiển thị đúng với bạn.</p></div>
    </header>
    <section className="settings-page-grid">
      <article className="panel settings-summary">
        <span className="settings-avatar"><UserRound /></span>
        <div><p className="eyebrow">TÀI KHOẢN</p><h2>{user?.displayName}</h2><p>{user?.email}</p></div>
        <dl><div><dt>Tiền tệ mặc định</dt><dd>{user?.preferredCurrency}</dd></div><div><dt>Múi giờ</dt><dd>{user?.timeZone}</dd></div></dl>
      </article>
      <form className="panel settings-form" onSubmit={submit}>
        <div className="section-heading"><div><p className="eyebrow">THÔNG TIN SỬ DỤNG</p><h2>Cập nhật thiết lập</h2></div><Globe2 /></div>
        {error && <div className="form-alert" role="alert">{error}</div>}
        {saved && <div className="success-alert" role="status"><Check /> Đã lưu thay đổi.</div>}
        <label><span>Tên hiển thị</span><input required minLength={2} maxLength={120} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
        <label><span>Tiền tệ mặc định cho giao dịch mới</span><input required minLength={3} maxLength={3} value={preferredCurrency} onChange={(event) => setPreferredCurrency(event.target.value.toUpperCase())} /></label>
        <label><span>Múi giờ</span><select value={timeZone} onChange={(event) => setTimeZone(event.target.value)}>{timeZones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}</select><small>Mốc giao dịch, báo cáo tháng và lịch thu chi sử dụng múi giờ này.</small></label>
        <p className="settings-note">Thay đổi tiền tệ mặc định không chuyển đổi số dư hoặc dữ liệu đã ghi nhận. Mỗi ví và giao dịch vẫn giữ nguyên loại tiền ban đầu.</p>
        <footer><button className="primary-button" disabled={saving}>{saving ? <><LoaderCircle className="spin" /> Đang lưu</> : <><Check /> Lưu thiết lập</>}</button></footer>
      </form>
    </section>
  </>
}
