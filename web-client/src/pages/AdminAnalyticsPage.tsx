import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/http'
import { ErrorState, Spinner } from '../components/ui'
import type { Analytics, OrderStatus } from '../types'
import { errorMessage, money } from '../utils'

interface BackendAnalytics {
  overview: { orderCount: number; revenue: number }
  byStatus: Partial<Record<OrderStatus, number>>
  topServices?: Array<{ serviceName: string; soldCount: number; revenue?: number }>
}

export function AdminAnalyticsPage() {
  const [data, setData] = useState<Analytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const result = await api.get<BackendAnalytics>('/api/analytics')
      setData({
        totalOrders: result.overview.orderCount,
        newOrders: result.byStatus.NEW || 0,
        inProgressOrders: result.byStatus.IN_PROGRESS || 0,
        completedOrders: result.byStatus.COMPLETED || 0,
        cancelledOrders: result.byStatus.CANCELLED || 0,
        totalRevenue: result.overview.revenue,
        popularServices: (result.topServices || []).map(service => ({
          name: service.serviceName,
          count: service.soldCount,
          revenue: service.revenue,
        })),
      })
    }
    catch (err) { setError(errorMessage(err)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  if (loading) return <div className="page container"><Spinner /></div>
  if (error || !data) return <div className="page container"><ErrorState message={error || 'Статистика недоступна'} retry={load} /></div>
  const statuses = [
    { label: 'Новые', value: data.newOrders, color: '#6787a1' },
    { label: 'В работе', value: data.inProgressOrders, color: '#c79b58' },
    { label: 'Завершённые', value: data.completedOrders, color: '#6d927c' },
    { label: 'Отменённые', value: data.cancelledOrders, color: '#9aa0a4' },
  ]
  return <div className="page"><div className="container"><div className="page-heading page-heading--editorial"><div><h1>Аналитика</h1><p>Сводка по обращениям и работе бюро.</p></div></div>
    <div className="metric-grid metric-grid--ledger"><article><small>Всего заявок</small><strong>{data.totalOrders}</strong><em>За всё время</em></article><article><small>Сейчас в работе</small><strong>{data.inProgressOrders}</strong><em>Требуют внимания</em></article><article><small>Завершено</small><strong>{data.completedOrders}</strong><em>Выполненные заявки</em></article><article><small>Выручка</small><strong>{money.format(data.totalRevenue)}</strong><em>По завершённым заявкам</em></article></div>
    <div className="analytics-grid analytics-grid--editorial"><section className="data-section"><header><h2>Заявки по статусам</h2><p>Текущее распределение</p></header><div className="status-ledger">{statuses.map(item => <div key={item.label}><span><i style={{ background: item.color }} />{item.label}</span><strong>{item.value}</strong></div>)}</div></section><section className="data-section"><header><h2>Часто заказывают</h2><p>Услуги по количеству добавлений в заявки</p></header>{data.popularServices?.length ? <ol className="popularity-list">{data.popularServices.map(item => <li key={item.name}><span>{item.name}</span><strong>{item.count}</strong></li>)}</ol> : <p className="muted">Данных по услугам пока нет.</p>}</section></div>
  </div></div>
}
