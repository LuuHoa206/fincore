import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'
import { LoginPage } from './features/auth/AuthPage'
import { CategoriesPage } from './features/categories/CategoriesPage'
import { BudgetsPage } from './features/budgets/BudgetsPage'
import { SavingGoalsPage } from './features/savinggoals/SavingGoalsPage'
import { MoneyJarsPage } from './features/moneyjars/MoneyJarsPage'
import { AllocationRulesPage } from './features/allocationrules/AllocationRulesPage'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { RegisterPage } from './features/auth/RegisterPage'
import { WalletsPage } from './features/wallets/WalletsPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'
import { RecurringRulesPage } from './features/recurring/RecurringRulesPage'
import { SplitBillsPage } from './features/splitbills/SplitBillsPage'
import { AppLayout } from './layout/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import './App.css'

function App() {
  return (
    <AuthProvider>
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
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
