# «Память»: бюро ритуальных услуг — бэкенд v2

Проект состоит из:
- **REST-бэкенда** (`src/api`) — HTTP-сервер на JDK без внешних зависимостей,
  аутентификация JWT, PostgreSQL;
- **консольного приложения** (`src/Main.java`, `src/ui`) — работает через те же DAO;
- **JavaFX-клиента** (`javafx-client/`) — подключается к той же БД через JDBC.

Реализованы все пункты ТЗ: JWT-авторизация, три роли, CRUD заказов, поиск/
фильтры/сортировка/пагинация, архив (админ), комментарии, массовые операции
(админ), CSV-импорт/экспорт (админ), аналитика (админ), управление
пользователями (админ), журнал аудита (админ). Соответствие пунктам ТЗ —
в `docs/TZ_CHECKLIST.md`, справочник API — в `docs/API.md`.

## Требования

- JDK 17+ (сборка проверена на JDK 21);
- PostgreSQL 12+;
- драйвер `lib/postgresql-42.7.12.jar`.

## Подготовка базы

### Новая база
```sql
-- в psql/pgAdmin по очереди:
--   src/db/schema.sql  (создаёт 12 таблиц)
--   src/db/data.sql    (тестовые данные)
```

### Существующая база (без потери данных)
```sql
-- src/db/migration_v2.sql — идемпотентный, можно запускать повторно
```

## Сборка и запуск REST-сервера

```bash
# Linux/macOS
javac -cp lib/postgresql-42.7.12.jar -encoding UTF-8 -d out \
  src/Main.java src/ui/*.java src/dao/*.java src/models/*.java \
  src/utils/*.java src/db/*.java src/api/*.java

java -cp out:lib/postgresql-42.7.12.jar api.ApiServer
```
```bat
:: Windows
javac -cp lib\postgresql-42.7.12.jar -encoding UTF-8 -d out src\Main.java src\ui\*.java src\dao\*.java src\models\*.java src\utils\*.java src\db\*.java src\api\*.java
java -cp "out;lib\postgresql-42.7.12.jar" api.ApiServer
```
Или скриптами: `run_api.sh` / `run_api.bat`.

Сервер слушает порт `8080` (переменная `HTTP_PORT`). Swagger нет — контракт
описан в `docs/API.md`, автотест — `tests/test_api.py`.

## Конфигурация

Скопируйте `database.properties.example` → `database.properties` и укажите
реквизиты. Переменные окружения имеют приоритет:

```bash
export MEMORY_DB_URL='jdbc:postgresql://localhost:5432/funeral_services'
export MEMORY_DB_USER='postgres'
export MEMORY_DB_PASSWORD='<пароль>'
export MEMORY_JWT_SECRET='<секрет для подписи JWT>'   # необязательно
export HTTP_PORT=8080
```

Секрет JWT берётся из `MEMORY_JWT_SECRET`, затем из `jwt.secret` в
`database.properties`; если не задан — генерируется и сохраняется в файл
`.jwt-secret` (токены переживают перезапуск). Пароль БД не выводится в
интерфейсе и не хранится в коде.

## Тестовые учётные записи (data.sql)

| Логин | Пароль | Роль |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `manager` | `manager123` | MANAGER |
| `petrov` | `123456` | CLIENT |

После первого входа пароли тестовых записей автоматически переводятся в
PBKDF2-формат. Новые пользователи регистрируются через `POST /api/auth/register`
и получают роль CLIENT; повысить до MANAGER/ADMIN может админ через
`PUT /api/users/{id}/role`.

## Консольное приложение (совместимость)

```bash
java -cp "out:lib/postgresql-42.7.12.jar" Main     # Linux
java -cp "out;lib\postgresql-42.7.12.jar" Main     # Windows
```

## Проверка сборки и API

```bash
# 1. компиляция без ошибок — команда javac выше
# 2. автотест API (137 проверок; нужна чистая база из schema.sql + data.sql)
python3 tests/test_api.py http://localhost:8080/api
```

## Структура бэкенда

```
src/
  api/    REST-сервер: маршрутизация, JWT, обработчики (ApiServer, AuthService,
          OrderHandler, UserHandler, AdminHandler, ApiContext, ApiError)
  dao/    Доступ к данным (OrderDAO, UserDAO, ClientDAO, CommentDAO,
          AuditDAO, AnalyticsDAO, Page, DaoException)
  models/ User, Client, Order, OrderItem, Service, Comment, AuditEntry
  utils/  Json (мини-библиотека JSON), JwtUtil (HS256), PasswordUtils
          (PBKDF2/SHA-256), ConsoleHelper
  db/     DatabaseConnector, DatabaseConfig, schema.sql, data.sql,
          migration_v2.sql
```
