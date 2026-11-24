# «Память»: бюро ритуальных услуг — бэкенд v3 на Spring Boot

Реализация архитектуры **«Web-интерфейс (React) → HTTP/REST/JSON →
Spring Boot Backend → JPA/SQL → PostgreSQL»**: браузер больше не
обращается к базе напрямую — вся авторизация, бизнес-логика, проверки
и обработка запросов выполняются на сервере.

Переписан с нуля с прежнего бэкенда на JDK HttpServer (v2). Контракт
API полностью сохранён — автотест `tests/test_api.py` (137 проверок)
проходит без изменений против нового сервера.

## Что изменилось по сравнению с v2

| Было (v2) | Стало (v3) |
|---|---|
| JDK HttpServer + ручная маршрутизация | Spring Boot Web (REST-контроллеры) |
| Рукописный JDBC в DAO | Spring Data JPA (Hibernate) |
| Свой парсер JSON (utils/Json) | Jackson (из коробки) |
| Свой JWT (utils/JwtUtil) | jjwt + JwtAuthFilter (Spring Security) |
| Ручные проверки прав в каждом обработчике | Spring Security: `hasRole`, правила маршрутов |
| PBKDF2/SHA-256 (utils/PasswordUtils) | без изменений (совместимость хешей) |

Схема БД, тестовые данные, форматы запросов/ответов, коды ошибок,
роли и права — **идентичны** прежней версии (см. `docs/API.md`).

## Стек

- **Java 17+** (сборка проверена на JDK 21), **Maven 3.9+**
- **Spring Boot 3.4** — Web, Data JPA, Security, Validation
- **PostgreSQL 12+** (основная БД), **H2** (профиль `demo` для локального запуска)
- **jjwt 0.12** — JWT (HS256), токен на 24 часа

## Структура проекта

```
backend-spring/
├── pom.xml                       # Maven-сборка (spring-boot-starter-web/data-jpa/security)
├── db/
│   ├── schema.sql                # схема PostgreSQL (12 таблиц)
│   └── data.sql                  # тестовые данные
├── docs/
│   └── API.md                    # справочник эндпоинтов (контракт сохранён)
├── tests/
│   └── test_api.py               # автотест: 137 проверок всех разделов
└── src/main/
    ├── java/ru/memory/backend/
    │   ├── BackendApplication.java      # точка входа Spring Boot
    │   ├── config/
    │   │   ├── SecurityConfig.java      # JWT-защита маршрутов, роли, CORS
    │   │   └── GlobalExceptionHandler.java  # {"error": "..."} для всех ошибок
    │   ├── controller/            # REST-контроллеры (/api/**)
    │   │   ├── AuthController.java       # регистрация, вход, /me
    │   │   ├── OrderController.java      # заказы: CRUD, архив, комментарии, bulk, CSV
    │   │   ├── UserController.java       # управление пользователями (админ)
    │   │   ├── AdminController.java      # аналитика, журнал аудита (админ)
    │   │   ├── ServiceController.java    # каталог услуг
    │   │   └── RootController.java       # /api, /api/health
    │   ├── entity/                # JPA-сущности (Hibernate → PostgreSQL)
    │   ├── repository/            # Spring Data JPA + проекции аналитики
    │   ├── security/              # JwtService, JwtAuthFilter, UserPrincipal
    │   ├── service/               # бизнес-логика (Auth, Order, User, Csv, Audit)
    │   └── util/PasswordUtils.java    # PBKDF2 + legacy SHA-256
    └── resources/
        ├── application.yml        # порт, БД, секрет JWT
        └── db/                    # schema/data для PostgreSQL и H2
```

## Быстрый старт (без PostgreSQL — профиль demo)

```bash
mvn package -DskipTests
java -jar target/backend-1.0.0.jar --spring.profiles.active=demo
```

Поднимается встроенная H2 (in-memory) с той же схемой и данными,
сервер слушает `http://localhost:8080/api`. Годится для демонстрации
и прогона автотеста.

## Запуск с PostgreSQL (основной режим)

1. Создайте базу и примените скрипты (в psql/pgAdmin по очереди):

```sql
-- db/schema.sql  (создаёт 12 таблиц)
-- db/data.sql    (тестовые данные)
```

2. Задайте реквизиты (переменные окружения или `application.yml`):

```bash
export MEMORY_DB_URL='jdbc:postgresql://localhost:5432/funeral_services'
export MEMORY_DB_USER='postgres'
export MEMORY_DB_PASSWORD='<пароль>'
export MEMORY_JWT_SECRET='<секрет для подписи JWT>'   # необязательно
export HTTP_PORT=8080                                  # необязательно, 8080 по умолчанию
```

3. Запустите:

```bash
mvn spring-boot:run
# или
mvn package -DskipTests
java -jar target/backend-1.0.0.jar
```

Секрет JWT берётся из `MEMORY_JWT_SECRET`; если не задан —
генерируется временный (токены действительны до перезапуска).
Пароль БД не хранится в коде и не выводится в интерфейсе.

## Тестовые учётные записи (data.sql)

| Логин | Пароль | Роль |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `manager` | `manager123` | MANAGER |
| `petrov` | `123456` | CLIENT |

После первого входа пароли тестовых записей автоматически переводятся в
PBKDF2-формат. Новые пользователи регистрируются через
`POST /api/auth/register` и получают роль CLIENT; повысить до
MANAGER/ADMIN может админ через `PUT /api/users/{id}/role`.

## Проверка

```bash
# автотест всех разделов (137 проверок; нужна чистая база
# из schema.sql + data.sql или профиль demo)
python3 tests/test_api.py http://localhost:8080/api
```

Быстрая проверка curl:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -s "http://localhost:8080/api/orders?page=1&limit=5" \
  -H "Authorization: Bearer $TOKEN"
```

## Функциональность (соответствие прежнему ТЗ)

- JWT-авторизация, три роли (CLIENT / MANAGER / ADMIN), мгновенная
  блокировка и смена роли (проверяются по БД при каждом запросе);
- CRUD заказов с валидацией: цены из каталога, сумма считается
  на сервере; клиент видит и меняет только свои заказы;
- поиск / фильтры / сортировка / пагинация списков;
- архивирование и восстановление заказов (админ);
- комментарии к заказам (автор или админ может удалить);
- массовые операции: archive / restore / delete / setStatus (админ);
- CSV-импорт/экспорт с построчным отчётом об ошибках (админ);
- аналитика: обзор, по статусам, по месяцам, топ услуг, пользователи;
- управление пользователями: роли, сброс пароля, блокировка, профиль;
- журнал аудита всех действий, включая отказы в доступе.

Полный справочник эндпоинтов — `docs/API.md`.
