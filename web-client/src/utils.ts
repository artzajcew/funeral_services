import type { OrderStatus } from './types'

export const money = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  maximumFractionDigits: 0,
})

export const dateTime = (value: string) => new Intl.DateTimeFormat('ru-RU', {
  dateStyle: 'medium',
  timeStyle: 'short',
}).format(new Date(value))

export const statusLabel: Record<OrderStatus, string> = {
  NEW: 'Новая',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Завершена',
  CANCELLED: 'Отменена',
}

export const statusTone: Record<OrderStatus, string> = {
  NEW: 'blue',
  IN_PROGRESS: 'amber',
  COMPLETED: 'green',
  CANCELLED: 'gray',
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Произошла непредвиденная ошибка'
}

export function compactPersonName(fullName: string): string {
  const [surname, ...rest] = fullName.trim().split(/\s+/).filter(Boolean)
  if (!surname) return ''
  const initials = rest.slice(0, 2).map(part => `${part[0].toUpperCase()}.`)
  return [surname, ...initials].join(' ')
}
