import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Edit3, Phone, Plus, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, ApiError, unwrapList } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { Button, EmptyState, ErrorState, Field, Modal, Spinner, Toast } from '../components/ui'
import type { Service } from '../types'
import { errorMessage, money } from '../utils'

const blankService = { name: '', description: '', price: '' }

export function ServicesPage() {
  const { role } = useAuth()
  const [services, setServices] = useState<Service[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [authRequired, setAuthRequired] = useState(false)
  const [editing, setEditing] = useState<Service | null | undefined>(undefined)
  const [form, setForm] = useState(blankService)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [toast, setToast] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError(''); setAuthRequired(false)
    try { setServices(unwrapList(await api.get<Service[] | { content: Service[] }>('/api/services'))) }
    catch (err) {
      if (role === 'GUEST' && err instanceof ApiError && err.status === 401) setAuthRequired(true)
      else setError(errorMessage(err))
    } finally { setLoading(false) }
  }, [role])
  useEffect(() => { void load() }, [load])

  const openEditor = (service?: Service) => {
    setEditing(service || null); setFormError('')
    setForm(service ? { name: service.name, description: service.description || '', price: String(service.price) } : blankService)
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setFormError(''); setSaving(true)
    try {
      const payload = { name: form.name.trim(), description: form.description.trim(), price: Number(form.price) }
      if (editing) await api.put<Service>(`/api/services/${editing.id}`, payload)
      else await api.post<Service>('/api/services', payload)
      setEditing(undefined); setToast(editing ? 'Услуга обновлена' : 'Услуга добавлена'); await load()
    } catch (err) { setFormError(errorMessage(err)) } finally { setSaving(false) }
  }

  const visible = services.filter(service => `${service.name} ${service.description || ''}`.toLowerCase().includes(query.toLowerCase()))
  return <div className="page page--catalog"><div className="container">
    <div className="page-heading page-heading--editorial catalog-heading"><div><h1>Услуги и стоимость</h1><p>Точный состав можно обсудить с координатором до оформления заявки.</p></div><div className="catalog-heading__tools"><label className="catalog-search"><Search size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Поиск по каталогу" aria-label="Поиск по каталогу" /></label><div className="catalog-heading__meta"><span>{visible.length} {visible.length === 1 ? 'услуга' : 'услуг'}</span></div></div></div>
    {role === 'ADMIN' && <div className="catalog-admin-actions"><Button onClick={() => openEditor()}><Plus size={18} /> Добавить услугу</Button></div>}
    {loading ? <Spinner /> : authRequired ? <EmptyState title="Каталог доступен после входа" text="Войдите или зарегистрируйтесь, чтобы увидеть услуги и оформить заявку." action={<Link className="button button--primary" to="/login">Войти в кабинет</Link>} /> : error ? <ErrorState message={error} retry={load} /> : visible.length === 0 ? <EmptyState title="Услуги не найдены" text="Измените поисковый запрос или добавьте первую услугу." /> : <div className={`catalog-layout ${role === 'ADMIN' ? 'catalog-layout--admin' : ''}`}>
      {role !== 'ADMIN' && <aside className="catalog-note"><h2>Не знаете, с чего начать?</h2><p>Позвоните нам. Координатор спокойно выслушает и подскажет, какие услуги нужны именно в вашей ситуации.</p><a href="tel:+74951234567"><Phone size={17} /> +7 (495) 123-45-67</a><small>Круглосуточно, без выходных</small></aside>}
      <section className="service-list" aria-label="Перечень услуг">{visible.map((service, index) => <article className="service-row" key={service.id}><span className="service-row__index">{String(index + 1).padStart(2, '0')}</span><div className="service-row__body"><h2>{service.name}</h2><p>{service.description || 'Подробности услуги уточнит ваш координатор.'}</p></div><div className="service-row__meta"><strong>{money.format(service.price)}</strong>{role === 'ADMIN' && <button className="edit-button" onClick={() => openEditor(service)}><Edit3 size={16} /> Изменить</button>}</div></article>)}</section>
    </div>}
  </div>
  {editing !== undefined && <Modal title={editing ? 'Изменить услугу' : 'Новая услуга'} onClose={() => setEditing(undefined)}><form className="modal__body form-stack" onSubmit={submit}>{formError && <div className="form-error">{formError}</div>}<Field label="Название"><input required maxLength={150} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Описание"><textarea rows={4} maxLength={500} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field><Field label="Стоимость, ₽"><input required min="1" step="0.01" type="number" value={form.price} onChange={e => setForm({ ...form, price: e.target.value })} /></Field><div className="modal__actions"><Button type="button" variant="secondary" onClick={() => setEditing(undefined)}>Отмена</Button><Button disabled={saving} type="submit">{saving ? 'Сохраняем…' : 'Сохранить'}</Button></div></form></Modal>}
  {toast && <Toast message={toast} onClose={() => setToast('')} />}
  </div>
}
