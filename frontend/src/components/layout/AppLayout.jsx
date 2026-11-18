import { Bell, CreditCard, LayoutDashboard, LogOut, Menu as MenuIcon, Plus, Search, UserRound, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { getSummary } from '../../api/dashboard'
import { useAuth } from '../../context/AuthContext.jsx'
import { DATA_CHANGED } from '../../utils/events'
import Avatar from '../ui/Avatar.jsx'
import Logo from '../ui/Logo.jsx'
import Menu from '../ui/Menu.jsx'
import VerifyEmailBanner from './VerifyEmailBanner.jsx'

const NAV = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/subscriptions', label: 'Subscriptions', icon: CreditCard },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/profile', label: 'Profile & security', icon: UserRound },
]

/** Page title shown in the top bar, from the current path. */
function titleFor(pathname) {
  if (pathname.startsWith('/subscriptions/new')) return 'New subscription'
  if (/^\/subscriptions\/\d+\/edit/.test(pathname)) return 'Edit subscription'
  if (/^\/subscriptions\/\d+/.test(pathname)) return 'Subscription details'
  return NAV.find((n) => pathname.startsWith(n.to))?.label ?? 'SubTrack'
}

function Sidebar({ onNavigate }) {
  const { user } = useAuth()
  return (
    <div className="flex h-full flex-col bg-slate-900 text-slate-300">
      <div className="flex h-16 items-center px-5">
        <Link to="/dashboard" onClick={onNavigate}>
          <Logo tone="light" />
        </Link>
      </div>
      <nav className="flex-1 space-y-1 px-3 py-4" aria-label="Main">
        <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500">Workspace</p>
        {NAV.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                isActive ? 'bg-white/10 text-white' : 'hover:bg-white/5 hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Icon size={18} className={isActive ? 'text-indigo-300' : 'text-slate-400 group-hover:text-slate-200'} />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded-lg bg-white/5 p-3">
        <div className="flex items-center gap-3">
          <Avatar name={user?.name} size="sm" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-white">{user?.name}</p>
            <p className="truncate text-xs text-slate-400">{user?.email}</p>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [attention, setAttention] = useState(0)

  // Bell badge: cycles that need the user (overdue + awaiting confirmation).
  // Refreshed on navigation and whenever a page reports a change (see notifyDataChanged).
  useEffect(() => {
    const refresh = () =>
      getSummary()
        .then((s) => setAttention(s.overdueCount + s.awaitingConfirmationCount))
        .catch(() => {})
    refresh()
    window.addEventListener(DATA_CHANGED, refresh)
    return () => window.removeEventListener(DATA_CHANGED, refresh)
  }, [location.pathname])

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const onSearch = (e) => {
    e.preventDefault()
    navigate(`/subscriptions${search.trim() ? `?q=${encodeURIComponent(search.trim())}` : ''}`)
  }

  return (
    <div className="min-h-full">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">
        <Sidebar />
      </aside>

      {/* Mobile sidebar */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMobileOpen(false)} aria-hidden="true" />
          <aside className="relative h-full w-64">
            <Sidebar onNavigate={() => setMobileOpen(false)} />
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 -right-11 rounded-md bg-slate-900 p-1.5 text-white"
              aria-label="Close menu"
            >
              <X size={18} />
            </button>
          </aside>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
            onClick={() => setMobileOpen(true)}
            aria-label="Open menu"
          >
            <MenuIcon size={20} />
          </button>
          <h1 className="truncate text-base font-semibold text-slate-900">{titleFor(location.pathname)}</h1>

          <form onSubmit={onSearch} className="ml-auto hidden w-full max-w-xs md:block" role="search">
            <label className="relative block">
              <span className="sr-only">Search subscriptions</span>
              <Search size={16} className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search subscriptions…"
                className="w-full rounded-lg border-0 bg-slate-100 py-2 pr-3 pl-9 text-sm text-slate-900 placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-indigo-500"
              />
            </label>
          </form>

          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <Link
              to="/subscriptions/new"
              className="hidden items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 sm:inline-flex"
            >
              <Plus size={16} /> New
            </Link>
            <Link
              to="/dashboard"
              className="relative rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
              aria-label={attention ? `${attention} payments need attention` : 'Nothing needs attention'}
              title={attention ? `${attention} need attention` : 'All caught up'}
            >
              <Bell size={20} />
              {attention > 0 && (
                <span className="absolute top-1 right-1 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
                  {attention}
                </span>
              )}
            </Link>
            <Menu
              header={
                <>
                  <p className="text-sm font-medium text-slate-900">{user?.name}</p>
                  <p className="truncate text-xs text-slate-500">{user?.email}</p>
                </>
              }
              trigger={({ toggle, open }) => (
                <button
                  type="button"
                  onClick={toggle}
                  aria-expanded={open}
                  aria-label="Account menu"
                  className="ml-1 rounded-full ring-offset-2 hover:ring-2 hover:ring-slate-200"
                >
                  <Avatar name={user?.name} size="sm" />
                </button>
              )}
              items={[
                { label: 'Profile & security', icon: UserRound, onClick: () => navigate('/profile') },
                'divider',
                { label: 'Log out', icon: LogOut, onClick: onLogout, danger: true },
              ]}
            />
          </div>
        </header>
        <VerifyEmailBanner />
        <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
