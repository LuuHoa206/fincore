import { ArrowLeftRight, CalendarClock, ChartNoAxesCombined, CircleDollarSign, LayoutDashboard, LogOut, Menu, Settings, SlidersHorizontal, Tag, Target, UsersRound, WalletCards, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/authContextState'

function initials(name: string) {
  return name.trim().split(/\s+/).slice(-2).map((part) => part[0]?.toUpperCase()).join('')
}

export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const { user, logout } = useAuth()

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNavOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <span className="brand-mark"><CircleDollarSign size={24} /></span>
          <div><strong>FinCore</strong><small>Money, made clear</small></div>
        </div>
        <button className="close-nav icon-button" onClick={() => setMobileNavOpen(false)} aria-label="Đóng menu"><X /></button>
        <nav>
          <NavLink to="/settings" onClick={() => setMobileNavOpen(false)}><Settings /> Hồ sơ & thiết lập</NavLink>
          <NavLink to="/allocation-rules" onClick={() => setMobileNavOpen(false)}><SlidersHorizontal /> Quy tắc chia tiền</NavLink>
          <NavLink to="/jars" onClick={() => setMobileNavOpen(false)}><CircleDollarSign /> Hũ tiền</NavLink>
          <NavLink to="/" end onClick={() => setMobileNavOpen(false)}><LayoutDashboard /> Tổng quan</NavLink>
          <NavLink to="/wallets" onClick={() => setMobileNavOpen(false)}><WalletCards /> Ví tiền</NavLink>
          <NavLink to="/categories" onClick={() => setMobileNavOpen(false)}><Tag /> Danh mục</NavLink>
          <NavLink to="/transactions" onClick={() => setMobileNavOpen(false)}><ArrowLeftRight /> Giao dịch</NavLink>
          <NavLink to="/recurring" onClick={() => setMobileNavOpen(false)}><CalendarClock /> Giao dịch định kỳ</NavLink>
          <NavLink to="/split-bills" onClick={() => setMobileNavOpen(false)}><UsersRound /> Chia hóa đơn</NavLink>
          <NavLink to="/budgets" onClick={() => setMobileNavOpen(false)}><ChartNoAxesCombined /> Ngân sách</NavLink>
          <NavLink to="/goals" onClick={() => setMobileNavOpen(false)}><Target /> Mục tiêu</NavLink>
        </nav>
        <button className="settings-link logout-link" onClick={() => void logout()}><LogOut /> Đăng xuất</button>
        <div className="sidebar-profile">
          <span>{initials(user?.displayName ?? 'FC')}</span>
          <div><strong>{user?.displayName}</strong><small>{user?.email}</small></div>
        </div>
      </aside>

      {mobileNavOpen && <button className="nav-backdrop" onClick={() => setMobileNavOpen(false)} aria-label="Đóng menu" />}

      <button className="floating-mobile-menu icon-button" onClick={() => setMobileNavOpen(true)} aria-label="Mở menu"><Menu /></button>
      <main className="workspace"><Outlet /></main>
    </div>
  )
}
