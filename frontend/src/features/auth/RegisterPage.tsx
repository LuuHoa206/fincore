import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, UserRound } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { AuthShell } from './AuthPage'
import { getAuthenticationRedirect } from './authRedirect'
import { useAuth } from './authContextState'

const registerSchema = z.object({
  displayName: z.string().trim().min(2, 'Họ tên cần ít nhất 2 ký tự').max(120),
  email: z.email('Email không đúng định dạng'),
  password: z.string().min(8, 'Mật khẩu cần ít nhất 8 ký tự').max(72),
})

type RegisterForm = z.infer<typeof registerSchema>

export function RegisterPage() {
  const { user, register: createAccount } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [showPassword, setShowPassword] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  })

  if (user) {
    return <Navigate to="/" replace />
  }

  const from = getAuthenticationRedirect(location.state)

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError('')
    try {
      await createAccount({ ...values, preferredCurrency: 'VND', timeZone: 'Asia/Ho_Chi_Minh' })
      navigate(from, { replace: true })
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, 'Không thể tạo tài khoản.'))
    }
  })

  return (
    <AuthShell title="Tạo tài khoản FinCore" subtitle="Bắt đầu với đơn vị tiền tệ VND và múi giờ Việt Nam.">
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {submitError && <div className="form-alert" role="alert">{submitError}</div>}
        <label>
          <span>Họ và tên</span>
          <div className="input-with-icon"><UserRound /><input autoComplete="name" placeholder="Lưu Hòa" {...register('displayName')} /></div>
          {errors.displayName && <small className="field-error">{errors.displayName.message}</small>}
        </label>
        <label>
          <span>Email</span>
          <div className="input-with-icon"><Mail /><input type="email" autoComplete="email" placeholder="ban@example.com" {...register('email')} /></div>
          {errors.email && <small className="field-error">{errors.email.message}</small>}
        </label>
        <label>
          <span>Mật khẩu</span>
          <div className="input-with-icon">
            <LockKeyhole />
            <input type={showPassword ? 'text' : 'password'} autoComplete="new-password" placeholder="Tối thiểu 8 ký tự" {...register('password')} />
            <button type="button" className="reveal-button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showPassword ? <EyeOff /> : <Eye />}
            </button>
          </div>
          {errors.password && <small className="field-error">{errors.password.message}</small>}
        </label>
        <button className="auth-submit" disabled={isSubmitting}>
          {isSubmitting ? <LoaderCircle className="spin" /> : <>Tạo tài khoản <ArrowRight /></>}
        </button>
      </form>
      <p className="auth-switch">Đã có tài khoản? <Link to="/login" state={location.state}>Đăng nhập</Link></p>
    </AuthShell>
  )
}
