import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowRight, CalendarDays, Plus, Search, SlidersHorizontal } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { api, unwrapList } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, ErrorState, Spinner, StatusBadge } from '../components/ui'
import type { Order, OrderStatus } from '../types'
import { dateTime, errorMessage, money, statusLabel, statusTone } from '../utils'

export function OrdersPage() {
  const { role } = useAuth()
  const navigate = useNavigate()
  const isStaff = role === 'ADMIN' || role === 'MANAGER'
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setOrders(unwrapList(await api.get<Order[] | { content: Order[] }>('/api/orders'))) }
    catch (err) { setError(errorMessage(err)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])

  const visible = useMemo(() => orders.filter(order => {
    const text = `#${order.id} ${order.clientName || ''}`.toLowerCase()
    return (status === 'ALL' || order.status === status) && text.includes(query.toLowerCase())
  }), [orders, query, status])

  return <div className="page"><div className="container">
    <div className="page-heading page-heading--editorial"><div><h1>{isStaff ? 'Заявки' : 'Мои заявки'}</h1><p>{isStaff ? 'Обращения клиентов, сроки и текущие статусы.' : 'Здесь можно проверить статус и продолжить разговор с координатором.'}</p></div>{role === 'CLIENT' && <Link className="button button--primary" to="/orders/new"><Plus size={18} /> Новая заявка</Link>}</div>
    <div className="toolbar toolbar--filters"><label className="search-box"><Search size={19} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder={isStaff ? 'Номер или имя клиента' : 'Номер заявки'} /></label><label className="select-box"><SlidersHorizontal size={18} /><select value={status} onChange={e => setStatus(e.target.value as OrderStatus | 'ALL')}><option value="ALL">Все статусы</option>{Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
    {loading ? <Spinner /> : error ? <ErrorState message={error} retry={load} /> : visible.length === 0 ? <EmptyState title={orders.length ? 'Ничего не найдено' : 'Заявок пока нет'} text={orders.length ? 'Попробуйте изменить фильтры.' : isStaff ? 'Активных заявок пока нет.' : 'Создайте первую заявку, выбрав необходимые услуги.'} action={!orders.length && role === 'CLIENT' && <Link className="button button--primary" to="/orders/new">Создать заявку</Link>} /> : <div className="table-card table-card--plain"><div className="table-scroll"><table><thead><tr><th>Заявка</th>{isStaff && <th>Клиент</th>}<th>Создана</th><th>Статус</th><th>Сумма</th><th aria-label="Действия" /></tr></thead><tbody>{visible.map(order => <tr className="order-row" key={order.id} role="link" tabIndex={0} aria-label={`Открыть заявку ${order.id}`} onClick={() => navigate(`/orders/${order.id}`)} onKeyDown={event => event.key === 'Enter' && navigate(`/orders/${order.id}`)}><td><strong>№ {order.id}</strong></td>{isStaff && <td><div className="table-person"><span>{order.clientName?.[0] || 'К'}</span><div><strong>{order.clientName || `Клиент №${order.clientId}`}</strong>{order.clientPhone && <small>{order.clientPhone}</small>}</div></div></td>}<td><span className="table-date"><CalendarDays size={16} />{dateTime(order.createdAt)}</span></td><td><StatusBadge tone={statusTone[order.status]}>{statusLabel[order.status]}</StatusBadge></td><td><strong>{money.format(order.totalCost)}</strong></td><td><span className="row-link" aria-hidden="true"><ArrowRight size={19} /></span></td></tr>)}</tbody></table></div></div>}
  </div></div>
}
