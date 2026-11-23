import { AlertCircle, CheckCircle2, LoaderCircle, X } from 'lucide-react'
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from 'react'

export function Button({ className = '', variant = 'primary', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost' }) {
  return <button className={`button button--${variant} ${className}`} {...props} />
}

export function Spinner({ label = 'Загрузка…' }: { label?: string }) {
  return <div className="loading"><LoaderCircle className="spin" size={22} /><span>{label}</span></div>
}

export function EmptyState({ title, text, action }: { title: string; text: string; action?: ReactNode }) {
  return <div className="empty-state"><h3>{title}</h3><p>{text}</p>{action}</div>
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="alert alert--error"><AlertCircle size={20} /><div><strong>Не удалось загрузить данные</strong><p>{message}</p>{retry && <button className="text-button" onClick={retry}>Попробовать снова</button>}</div></div>
}

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return <label className="field"><span className="field__label">{label}</span>{children}{hint && <small>{hint}</small>}{error && <span className="field__error">{error}</span>}</label>
}

export function StatusBadge({ tone, children }: { tone: string; children: ReactNode }) {
  return <span className={`badge badge--${tone}`}>{children}</span>
}

export function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  useEffect(() => {
    const listener = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.addEventListener('keydown', listener)
    return () => document.removeEventListener('keydown', listener)
  }, [onClose])
  return <div className="modal-backdrop" role="presentation" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" data-lenis-prevent>
      <header className="modal__header"><h2 id="modal-title">{title}</h2><button className="icon-button" aria-label="Закрыть" onClick={onClose}><X size={21} /></button></header>
      {children}
    </section>
  </div>
}

export function Toast({ message, type = 'success', onClose }: { message: string; type?: 'success' | 'error'; onClose: () => void }) {
  useEffect(() => {
    const timer = window.setTimeout(onClose, 4000)
    return () => window.clearTimeout(timer)
  }, [onClose])
  return <div className={`toast toast--${type}`} role="status">
    {type === 'success' ? <CheckCircle2 size={20} /> : <AlertCircle size={20} />}
    <span>{message}</span><button onClick={onClose} aria-label="Закрыть"><X size={17} /></button>
  </div>
}
