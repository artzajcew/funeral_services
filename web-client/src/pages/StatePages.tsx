import { Link } from 'react-router-dom'

export function ForbiddenPage() {
  return <div className="state-page"><span>403</span><h1>Доступ закрыт</h1><p>У вашей учётной записи нет прав для просмотра этой страницы.</p><Link className="button button--primary" to="/">На главную</Link></div>
}

export function NotFoundPage() {
  return <div className="state-page"><span>404</span><h1>Страница не найдена</h1><p>Возможно, адрес изменился или страница была удалена.</p><Link className="button button--primary" to="/">На главную</Link></div>
}
