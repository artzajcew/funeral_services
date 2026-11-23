import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../types'
import { Spinner } from './ui'

export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { user, role, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="page container"><Spinner /></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (roles && !roles.includes(role)) return <Navigate to="/forbidden" replace />
  return <Outlet />
}

export function GuestOnly() {
  const { user } = useAuth()
  return user ? <Navigate to={user.role === 'ADMIN' ? '/admin/analytics' : '/orders'} replace /> : <Outlet />
}
