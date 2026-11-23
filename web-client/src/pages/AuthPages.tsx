import { useState, type CSSProperties, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../utils'
import { Button, Field } from '../components/ui'
import heroScene from '../../../assets/plates/hero-scene.png'

const authSceneStyle = { '--auth-scene': `url(${heroScene})` } as CSSProperties

function AuthAside() {
  return <aside className="auth-aside"><Link className="brand brand--light" to="/"><span><strong>Память</strong></span></Link><div><h2>Все важные сведения всегда под рукой</h2><p>Создавайте заявки, следите за их статусом и получайте ответы координатора.</p></div><blockquote>«Бережно сопровождаем на каждом этапе»</blockquote></aside>
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ login: '', password: '' })
  const [show, setShow] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setBusy(true)
    try {
      const user = await login(form)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from || (user.role === 'ADMIN' ? '/admin/analytics' : '/orders'), { replace: true })
    } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  return <div className="auth-layout" style={authSceneStyle}><AuthAside /><section className="auth-panel"><div className="auth-form-wrap"><Link className="auth-back" to="/"><ArrowLeft size={16} /> На главную</Link><span className="auth-icon"><LockKeyhole /></span><h1>Вход в кабинет</h1><p>Введите логин и пароль, указанные при регистрации.</p>{error && <div className="form-error">{error}</div>}<form onSubmit={submit} className="form-stack"><Field label="Логин"><input autoFocus required autoComplete="username" value={form.login} onChange={e => setForm({ ...form, login: e.target.value })} placeholder="Ваш логин" /></Field><Field label="Пароль"><div className="password-input"><input required minLength={6} type={show ? 'text' : 'password'} autoComplete="current-password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="Не менее 6 символов" /><button type="button" onClick={() => setShow(!show)} aria-label={show ? 'Скрыть пароль' : 'Показать пароль'}>{show ? <EyeOff /> : <Eye />}</button></div></Field><Button disabled={busy} type="submit" className="button--wide">{busy ? 'Входим…' : <>Войти <ArrowRight size={18} /></>}</Button></form><p className="auth-switch">Нет аккаунта? <Link to="/register">Зарегистрироваться</Link></p></div></section></div>
}

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', login: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    if (!/^\+?[0-9 ()-]{10,}$/.test(form.phone)) { setError('Введите корректный номер телефона'); return }
    setBusy(true)
    try { await register(form); navigate('/orders', { replace: true }) } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }
  return <div className="auth-layout" style={authSceneStyle}><AuthAside /><section className="auth-panel"><div className="auth-form-wrap auth-form-wrap--wide"><Link className="auth-back" to="/"><ArrowLeft size={16} /> На главную</Link><h1>Регистрация клиента</h1><p>Создайте личный кабинет для оформления и отслеживания заявок.</p>{error && <div className="form-error">{error}</div>}<form onSubmit={submit} className="form-stack"><Field label="ФИО"><input required value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} placeholder="Иванов Иван Иванович" /></Field><div className="form-grid"><Field label="Телефон"><input required type="tel" autoComplete="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="+7 999 000-00-00" /></Field><Field label="Электронная почта"><input required type="email" autoComplete="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="name@example.ru" /></Field></div><div className="form-grid"><Field label="Логин"><input required minLength={3} autoComplete="username" value={form.login} onChange={e => setForm({ ...form, login: e.target.value })} placeholder="Придумайте логин" /></Field><Field label="Пароль" hint="Минимум 6 символов"><input required minLength={6} type="password" autoComplete="new-password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="••••••••" /></Field></div><label className="checkbox"><input required type="checkbox" /><span>Я согласен на обработку персональных данных</span></label><Button disabled={busy} type="submit" className="button--wide">{busy ? 'Создаём аккаунт…' : <>Создать аккаунт <ArrowRight size={18} /></>}</Button></form><p className="auth-switch">Уже есть аккаунт? <Link to="/login">Войти</Link></p></div></section></div>
}
