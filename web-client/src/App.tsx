import { useEffect } from 'react'
import { useLenis } from 'lenis/react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { GuestOnly, RequireAuth } from './components/RouteGuards'
import { AdminAnalyticsPage } from './pages/AdminAnalyticsPage'
import { AdminAuditPage } from './pages/AdminAuditPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { HomePage } from './pages/HomePage'
import { OrderDetailPage } from './pages/OrderDetailPage'
import { OrderFormPage } from './pages/OrderFormPage'
import { OrdersPage } from './pages/OrdersPage'
import { ServicesPage } from './pages/ServicesPage'
import { ForbiddenPage, NotFoundPage } from './pages/StatePages'

function ScrollToTop() {
  const { pathname, hash } = useLocation()
  const lenis = useLenis()

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const target = hash ? document.getElementById(hash.slice(1)) : null
      if (target) {
        if (lenis) lenis.scrollTo(target, { immediate: true, offset: -96 })
        else target.scrollIntoView()
        return
      }
      if (lenis) lenis.scrollTo(0, { immediate: true })
      else window.scrollTo(0, 0)
    })
    return () => window.cancelAnimationFrame(frame)
  }, [hash, lenis, pathname])

  return null
}

export default function App() {
  return <BrowserRouter><ScrollToTop /><AuthProvider><Routes>
    <Route element={<GuestOnly />}><Route path="/login" element={<LoginPage />} /><Route path="/register" element={<RegisterPage />} /></Route>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="services" element={<ServicesPage />} />
      <Route element={<RequireAuth roles={['CLIENT', 'MANAGER', 'ADMIN']} />}>
        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/:id" element={<OrderDetailPage />} />
      </Route>
      <Route element={<RequireAuth roles={['CLIENT']} />}>
        <Route path="orders/new" element={<OrderFormPage />} />
      </Route>
      <Route element={<RequireAuth roles={['ADMIN']} />}>
        <Route path="admin/users" element={<AdminUsersPage />} />
        <Route path="admin/analytics" element={<AdminAnalyticsPage />} />
        <Route path="admin/audit" element={<AdminAuditPage />} />
      </Route>
      <Route path="forbidden" element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Route>
  </Routes></AuthProvider></BrowserRouter>
}
