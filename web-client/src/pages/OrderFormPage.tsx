import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, Minus, Plus, ShoppingBag } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { api, unwrapList } from '../api/http'
import { Button, ErrorState, Field, Spinner } from '../components/ui'
import type { Order, OrderInput, Service } from '../types'
import { errorMessage, money } from '../utils'

export function OrderFormPage() {
  const navigate = useNavigate()
  const [services, setServices] = useState<Service[]>([])
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get<Service[] | { content: Service[] }>('/api/services')
      .then(result => setServices(unwrapList(result)))
      .catch(err => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])
  const selected = services.filter(service => (quantities[service.id] || 0) > 0)
  const total = useMemo(() => selected.reduce((sum, service) => sum + service.price * quantities[service.id], 0), [selected, quantities])
  const change = (id: number, delta: number) => setQuantities(current => ({ ...current, [id]: Math.max(0, (current[id] || 0) + delta) }))

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    if (!selected.length) { setError('Выберите хотя бы одну услугу'); return }
    setSaving(true)
    try {
      const payload: OrderInput = { items: selected.map(service => ({ serviceId: service.id, quantity: quantities[service.id] })) }
      const order = await api.post<Order>('/api/orders', payload)
      let commentWarning = false
      if (comment.trim()) {
        try {
          await api.post(`/api/orders/${order.id}/comments`, { text: comment.trim() })
        } catch {
          commentWarning = true
        }
      }
      navigate(`/orders/${order.id}`, { state: { created: true, commentWarning } })
    } catch (err) { setError(errorMessage(err)) } finally { setSaving(false) }
  }

  return <div className="page"><div className="container container--narrow"><Link className="back-link" to="/orders"><ArrowLeft size={17} /> К списку заявок</Link><div className="page-heading page-heading--editorial"><div><h1>Новая заявка</h1><p>Выберите нужные услуги. Состав можно будет изменить до завершения заявки.</p></div></div>{loading ? <Spinner /> : error && !services.length ? <ErrorState message={error} /> : <form onSubmit={submit} className="order-form"><section className="panel"><div className="panel__heading"><div><span className="panel__icon"><ShoppingBag /></span><div><h2>Услуги</h2><p>Укажите необходимое количество.</p></div></div></div><div className="service-picker">{services.map(service => <article className={`service-option ${quantities[service.id] ? 'service-option--selected' : ''}`} key={service.id}><div><h3>{service.name}</h3><p>{service.description || 'Подробности согласуем после отправки заявки.'}</p><strong>{money.format(service.price)}</strong></div><div className="quantity"><button type="button" onClick={() => change(service.id, -1)} disabled={!quantities[service.id]} aria-label={`Уменьшить ${service.name}`}><Minus size={16} /></button><span>{quantities[service.id] || 0}</span><button type="button" onClick={() => change(service.id, 1)} aria-label={`Добавить ${service.name}`}><Plus size={16} /></button></div></article>)}</div></section><section className="panel"><Field label="Комментарий к заявке" hint="Необязательно"><textarea rows={4} maxLength={1000} value={comment} onChange={e => setComment(e.target.value)} placeholder="Напишите дополнительную информацию для координатора" /></Field></section>{error && <div className="form-error">{error}</div>}<aside className="order-summary"><div><span>Выбрано услуг</span><strong>{selected.reduce((sum, service) => sum + quantities[service.id], 0)}</strong></div><div className="order-summary__total"><span>Предварительная стоимость</span><strong>{money.format(total)}</strong></div><Button disabled={saving || !selected.length} type="submit">{saving ? 'Отправляем…' : 'Отправить заявку'}</Button></aside></form>}</div></div>
}
