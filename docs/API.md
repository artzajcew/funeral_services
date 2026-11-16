# REST API «Память» — справочник эндпоинтов

Базовый URL: `http://localhost:8080/api` (порт задаётся переменной `HTTP_PORT`).

Формат запросов и ответов — JSON (UTF-8). Аутентификация — JWT Bearer:

```
Authorization: Bearer <токен>
```

Токен выдаётся на 24 часа (`/auth/register`, `/auth/login`). Роль и блокировка
проверяются по актуальным данным из БД при каждом запросе: блокировка учётной
записи и смена роли действуют мгновенно.

## Ответ об ошибке

```json
{ "error": "Человекочитаемое описание проблемы" }
```

Коды: 400 — ошибка валидации, 401 — нет/просрочен токен, 403 — нет прав или
заблокирован, 404 — не найдено, 409 — конфликт (дубликат, повторное действие),
405 — неверный метод, 500 — ошибка сервера.

## Роли и права

| Возможность | CLIENT | MANAGER | ADMIN |
|---|---|---|---|
| Регистрация, вход, каталог услуг | да | да | да |
| Свои заказы: CRUD, комментарии | да | — | — |
| Все заказы: CRUD, комментарии | — | да | да |
| Смена статуса любого заказа | — | да | да |
| Архивирование / восстановление | — | — | да |
| Массовые операции | — | — | да |
| Импорт / экспорт CSV | — | — | да |
| Аналитика | — | — | да |
| Управление пользователями | — | — | да |
| Журнал аудита | — | — | да |

Регистрация через API всегда создаёт пользователя с ролью `CLIENT`.

## Пагинация (все списки)

Параметры: `page` (с 1, по умолчанию 1), `limit` (1–100, по умолчанию 10).

```json
{ "data": [ ... ], "page": 1, "limit": 10, "total": 42, "totalPages": 5 }
```

---

## 1. Аутентификация

### POST /api/auth/register
Создаёт клиента (роль CLIENT). Публичный.
```json
{ "login": "ivanov", "password": "secret1", "fullName": "Иванов Иван Иванович",
  "phone": "+79001234567", "email": "ivanov@example.com" }
```
`email` необязателен. Ответ: `token`, `user`, `expiresIn`.

### POST /api/auth/login
```json
{ "login": "admin", "password": "admin123" }
```
Ответ: `token`, `user`. Неудача: 401. Заблокирован: 403.

### GET /api/auth/me
Текущий пользователь + профиль клиента (если есть).

---

## 2. Заказы (основная сущность)

### GET /api/orders — список с поиском, фильтрами, сортировкой
Клиент видит только свои заказы (архивные скрыты всегда). Сотрудники видят все.

Параметры:
- `search` — подстрока: ФИО клиента, телефон, e-mail, статус, номер заказа;
- `status` — `NEW | IN_PROGRESS | COMPLETED | CANCELLED`;
- `clientId` — фильтр по клиенту (сотрудники);
- `dateFrom`, `dateTo` — период создания, `YYYY-MM-DD`;
- `minCost`, `maxCost` — сумма заказа;
- `archived` — `false` (по умолчанию) / `true` / `all` (сотрудники);
- `sortBy` — `created_at | id | status | total_cost | client_name`;
- `sortDir` — `ASC | DESC`.

### GET /api/orders/{id}
Детали заказа + `items` (позиции с названиями услуг). Клиент — только свой.

### POST /api/orders
```json
{ "items": [ { "serviceId": 1, "quantity": 2 }, { "serviceId": 3, "quantity": 1 } ],
  "clientId": 4 }
```
`clientId` указывают только сотрудники (для клиента заказ создаётся на его
профиль). Цены берутся из каталога, `totalCost` считается на сервере.
Ответ 201: заказ с `items`.

### PUT /api/orders/{id}
- клиент: `items` (пока статус NEW) или `status: "CANCELLED"` (свой заказ);
- сотрудник: `items`, `status`, `clientId` — любой заказ.

### DELETE /api/orders/{id}
Клиент — свой (кроме COMPLETED), сотрудник — любой. Архивные сначала
восстанавливаются. Позиции удаляются каскадно.

---

## 3. Архив (только админ)

- `POST /api/orders/{id}/archive` — скрыть запись из списков;
- `POST /api/orders/{id}/restore` — вернуть из архива.

Архивные заказы видны в списке только при `?archived=true` (сотрудники),
у клиента они скрыты полностью (404). Повторное архивирование — 409.

---

## 4. Комментарии

- `GET /api/orders/{id}/comments` — клиент для своих, сотрудники для любых;
- `POST /api/orders/{id}/comments` — `{ "text": "до 2000 символов" }`;
- `DELETE /api/orders/{id}/comments/{commentId}` — автор или админ.

Ответ комментария:
```json
{ "id": 1, "orderId": 4, "authorId": 2, "authorName": "Петров Петр Петрович",
  "authorRole": "CLIENT", "text": "...", "createdAt": "2026-09-10 12:00:00" }
```

---

## 5. Массовые операции (только админ)

### POST /api/orders/bulk
```json
{ "ids": [3, 4, 5], "action": "archive" }
```
Действия: `archive`, `restore`, `delete`, `setStatus` (плюс `"status": "NEW"`).
Не более 1000 ID за раз. Ответ: `success`, `failed` (с причинами по каждому ID).

---

## 6. CSV (только админ)

### GET /api/orders/export
Экспорт текущей выборки (параметры как у списка). UTF-8 c BOM, разделитель `;`.
Колонки: `id;client_id;client_name;created_at;status;total_cost;archived;comments_count`.
По умолчанию выгружает всё (`archived=all`).

### POST /api/orders/import
Тело запроса — CSV-текст (`Content-Type: text/csv`). Обязательный заголовок,
разделитель `;` или `,`:
```
client_id;status;total_cost;created_at
1;NEW;12500.50;2026-10-01 10:00:00
2;IN_PROGRESS;30000;2026-10-02
```
`status` и `created_at` необязательны (`NEW`, текущее время). Валидные строки
импортируются, ошибки возвращаются построчно:
```json
{ "imported": 2, "totalLines": 6, "errorsCount": 1,
  "errors": [ { "line": 4, "error": "Клиент с ID 9999 не существует" } ] }
```

---

## 7. Аналитика (только админ)

### GET /api/analytics?from=2026-01-01&to=2026-12-31&topLimit=10
```json
{ "period": { "from": "...", "to": "..." },
  "overview": { "orderCount": 8, "revenue": 152000.0, "avgOrderCost": 19000.0,
                "minOrderCost": 40000.0, "maxOrderCost": 80000.0, "archivedCount": 1 },
  "byStatus": { "NEW": 2, "IN_PROGRESS": 1, "COMPLETED": 2, "CANCELLED": 1 },
  "byMonth":  [ { "month": "2026-09", "orderCount": 4, "totalSum": 239000.0 } ],
  "topServices": [ { "serviceName": "Копка могилы", "soldCount": 2, "revenue": 30000.0 } ],
  "users": { "total": 6, "blockedTotal": 0,
             "byRole": { "ADMIN": 1, "MANAGER": 1, "CLIENT": 4 },
             "blockedByRole": { "ADMIN": 0, "MANAGER": 0, "CLIENT": 0 } } }
```
Выручка — сумма заказов `COMPLETED` за период.

---

## 8. Управление пользователями (только админ)

- `GET /api/users?search=&role=CLIENT&blocked=false&page=1&limit=10` — список
  (login, ФИО, роль, блокировка, телефон/e-mail, число заказов, последнее действие);
- `GET /api/users/{id}` — карточка;
- `PUT /api/users/{id}/role` — `{ "role": "ADMIN|MANAGER|CLIENT" }`
  (нельзя понизить свою роль);
- `POST /api/users/{id}/reset-password` — `{}` (автогенерация) или
  `{ "newPassword": "..." }`; новый пароль возвращается в ответе;
- `POST /api/users/{id}/block`, `POST /api/users/{id}/unblock`
  (себя блокировать нельзя);
- `PUT /api/users/{id}` — `{ "fullName": "...", "phone": "...", "email": "..." }`.

---

## 9. Журнал аудита (только админ)

### GET /api/audit?page=1&limit=20&userId=&action=&entityType=&dateFrom=&dateTo=
Запись: `id, userId, userLogin, action, entityType, entityId, details, createdAt`.

Фиксируются: `REGISTER`, `LOGIN`, `LOGIN_FAILED`, `LOGIN_BLOCKED`,
`CREATE_ORDER`, `UPDATE_ORDER`, `DELETE_ORDER`, `ARCHIVE_ORDER`,
`RESTORE_ORDER`, `ADD_COMMENT`, `DELETE_COMMENT`, `BULK_OPERATION`,
`EXPORT_ORDERS`, `IMPORT_ORDERS`, `USER_ROLE_CHANGED`, `USER_UPDATED`,
`USER_PASSWORD_RESET`, `USER_BLOCKED`, `USER_UNBLOCKED`, `ACCESS_DENIED`.

### GET /api/audit/actions — список типов действий (для фильтров UI).

---

## 10. Прочее

- `GET /api/services` — каталог услуг (любая роль, для создания заказа);
- `GET /api/health` — проверка живости (без авторизации).

## Быстрая проверка (curl)

```bash
# вход админа
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# список заказов
curl -s "http://localhost:8080/api/orders?page=1&limit=5" -H "Authorization: Bearer $TOKEN"

# аналитика
curl -s "http://localhost:8080/api/analytics" -H "Authorization: Bearer $TOKEN"
```

Автотест всех разделов: `python3 tests/test_api.py http://localhost:8080/api`
(требует чистую базу из `src/db/schema.sql` + `src/db/data.sql`).
