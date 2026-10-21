import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { notificationsApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { onNotificationsChanged } from '../lib/notificationsBus'
import { ChatProvider, useChat } from './chat/ChatContext'
import { ActivityIcon, BellIcon, CalendarIcon, ChatIcon, FlagIcon, HomeIcon, LogoutIcon, ShieldIcon, TicketIcon, TrophyIcon, UserIcon, UsersIcon } from './icons'
import { Avatar } from './ui'

const NAV = [
  { to: '/', label: 'Home', icon: HomeIcon, end: true },
  { to: '/clubs', label: 'Clubs', icon: UsersIcon },
  { to: '/events', label: 'Events', icon: CalendarIcon, end: true },
  { to: '/tickets', label: 'Tickets', icon: TicketIcon },
  { to: '/activities', label: 'Activities', icon: ActivityIcon },
  // the phone's bottom bar has room for five; the leaderboard is also in the account menu
  { to: '/leaderboard', label: 'Leaderboard', icon: TrophyIcon, desktopOnly: true },
]

function useUnreadCount() {
  const [count, setCount] = useState(0)
  const location = useLocation()
  useEffect(() => {
    let cancelled = false
    const load = () => notificationsApi.unreadCount().then((r) => !cancelled && setCount(r.unread)).catch(() => undefined)
    load()
    const timer = setInterval(load, 60_000)
    const off = onNotificationsChanged(load)
    return () => { cancelled = true; clearInterval(timer); off() }
  }, [location.pathname])
  return count
}

function UserMenu() {
  const { user, hasRole, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  return (
    <div className="menu" ref={ref}>
      <button className="icon-button" aria-label="Account menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Avatar name={user?.name} url={user?.profilePicUrl} size={34} />
      </button>
      {open && (
        <div className="menu-panel" onClick={() => setOpen(false)}>
          <div className="menu-header">
            <div className="bold truncate">{user?.name}</div>
            <div className="muted small truncate">{user?.email}</div>
          </div>
          <Link to="/profile"><span className="row"><UserIcon size={16} />Profile</span></Link>
          <Link to="/leaderboard"><span className="row"><TrophyIcon size={16} />Leaderboard</span></Link>
          {!hasRole('CLUB_OWNER') && <Link to="/become-owner"><span className="row"><ShieldIcon size={16} />Become a club owner</span></Link>}
          {hasRole('ADMIN') && <Link to="/admin/verification"><span className="row"><ShieldIcon size={16} />Owner applications</span></Link>}
          {hasRole('ADMIN') && <Link to="/admin/chat-reports"><span className="row"><FlagIcon size={16} />Reported messages</span></Link>}
          <button onClick={async () => { await logout(); navigate('/login') }}>
            <span className="row"><LogoutIcon size={16} />Log out</span>
          </button>
        </div>
      )}
    </div>
  )
}

function MessagesLink() {
  const { unread } = useChat()
  return (
    <Link to="/messages" className="icon-button" aria-label={`Messages${unread ? ` (${unread} unread)` : ''}`}>
      <ChatIcon />
      {unread > 0 && <span className="dot-count">{unread > 99 ? '99+' : unread}</span>}
    </Link>
  )
}

export function Layout() {
  const unread = useUnreadCount()
  return (
    <ChatProvider>
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark"><ActivityIcon size={18} /></span>
            The Club
          </Link>
          <nav className="main-nav" aria-label="Main">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end}><Icon size={18} />{label}</NavLink>
            ))}
          </nav>
          <div className="header-actions">
            <MessagesLink />
            <Link to="/notifications" className="icon-button" aria-label={`Notifications${unread ? ` (${unread} unread)` : ''}`}>
              <BellIcon />
              {unread > 0 && <span className="dot-count">{unread > 99 ? '99+' : unread}</span>}
            </Link>
            <UserMenu />
          </div>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      <nav className="bottom-nav" aria-label="Main">
        {NAV.filter((item) => !item.desktopOnly).map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end}><Icon size={22} />{label}</NavLink>
        ))}
      </nav>
    </ChatProvider>
  )
}
