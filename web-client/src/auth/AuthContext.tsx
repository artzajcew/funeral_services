import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, tokenStorage, userStorage } from '../api/http'
import type { AuthResponse, Role, User } from '../types'

interface LoginInput { login: string; password: string }
interface RegisterInput { login: string; password: string; fullName: string; email: string; phone: string }

interface AuthContextValue {
  user: User | null
  role: Role
  loading: boolean
  login: (input: LoginInput) => Promise<User>
  register: (input: RegisterInput) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function normalizeAuth(result: AuthResponse): User {
  const token = result.accessToken || result.token
  if (token) tokenStorage.set(token)
  userStorage.set(result.user)
  return result.user
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => tokenStorage.get() ? userStorage.get<User>() : null)
  const [loading] = useState(false)

  const login = useCallback(async (input: LoginInput) => {
    const result = await api.post<AuthResponse>('/api/auth/login', input)
    const nextUser = normalizeAuth(result)
    setUser(nextUser)
    return nextUser
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    const result = await api.post<AuthResponse>('/api/auth/register', input)
    const nextUser = normalizeAuth(result)
    setUser(nextUser)
    return nextUser
  }, [])

  const logout = useCallback(async () => {
    // JWT backend не хранит серверную сессию: выход выполняется на клиенте.
    tokenStorage.clear()
    userStorage.clear()
    setUser(null)
  }, [])

  const value = useMemo(() => ({
    user,
    role: user?.role || 'GUEST' as Role,
    loading,
    login,
    register,
    logout,
  }), [user, loading, login, register, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
