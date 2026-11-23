import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Archive, ArrowLeft, CalendarDays, Edit3, MessageSquareText, Minus, Plus, Send } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, unwrapList } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorState, Field, Modal, Spinner, StatusBadge, Toast } from '../components/ui'
import type { Note, Order, OrderStatus, Service } from '../types'
import { dateTime, errorMessage, money, statusLabel, statusTone } from '../utils'

export function OrderDetailPage() {
  const { id } = useParams()
  const { role } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [order, setOrder] = useState<Order | null>(null)
  const [notes, setNotes] = useState<Note[]>([])
  const [services, setServices] = useState<Service[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [sendingNote, setSendingNote] = useState(false)
  const [statusBusy, setStatusBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const creationState = location.state as { created?: boolean; commentWarning?: boolean } | null
  const [toast, setToast] = useState(creationState?.commentWarning ? 'Заявка создана, но комментарий не добавлен' : creationState?.created ? 'Заявка успешно создана' : '')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [orderResult, notesResult, servicesResult] = await Promise.all([
        api.get<Order>(`/api/orders/${id}`),
        api.get<Note[] | { content: Note[] } | { data: Note[] }>(`/api/orders/${id}/comments`),
        api.get<Service[] | { content: Service[] }>('/api/services'),
      ])
      setOrder(orderResult); setNotes(unwrapList(notesResult)); setServices(unwrapList(servicesResult))
      setQuantities(Object.fromEntries((orderResult.items || []).map(item => [item.serviceId, item.quantity])))
    } catch (err) { setError(errorMessage(err)) } finally { setLoading(false) }
  }, [id])
  useEffect(() => { void load() }, [load])

  const submitNote = async (event: FormEvent) => {
    event.preventDefault(); if (!note.trim()) return
    setSendingNote(true); setActionError('')
    try {
      const created = await api.post<Note>(`/api/orders/${id}/comments`, { text: note.trim() })
      setNotes(current => [...current, created]); setNote(''); setToast('Комментарий добавлен')
    } catch (err) { setActionError(errorMessage(err)) } finally { setSendingNote(false) }
  }
  const updateStatus = async (status: OrderStatus) => {
    setStatusBusy(true); setActionError('')
    try {
      const updated = await api.put<Order>(`/api/orders/${id}`, { status })
      setOrder(current => updated || (current ? { ...current, status } : current)); setToast('Статус изменён')
    } catch (err) { setActionError(errorMessage(err)) } finally { setStatusBusy(false) }
  }
  const saveOrder = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setActionError('')
    try {
      const items = services.filter(service => quantities[service.id] > 0).map(service => ({ serviceId: service.id, quantity: quantities[service.id] }))
      if (!items.length) throw new Error('В заявке должна остаться хотя бы одна услуга')
      await api.put<Order>(`/api/orders/${id}`, { items })
      setEditing(false); setToast('Заявка обновлена'); await load()
    } catch (err) { setActionError(errorMessage(err)) } finally { setSaving(false) }
  }
  const archive = async () => {
    setSaving(true); setActionError('')
    try { await api.post(`/api/orders/${id}/archive`); navigate('/orders', { replace: true, state: { archived: true } }) }
    catch (err) { setActionError(errorMessage(err)); setSaving(false) }
  }
  const selectedTotal = useMemo(() => services.reduce((sum, service) => sum + (quantities[service.id] || 0) * service.price, 0), [services, quantities])

  if (loading) return <div className="page container"><Spinner /></div>
  if (error || !order) return <div className="page container"><Link className="back-link" to="/orders"><ArrowLeft size={17} /> К заявкам</Link><ErrorState message={error || 'Заявка не найдена'} retry={load} /></div>

  return <div className="page"><div className="container container--narrow"><Link className="back-link" to="/orders"><ArrowLeft size={17} /> К списку заявок</Link>
    <div className="order-title"><div><h1>Заявка № {order.id}</h1><p><CalendarDays size={16} /> Создана {dateTime(order.createdAt)}</p></div><StatusBadge tone={statusTone[order.status]}>{statusLabel[order.status]}</StatusBadge></div>
    {actionError && <div className="form-error">{actionError}</div>}
    <div className="detail-grid"><div className="detail-main">
      <section className="panel"><div className="panel__heading"><div><h2>Состав заявки</h2><p>Выбранные услуги и стоимость</p></div>{(role !== 'CLIENT' || order.status === 'NEW') && <Button variant="ghost" onClick={() => setEditing(true)}><Edit3 size={17} /> Изменить</Button>}</div><div className="line-items">{order.items?.length ? order.items.map(item => <div key={item.id || item.serviceId}><span><strong>{item.serviceName || services.find(service => service.id === item.serviceId)?.name || `Услуга №${item.serviceId}`}</strong><small>{item.quantity} × {money.format(item.price)}</small></span><strong>{money.format(item.quantity * item.price)}</strong></div>) : <p className="muted">Состав заявки не передан сервером.</p>}<div className="line-items__total"><span>Итого</span><strong>{money.format(order.totalCost)}</strong></div></div></section>
      <section className="panel"><div className="panel__heading"><div><span className="panel__icon"><MessageSquareText /></span><div><h2>Комментарии</h2><p>Обсуждение заявки с координатором</p></div></div></div><div className="notes">{notes.length ? notes.map(item => <article className="note" key={item.id}><div className="note__avatar">{item.authorName?.[0] || 'П'}</div><div><header><strong>{item.authorName || 'Пользователь'}</strong><time>{dateTime(item.createdAt)}</time></header><p>{item.text}</p></div></article>) : <p className="muted">Комментариев пока нет.</p>}</div><form className="note-form" onSubmit={submitNote}><textarea required value={note} onChange={e => setNote(e.target.value)} rows={2} maxLength={1000} placeholder="Напишите комментарий…" /><Button disabled={sendingNote || !note.trim()} type="submit" aria-label="Отправить комментарий"><Send size={18} /> <span>{sendingNote ? 'Отправляем…' : 'Отправить'}</span></Button></form></section>
    </div><aside className="detail-aside">
      {(role === 'ADMIN' || role === 'MANAGER') && <section className="panel"><h3>Управление</h3><Field label="Статус заявки"><select disabled={statusBusy} value={order.status} onChange={e => updateStatus(e.target.value as OrderStatus)}>{Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>{role === 'ADMIN' && <Button variant="danger" className="button--wide" onClick={() => setArchiving(true)}><Archive size={17} /> Архивировать</Button>}</section>}
      <section className="panel"><h3>Клиент</h3><div className="client-info"><span>{order.clientName?.split(' ').map(part => part[0]).slice(0, 2).join('') || 'К'}</span><div><strong>{order.clientName || `Клиент №${order.clientId}`}</strong>{order.clientPhone && <a href={`tel:${order.clientPhone}`}>{order.clientPhone}</a>}</div></div></section>
    </aside></div>
  </div>
  {editing && <Modal title="Изменить заявку" onClose={() => setEditing(false)}><form className="modal__body form-stack" onSubmit={saveOrder}><div className="compact-picker">{services.map(service => <div key={service.id}><span><strong>{service.name}</strong><small>{money.format(service.price)}</small></span><div className="quantity"><button type="button" onClick={() => setQuantities(q => ({ ...q, [service.id]: Math.max(0, (q[service.id] || 0) - 1) }))}><Minus size={15} /></button><span>{quantities[service.id] || 0}</span><button type="button" onClick={() => setQuantities(q => ({ ...q, [service.id]: (q[service.id] || 0) + 1 }))}><Plus size={15} /></button></div></div>)}</div><div className="modal-total"><span>Новая стоимость</span><strong>{money.format(selectedTotal)}</strong></div><div className="modal__actions"><Button type="button" variant="secondary" onClick={() => setEditing(false)}>Отмена</Button><Button disabled={saving} type="submit">{saving ? 'Сохраняем…' : 'Сохранить'}</Button></div></form></Modal>}
  {archiving && <Modal title="Архивировать заявку?" onClose={() => setArchiving(false)}><div className="modal__body"><p>Заявка №{order.id} будет скрыта из активного списка, но останется в базе данных.</p><div className="modal__actions"><Button variant="secondary" onClick={() => setArchiving(false)}>Отмена</Button><Button variant="danger" disabled={saving} onClick={archive}>{saving ? 'Архивируем…' : 'Архивировать'}</Button></div></div></Modal>}
  {toast && <Toast message={toast} onClose={() => setToast('')} />}
  </div>
}
