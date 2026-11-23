import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { BarChart3, ClipboardList, FileClock, House, Menu, PackageOpen, Users, X } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { Button } from './ui'
import { LogoMark } from './LogoMark'
import { compactPersonName } from '../utils'

interface NavItem {
  to: string
  label: string
  icon: typeof House
  end?: boolean
}

const commonLinks: NavItem[] = [
  { to: '/services', label: 'Услуги', icon: PackageOpen },
]

const clientLinks: NavItem[] = [
  { to: '/orders', label: 'Мои заявки', icon: ClipboardList },
]

const managerLinks: NavItem[] = [
  { to: '/orders', label: 'Заявки', icon: ClipboardList },
]

const adminLinks: NavItem[] = [
  { to: '/orders', label: 'Заявки', icon: ClipboardList },
  { to: '/admin/users', label: 'Пользователи', icon: Users },
  { to: '/admin/analytics', label: 'Аналитика', icon: BarChart3 },
  { to: '/admin/audit', label: 'Журнал', icon: FileClock },
]

export function Layout() {
  const { user, role, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const isHome = pathname === '/'
  const links = [...commonLinks, ...(role === 'CLIENT' ? clientLinks : role === 'MANAGER' ? managerLinks : role === 'ADMIN' ? adminLinks : [])]
  const workspaceTo = role === 'ADMIN' ? '/admin/analytics' : role === 'MANAGER' ? '/orders' : role === 'CLIENT' ? '/orders' : '/register'
  const workspaceLabel = role === 'GUEST' ? 'Оставить заявку' : 'Личный кабинет'
  const roleLabel = role === 'ADMIN' ? 'Администратор' : role === 'MANAGER' ? 'Менеджер' : 'Клиент'

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  return <div className={`app-shell ${isHome ? 'app-shell--home' : ''}`}>
    <a className="skip-link" href="#main-content">К основному содержанию</a>
    <header className="site-header site-header--unified">
      <div className="container site-header__inner">
        <Link className="brand" to="/" aria-label="Память, главная страница">
          <span><strong>Память</strong></span>
        </Link>

        <nav className={`main-nav ${open ? 'main-nav--open' : ''}`} aria-label="Основная навигация">
          <button className="main-nav__close" onClick={() => setOpen(false)} aria-label="Закрыть меню"><X /></button>
          {role === 'GUEST' ? <>
            <NavLink to="/services" onClick={() => setOpen(false)}>Услуги</NavLink>
            <a href="/#process" onClick={() => setOpen(false)}>Как мы работаем</a>
            <a href="/#contacts" onClick={() => setOpen(false)}>Контакты</a>
          </> : links.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}>
              <Icon size={18} />{label}
            </NavLink>)}
        </nav>

        <div className="header-actions">
          {user ? <>
            <Link className="header-account" to={workspaceTo} aria-label={`${roleLabel}: ${user.fullName || user.login}`}>
              {user.fullName ? compactPersonName(user.fullName) : user.login}
            </Link>
            <Button className="header-logout" variant="ghost" onClick={handleLogout}>Выйти</Button>
          </> : <>
            <Link className="home-login" to="/login">Войти</Link>
            <Link className="home-request" to={workspaceTo}>{workspaceLabel}</Link>
          </>}
          <button className="menu-button" onClick={() => setOpen(true)} aria-label="Открыть меню"><Menu /></button>
        </div>
      </div>
    </header>
    {open && <div className="nav-scrim" onClick={() => setOpen(false)} />}
    <main id="main-content"><Outlet /></main>
    <footer className={`site-footer ${isHome ? 'site-footer--home' : ''}`} id="contacts">
      <div className="container site-footer__inner"><div><div className="brand brand--footer"><LogoMark /><span><strong>Память</strong><small>ритуальное бюро</small></span></div><p>Помогаем достойно проводить близкого человека.</p></div><div><strong>Связаться с нами</strong><a href="tel:+74951234567">+7 (495) 123-45-67</a><span>Круглосуточно, без выходных</span></div><div><strong>Адрес</strong><span>Москва, ул. Тихая, 12</span><span>Ежедневно, 08:00-20:00</span></div></div>
      <div className="container site-footer__bottom">© 2026 Ритуальное бюро «Память»</div>
    </footer>
  </div>
}
