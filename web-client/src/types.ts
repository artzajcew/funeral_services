export type Role = 'GUEST' | 'CLIENT' | 'MANAGER' | 'ADMIN'

export interface User {
  id: number
  login: string
  fullName: string
  email?: string
  phone?: string
  role: Exclude<Role, 'GUEST'>
  enabled?: boolean
  blocked?: boolean
}

export interface AuthResponse {
  token?: string
  accessToken?: string
  user: User
}

export interface Service {
  id: number
  name: string
  description?: string
  price: number
  active?: boolean
}

export type OrderStatus = 'NEW' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface OrderItem {
  id?: number
  serviceId: number
  serviceName?: string
  quantity: number
  price: number
}

export interface Order {
  id: number
  clientId: number
  clientName?: string
  clientPhone?: string
  createdAt: string
  status: OrderStatus
  totalCost: number
  items?: OrderItem[]
}

export interface OrderInput {
  clientId?: number
  items: Array<{ serviceId: number; quantity: number }>
}

export interface Note {
  id: number
  text: string
  authorName?: string
  createdAt: string
}

export interface Analytics {
  totalOrders: number
  newOrders: number
  inProgressOrders: number
  completedOrders: number
  cancelledOrders: number
  totalRevenue: number
  popularServices?: Array<{ name: string; count: number; revenue?: number }>
}

export interface AuditEntry {
  id: number
  userName?: string
  userLogin?: string
  action: string
  entityType?: string
  entityId?: number
  createdAt: string
  details?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements?: number
  totalPages?: number
  number?: number
}
