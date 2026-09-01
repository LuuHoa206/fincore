import { Component, type ErrorInfo, type ReactNode } from 'react'
import { RefreshCw, TriangleAlert } from 'lucide-react'

type AppErrorBoundaryProps = { children: ReactNode }
type AppErrorBoundaryState = { hasError: boolean }

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('FinCore UI error', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="application-error" role="alert">
          <TriangleAlert />
          <h1>Không thể mở trang này</h1>
          <p>Trang gặp lỗi ngoài dự kiến. Hãy tải lại để tiếp tục làm việc.</p>
          <div>
            <button type="button" className="primary-button" onClick={() => window.location.reload()}><RefreshCw /> Tải lại trang</button>
            <a className="secondary-button" href="/">Về tổng quan</a>
          </div>
        </main>
      )
    }

    return this.props.children
  }
}
