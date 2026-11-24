# «Память»: JavaFX + PostgreSQL

JavaFX-интерфейс системы ритуальных услуг подключён к структуре PostgreSQL из
репозитория [`artzajcew/funeral_services`](https://github.com/artzajcew/funeral_services).
Снимок бэкенда импортирован без изменений в каталог `backend/`.

В репозитории бэкенда нет отдельного HTTP-сервера. Его слой данных состоит из
Java DAO-классов, которые напрямую работают с PostgreSQL. Поэтому JavaFX-клиент
также подключается к той же базе через JDBC.

## Что работает в JavaFX

- загрузка заказов, клиентов, услуг и позиций заказа из PostgreSQL;
- `TableView`, выбор строки, поиск, фильтрация по статусу и периоду, сортировка;
- создание заказа с существующим или новым клиентом и несколькими услугами;
- редактирование клиента, статуса и состава заказа; итоговая сумма рассчитывается автоматически;
- удаление заказа с подтверждением;
- обновление данных из базы;
- отдельный экран аналитики;
- экспорт текущей выборки в CSV;
- JavaFX-диалоги создания, редактирования, подтверждения и ошибок.
- раздельные слои `Controller` → `Service` → `Repository/JDBC`.

## Требования

- Java 21 или новее;
- Maven 3.9 или новее;
- PostgreSQL;
- база `funeral_services` со схемой из `../src/db/schema.sql`.

## Подготовка базы

Самый быстрый вариант — PostgreSQL в Docker. Создайте локальный файл `.env`
по шаблону и запустите контейнер:

```bash
cp .env.example .env
# замените replace_me в .env на локальный пароль
docker compose up -d
```

Схема и тестовые данные применяются автоматически при первом создании Docker-volume.
Для уже созданного volume повторный запуск SQL-файлов не выполняется.

Либо подготовьте установленный локально PostgreSQL вручную:

Эти команды изменяют PostgreSQL, поэтому выполняйте их вручную только для новой
или тестовой базы. `schema.sql` удаляет одноимённые таблицы перед созданием.

```bash
createdb -h localhost -p 5432 -U postgres funeral_services
psql -h localhost -p 5432 -U postgres -d funeral_services -f ../src/db/schema.sql
psql -h localhost -p 5432 -U postgres -d funeral_services -f ../src/db/data.sql
```

Скопируйте безопасный шаблон конфигурации и укажите локальные реквизиты PostgreSQL:

```bash
cp database.properties.example database.properties
```

Файл `database.properties` исключён из Git. Не добавляйте его в коммиты. Формат файла:

```properties
db.url=jdbc:postgresql://localhost:5432/funeral_services
db.user=postgres
db.password=<локальный пароль PostgreSQL>
```

Вместо файла можно использовать переменные окружения:

```bash
export MEMORY_DB_URL='<адрес PostgreSQL>'
export MEMORY_DB_USER='<пользователь PostgreSQL>'
export MEMORY_DB_PASSWORD='<пароль PostgreSQL>'
```

Переменные окружения имеют приоритет над `database.properties`. Путь к другому файлу
можно передать через `MEMORY_DB_CONFIG` или системное свойство `memory.db.config`.
Пароль не выводится в интерфейсе, не включается в экспорт и не хранится в Java-коде.

## Запуск

Из каталога проекта:

```bash
mvn javafx:run
```

Если база отсутствует или недоступна, приложение всё равно откроется и покажет
понятное сообщение. После подготовки базы нажмите кнопку «Обновить».

## Проверка сборки

```bash
mvn clean test
```

## Основные файлы фронтенда

- `src/main/java/ru/memory/fx/MainController.java`;
- `src/main/java/ru/memory/fx/data/DatabaseConfig.java`;
- `src/main/java/ru/memory/fx/data/JdbcOrderGateway.java`;
- `src/main/java/ru/memory/fx/service/`;
- `src/main/resources/ru/memory/fx/main-view.fxml`;
- `src/main/resources/ru/memory/fx/theme.css`.

## Ограничения контракта бэкенда

Таблица `orders` и `OrderDAO` хранят только клиента, дату создания, статус и
сумму. Отдельных полей «дата церемонии» и «место проведения» в заказе нет.
Покойный и дополнительные параметры услуг связаны через `order_items`, но
бэкенд не предоставляет операции их редактирования. Интерфейс не имитирует эти
возможности и показывает только те данные, которые реально поддерживает схема.
