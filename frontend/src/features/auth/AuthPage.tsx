import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, CircleDollarSign, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, ShieldCheck } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { useAuth } from './authContextState'

const loginSchema = z.object({
  email: z.email('Email không đúng định dạng'),
  password: z.string().min(8, 'Mật khẩu cần ít nhất 8 ký tự'),
})

type LoginForm = z.infer<typeof loginSchema>

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [showPassword, setShowPassword] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  })

  if (user) {
    return <Navigate to="/" replace />
  }

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError('')
    try {
      await login(values)
      navigate(from, { replace: true })
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, 'Đăng nhập không thành công.'))
    }
  })

  return (
    <AuthShell title="Chào mừng trở lại" subtitle="Đăng nhập để tiếp tục quản lý tài chính của bạn.">
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {submitError && <div className="form-alert" role="alert">{submitError}</div>}
        <label>
          <span>Email</span>
          <div className="input-with-icon"><Mail /><input type="email" autoComplete="email" placeholder="ban@example.com" {...register('email')} /></div>
          {errors.email && <small className="field-error">{errors.email.message}</small>}
        </label>
        <label>
          <span>Mật khẩu</span>
          <div className="input-with-icon">
            <LockKeyhole />
            <input type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Tối thiểu 8 ký tự" {...register('password')} />
            <button type="button" className="reveal-button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showPassword ? <EyeOff /> : <Eye />}
            </button>
          </div>
          {errors.password && <small className="field-error">{errors.password.message}</small>}
        </label>
        <button className="auth-submit" disabled={isSubmitting}>
          {isSubmitting ? <LoaderCircle className="spin" /> : <>Đăng nhập <ArrowRight /></>}
        </button>
      </form>
      <p className="auth-switch">Chưa có tài khoản? <Link to="/register">Tạo tài khoản</Link></p>
    </AuthShell>
  )
}

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="auth-page">
      <section className="auth-brand-panel">
        <div className="auth-brand"><span><CircleDollarSign /></span><strong>FinCore</strong></div>
        <div>
          <p className="eyebrow">TÀI CHÍNH CÁ NHÂN CÓ HỆ THỐNG</p>
          <h1>Biết tiền đang ở đâu.<br />Chủ động cho điều sắp tới.</h1>
          <p>Theo dõi dòng tiền, chia ngân sách vào từng hũ và xây dựng mục tiêu tiết kiệm trên cùng một nền tảng.</p>
        </div>
        <div className="auth-trust"><ShieldCheck /><span><strong>Dữ liệu thuộc về bạn</strong><small>Mỗi tài khoản chỉ truy cập các ví do chính mình sở hữu.</small></span></div>
      </section>
      <section className="auth-form-panel">
        <div className="auth-form-card">
          <div className="auth-mobile-brand"><CircleDollarSign /><strong>FinCore</strong></div>
          <header><h2>{title}</h2><p>{subtitle}</p></header>
          {children}
        </div>
      </section>
    </main>
  )
}

export { AuthShell }
