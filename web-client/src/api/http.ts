import type { PageResponse } from '../types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const TOKEN_KEY = 'memory.accessToken'
const USER_KEY = 'memory.user'

export class ApiError extends Error {
  status: number
  fields?: Record<string, string>

  constructor(message: string, status: number, fields?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fields = fields
  }
}

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const userStorage = {
  get: <T>() => {
    const value = localStorage.getItem(USER_KEY)
    if (!value) return null
    try { return JSON.parse(value) as T } catch { return null }
  },
  set: (user: unknown) => localStorage.setItem(USER_KEY, JSON.stringify(user)),
  clear: () => localStorage.removeItem(USER_KEY),
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = tokenStorage.get()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      credentials: 'include',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError('Не удалось связаться с сервером. Проверьте, запущен ли backend.', 0)
  }

  if (response.status === 401) {
    tokenStorage.clear()
    userStorage.clear()
  }
  const contentType = response.headers.get('content-type') || ''
  const payload = response.status === 204
    ? undefined
    : contentType.includes('application/json')
      ? await response.json()
      : await response.text()

  if (!response.ok) {
    const data = typeof payload === 'object' && payload ? payload as Record<string, unknown> : {}
    const message = String(data.message || data.error || (typeof payload === 'string' && payload) || 'Ошибка сервера')
    throw new ApiError(message, response.status, data.fields as Record<string, string> | undefined)
  }

  return payload as T
}

export function unwrapList<T>(value: T[] | PageResponse<T> | { data: T[] }): T[] {
  if (Array.isArray(value)) return value
  if ('content' in value && Array.isArray(value.content)) return value.content
  if ('data' in value && Array.isArray(value.data)) return value.data
  return []
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' }),
}
