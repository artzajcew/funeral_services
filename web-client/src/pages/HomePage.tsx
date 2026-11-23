import { ArrowRight, Clock3, FileText, HeartHandshake, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import flowersImage from '../assets/memory-flowers.webp'
import heroScene from '../../../assets/plates/hero-scene.png'

export function HomePage() {
  const { role } = useAuth()
  const actionTo = role === 'GUEST' ? '/register' : role === 'ADMIN' ? '/admin/analytics' : role === 'MANAGER' ? '/orders' : '/orders/new'
  const actionLabel = role === 'GUEST' ? 'Оставить заявку' : role === 'ADMIN' ? 'Открыть аналитику' : role === 'MANAGER' ? 'Открыть заявки' : 'Создать заявку'

  return <>
    <section className="cinematic-hero" aria-labelledby="home-title">
      <img className="cinematic-hero__scene" src={heroScene} alt="" />
      <div className="cinematic-hero__support">Круглосуточная поддержка</div>
      <h1 id="home-title"><span className="hero-letter-d">Д</span>остойное прощание,<br />продуманное до <em>деталей.</em></h1>
      <p className="cinematic-hero__copy">Берём на себя организационные вопросы<br />и остаёмся рядом на каждом этапе.</p>
      <Link className="cinematic-hero__cta" to={actionTo}><span>{actionLabel}</span></Link>
      <div className="cinematic-hero__bottom" aria-label="Основные направления">
        <span className="cinematic-hero__promise">Понятная стоимость и личный координатор</span>
        <Link to="/services"><FileText aria-hidden="true" />Документы</Link>
        <i aria-hidden="true" />
        <Link to="/services"><HeartHandshake aria-hidden="true" />Церемония</Link>
        <i aria-hidden="true" />
        <Link to="/services"><Clock3 aria-hidden="true" />Транспорт</Link>
      </div>
    </section>

    <section className="editorial-section cinematic-section">
      <div className="container editorial-intro">
        <div className="editorial-intro__title"><span className="section-kicker">Бюро «Память»</span><h2>Берём на себя сложные организационные вопросы</h2></div>
        <p>Собираем всё необходимое в одной заявке: от оформления документов до проведения церемонии. Координатор остаётся на связи и объясняет дальнейшие действия.</p>
      </div>

      <div className="container service-story">
        <figure className="service-story__image"><img src={flowersImage} alt="Флорист составляет композицию из белых лилий" /></figure>
        <div className="service-story__content">
          <span className="section-kicker">Забота в деталях</span>
          <h3>Спокойное сопровождение на каждом этапе</h3>
          <div className="service-story__list">
            <article><ShieldCheck /><div><h4>Понятная стоимость</h4><p>Цена каждой услуги видна до отправки заявки.</p></div></article>
            <article><Clock3 /><div><h4>Всегда на связи</h4><p>Комментарии и статусы собраны в личном кабинете.</p></div></article>
            <article><HeartHandshake /><div><h4>Личный координатор</h4><p>Один специалист сопровождает заявку от начала до завершения.</p></div></article>
          </div>
          <Link className="editorial-link" to="/services">Открыть каталог <ArrowRight size={17} /></Link>
        </div>
      </div>
    </section>

    <section className="process-section" id="process">
      <div className="container process-layout">
        <header><span className="section-kicker">Как мы работаем</span><h2>Спокойно.<br />Последовательно.</h2></header>
        <ol>
          <li><b>01</b><div><h3>Выберите услуги</h3><p>Изучите каталог и добавьте нужные позиции.</p></div></li>
          <li><b>02</b><div><h3>Оставьте заявку</h3><p>Проверьте состав и сообщите важные детали.</p></div></li>
          <li><b>03</b><div><h3>Следите за исполнением</h3><p>Получайте обновления и пишите координатору.</p></div></li>
        </ol>
      </div>
    </section>

    <section className="support-section">
      <img src={flowersImage} alt="Белые цветы в мастерской флориста" />
      <div className="support-section__shade" />
      <div className="container support-section__content">
        <div><span className="section-kicker">Мы рядом</span><h2>Не оставайтесь с вопросами один на один</h2><p>Специалист бюро ответит и поможет определить дальнейшие действия.</p></div>
        <a className="button button--primary button--large" href="tel:+74951234567">Позвонить нам</a>
      </div>
    </section>
  </>
}
