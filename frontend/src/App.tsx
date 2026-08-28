import { lazy, Suspense } from 'react'
import { LoaderCircle } from 'lucide-react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import './App.css'

const LoginPage = lazy(async () => ({ default: (await import('./features/auth/AuthPage')).LoginPage }))
const RegisterPage = lazy(async () => ({ default: (await import('./features/auth/RegisterPage')).RegisterPage }))
const AppLayout = lazy(async () => ({ default: (await import('./layout/AppLayout')).AppLayout }))
const DashboardPage = lazy(async () => ({ default: (await import('./pages/DashboardPage')).DashboardPage }))
const WalletsPage = lazy(async () => ({ default: (await import('./features/wallets/WalletsPage')).WalletsPage }))
const CategoriesPage = lazy(async () => ({ default: (await import('./features/categories/CategoriesPage')).CategoriesPage }))
const BudgetsPage = lazy(async () => ({ default: (await import('./features/budgets/BudgetsPage')).BudgetsPage }))
const SavingGoalsPage = lazy(async () => ({ default: (await import('./features/savinggoals/SavingGoalsPage')).SavingGoalsPage }))
const MoneyJarsPage = lazy(async () => ({ default: (await import('./features/moneyjars/MoneyJarsPage')).MoneyJarsPage }))
const AllocationRulesPage = lazy(async () => ({ default: (await import('./features/allocationrules/AllocationRulesPage')).AllocationRulesPage }))
const TransactionsPage = lazy(async () => ({ default: (await import('./features/transactions/TransactionsPage')).TransactionsPage }))
const RecurringRulesPage = lazy(async () => ({ default: (await import('./features/recurring/RecurringRulesPage')).RecurringRulesPage }))
const SplitBillsPage = lazy(async () => ({ default: (await import('./features/splitbills/SplitBillsPage')).SplitBillsPage }))
const SettingsPage = lazy(async () => ({ default: (await import('./features/auth/SettingsPage')).SettingsPage }))
const ActivityHistoryPage = lazy(async () => ({ default: (await import('./features/audit/ActivityHistoryPage')).ActivityHistoryPage }))
const NotificationsPage = lazy(async () => ({ default: (await import('./features/notifications/NotificationsPage')).NotificationsPage }))

function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<RouteLoading />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path="wallets" element={<WalletsPage />} />
              <Route path="categories" element={<CategoriesPage />} />
              <Route path="budgets" element={<BudgetsPage />} />
              <Route path="goals" element={<SavingGoalsPage />} />
              <Route path="jars" element={<MoneyJarsPage />} />
              <Route path="allocation-rules" element={<AllocationRulesPage />} />
              <Route path="transactions" element={<TransactionsPage />} />
              <Route path="recurring" element={<RecurringRulesPage />} />
              <Route path="split-bills" element={<SplitBillsPage />} />
              <Route path="settings" element={<SettingsPage />} />
              <Route path="activity" element={<ActivityHistoryPage />} />
              <Route path="notifications" element={<NotificationsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  )
}

function RouteLoading() {
  return <div className="route-loading" role="status"><LoaderCircle className="spin" /><span>Đang mở trang</span></div>
}

export default App
