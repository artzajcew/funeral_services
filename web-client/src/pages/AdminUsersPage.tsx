import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Edit3, Search } from 'lucide-react'
import { api, unwrapList } from '../api/http'
import { Button, EmptyState, ErrorState, Field, Modal, Spinner, StatusBadge, Toast } from '../components/ui'
import type { Role, User } from '../types'
import { errorMessage } from '../utils'

const initials = (name: string) => name.split(' ').filter(Boolean).map(part => part[0]).slice(0, 2).join('')

export function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<User | null>(null)
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', role: 'CLIENT', enabled: true })
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [toast, setToast] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = unwrapList(await api.get<User[] | { content: User[] } | { data: User[] }>('/api/users'))
      setUsers(result.map(user => ({ ...user, enabled: !user.blocked })))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const edit = (user: User) => {
    setEditing(user)
    setFormError('')
    setForm({ fullName: user.fullName, email: user.email || '', phone: user.phone || '', role: user.role, enabled: !user.blocked })
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!editing) return
    setSaving(true)
    setFormError('')
    try {
      const profile: { fullName: string; email?: string; phone?: string } = { fullName: form.fullName }
      if (editing.email != null) profile.email = form.email
      if (editing.phone != null) profile.phone = form.phone
      await api.put<User>(`/api/users/${editing.id}`, profile)
      if (form.role !== editing.role) {
        await api.put(`/api/users/${editing.id}/role`, { role: form.role })
      }
      if (form.enabled === Boolean(editing.blocked)) {
        await api.post(`/api/users/${editing.id}/${form.enabled ? 'unblock' : 'block'}`)
      }
      setEditing(null)
      setToast('Данные пользователя обновлены')
      await load()
    } catch (err) {
      setFormError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const visible = users.filter(user => `${user.login} ${user.fullName} ${user.email || ''}`.toLowerCase().includes(query.toLowerCase()))

  return <div className="page">
    <div className="container">
      <div className="page-heading page-heading--editorial"><div><h1>Пользователи</h1><p>Контактные данные, роли и доступ к системе.</p></div></div>
      <div className="toolbar">
        <label className="search-box"><Search size={19} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Имя, логин или почта" /></label>
        <span className="result-count">Всего: {visible.length}</span>
      </div>
      {loading ? <Spinner /> : error ? <ErrorState message={error} retry={load} /> : visible.length === 0 ? <EmptyState title="Пользователи не найдены" text="Попробуйте изменить поисковый запрос." /> :
        <div className="table-card table-card--plain"><div className="table-scroll"><table>
          <thead><tr><th>Пользователь</th><th>Контакты</th><th>Роль</th><th>Статус</th><th aria-label="Действия" /></tr></thead>
          <tbody>{visible.map(user => <tr key={user.id}>
            <td><div className="table-person"><span>{initials(user.fullName)}</span><div><strong>{user.fullName}</strong><small>@{user.login}</small></div></div></td>
            <td><div className="stacked-cell"><span>{user.email || '-'}</span><small>{user.phone || 'Телефон не указан'}</small></div></td>
            <td><StatusBadge tone={user.role === 'ADMIN' ? 'violet' : user.role === 'MANAGER' ? 'gold' : 'blue'}>{user.role === 'ADMIN' ? 'Администратор' : user.role === 'MANAGER' ? 'Менеджер' : 'Клиент'}</StatusBadge></td>
            <td><StatusBadge tone={user.blocked ? 'gray' : 'green'}>{user.blocked ? 'Отключён' : 'Активен'}</StatusBadge></td>
            <td><button className="row-link" onClick={() => edit(user)} aria-label={`Изменить ${user.fullName}`}><Edit3 size={18} /></button></td>
          </tr>)}</tbody>
        </table></div></div>}
    </div>

    {editing && <Modal title="Изменить пользователя" onClose={() => setEditing(null)}>
      <form className="modal__body form-stack" onSubmit={submit}>
        {formError && <div className="form-error">{formError}</div>}
        <Field label="ФИО"><input required value={form.fullName} onChange={event => setForm({ ...form, fullName: event.target.value })} /></Field>
        <div className="form-grid">
          <Field label="Email"><input type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} /></Field>
          <Field label="Телефон"><input type="tel" value={form.phone} onChange={event => setForm({ ...form, phone: event.target.value })} /></Field>
        </div>
        <Field label="Роль"><select value={form.role} onChange={event => setForm({ ...form, role: event.target.value as Exclude<Role, 'GUEST'> })}><option value="CLIENT">Клиент</option><option value="MANAGER">Менеджер</option><option value="ADMIN">Администратор</option></select></Field>
        <label className="checkbox"><input type="checkbox" checked={form.enabled} onChange={event => setForm({ ...form, enabled: event.target.checked })} /><span>Учётная запись активна</span></label>
        <div className="modal__actions"><Button type="button" variant="secondary" onClick={() => setEditing(null)}>Отмена</Button><Button disabled={saving} type="submit">{saving ? 'Сохраняем…' : 'Сохранить'}</Button></div>
      </form>
    </Modal>}
    {toast && <Toast message={toast} onClose={() => setToast('')} />}
  </div>
}
