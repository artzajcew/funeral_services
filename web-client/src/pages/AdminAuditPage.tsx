import { useCallback, useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { api, unwrapList } from '../api/http'
import { EmptyState, ErrorState, Spinner } from '../components/ui'
import type { AuditEntry } from '../types'
import { dateTime, errorMessage } from '../utils'

export function AdminAuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setEntries(unwrapList(await api.get<AuditEntry[] | { content: AuditEntry[] } | { data: AuditEntry[] }>('/api/audit'))) }
    catch (err) { setError(errorMessage(err)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  const visible = entries.filter(entry => `${entry.userName || entry.userLogin || ''} ${entry.action} ${entry.entityType || ''} ${entry.details || ''}`.toLowerCase().includes(query.toLowerCase()))
  return <div className="page"><div className="container"><div className="page-heading page-heading--editorial"><div><h1>Журнал действий</h1><p>Изменения, выполненные пользователями системы.</p></div></div><div className="toolbar"><label className="search-box"><Search size={19} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Пользователь или действие" /></label><span className="result-count">{visible.length} записей</span></div>{loading ? <Spinner /> : error ? <ErrorState message={error} retry={load} /> : visible.length === 0 ? <EmptyState title="Записей не найдено" text="Журнал пока пуст или ничего не подходит под запрос." /> : <div className="audit-list">{visible.map(entry => <article key={entry.id}><time>{dateTime(entry.createdAt)}</time><div><strong>{entry.action}</strong><p>{entry.details || `${entry.entityType || 'Объект'}${entry.entityId ? ` №${entry.entityId}` : ''}`}</p></div><small>{entry.userName || entry.userLogin || 'Система'}</small></article>)}</div>}</div></div>
}
