-- ============================================================
-- schema.sql
-- Система автоматизации бюро ритуальных услуг
-- PostgreSQL
<<<<<<< HEAD
--
-- Версия 2 (согласно ТЗ):
--   * пользователи: роли (ADMIN/MANAGER/CLIENT) и блокировка;
--   * заказы: архивирование;
--   * комментарии к заказам;
--   * журнал аудита.
-- ============================================================

-- Удаление таблиц при повторном запуске schema.sql
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS order_comments CASCADE;
=======
-- ============================================================

-- Удаление таблиц при повторном запуске schema.sql
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS delivery CASCADE;
DROP TABLE IF EXISTS digging CASCADE;
DROP TABLE IF EXISTS deceased CASCADE;
DROP TABLE IF EXISTS decor CASCADE;
DROP TABLE IF EXISTS coffins CASCADE;
DROP TABLE IF EXISTS services CASCADE;
DROP TABLE IF EXISTS clients CASCADE;
DROP TABLE IF EXISTS users CASCADE;


-- ============================================================
-- 1. USERS
-- Пользователи системы
<<<<<<< HEAD
--   role: ADMIN   — администратор (все функции, включая
--                   архив, массовые операции, CSV, аналитику,
--                   управление пользователями и аудит);
--         MANAGER — сотрудник бюро (работа со всеми заказами);
--         CLIENT  — клиент (только свои заказы).
=======
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
-- ============================================================

CREATE TABLE users (
    id SERIAL PRIMARY KEY,

    login VARCHAR(50) NOT NULL UNIQUE,

    password VARCHAR(255) NOT NULL,

    full_name VARCHAR(150) NOT NULL,

<<<<<<< HEAD
    role VARCHAR(20) NOT NULL DEFAULT 'CLIENT',

    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'MANAGER', 'CLIENT'))
=======
    role VARCHAR(20) NOT NULL,

    CONSTRAINT chk_users_role
        CHECK (role IN ('CLIENT', 'ADMIN'))
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
);


-- ============================================================
-- 2. CLIENTS
<<<<<<< HEAD
-- Клиенты бюро (профиль привязан к учётной записи)
=======
-- Клиенты бюро
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
-- ============================================================

CREATE TABLE clients (
    id SERIAL PRIMARY KEY,

    user_id INTEGER NOT NULL UNIQUE,

    phone VARCHAR(20) NOT NULL UNIQUE,

    email VARCHAR(100) UNIQUE,

    full_name VARCHAR(150) NOT NULL,

    CONSTRAINT fk_clients_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);


-- ============================================================
-- 3. SERVICES
-- Каталог услуг
-- ============================================================

CREATE TABLE services (
    id SERIAL PRIMARY KEY,

    name VARCHAR(150) NOT NULL UNIQUE,

    price NUMERIC(10, 2) NOT NULL,

    CONSTRAINT chk_services_price
        CHECK (price > 0)
);


-- ============================================================
-- 4. COFFINS
-- Гробы
-- ============================================================

CREATE TABLE coffins (
    id SERIAL PRIMARY KEY,

    material VARCHAR(50) NOT NULL,

    color VARCHAR(50) NOT NULL,

    price NUMERIC(10, 2) NOT NULL,

    CONSTRAINT chk_coffins_price
        CHECK (price > 0),

    CONSTRAINT chk_coffins_material
        CHECK (material IN ('Массив дерева', 'ДСП', 'Металл')),

    CONSTRAINT chk_coffins_color
        CHECK (color IN ('Оригинальный', 'Белый'))
);


-- ============================================================
-- 5. DECOR
-- Декор
-- ============================================================

CREATE TABLE decor (
    id SERIAL PRIMARY KEY,

    type VARCHAR(100) NOT NULL,

    price NUMERIC(10, 2) NOT NULL,

    CONSTRAINT chk_decor_price
        CHECK (price > 0)
);


-- ============================================================
-- 6. DIGGING
-- Копка могилы
-- ============================================================

CREATE TABLE digging (
    id SERIAL PRIMARY KEY,

    cemetery VARCHAR(150) NOT NULL,

    place VARCHAR(100) NOT NULL
);


-- ============================================================
-- 7. DECEASED
-- Данные покойного
-- ============================================================

CREATE TABLE deceased (
    id SERIAL PRIMARY KEY,

    full_name VARCHAR(150) NOT NULL,

    birth_date DATE NOT NULL,

    death_date DATE NOT NULL,

    CONSTRAINT chk_deceased_dates
        CHECK (death_date >= birth_date)
);


-- ============================================================
-- 8. DELIVERY
-- Доставка
-- ============================================================

CREATE TABLE delivery (
    id SERIAL PRIMARY KEY,

    "from" VARCHAR(200) NOT NULL,

    "to" VARCHAR(200) NOT NULL
);


-- ============================================================
-- 9. ORDERS
-- Заказы клиентов
<<<<<<< HEAD
--   is_archived — запись перенесена в архив (только админ);
--                 такие записи скрываются из обычных списков.
=======
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
-- ============================================================

CREATE TABLE orders (
    id SERIAL PRIMARY KEY,

    client_id INTEGER NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status VARCHAR(30) NOT NULL DEFAULT 'NEW',

    total_cost NUMERIC(12, 2) NOT NULL DEFAULT 0,

<<<<<<< HEAD
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,

    archived_at TIMESTAMP,

=======
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
    CONSTRAINT fk_orders_client
        FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'NEW',
                'IN_PROGRESS',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_orders_total_cost
        CHECK (total_cost >= 0)
);


-- ============================================================
-- 10. ORDER_ITEMS
-- Позиции заказа
-- ============================================================

CREATE TABLE order_items (
    id SERIAL PRIMARY KEY,

    order_id INTEGER NOT NULL,

    service_id INTEGER NOT NULL,

    quantity INTEGER NOT NULL DEFAULT 1,

    price NUMERIC(10, 2) NOT NULL,

    -- Дополнительные данные конкретной услуги
<<<<<<< HEAD
    coffin_id INTEGER,
    decor_id INTEGER,
    digging_id INTEGER,
    delivery_id INTEGER,
    deceased_id INTEGER,
=======
    coffin_id INTEGER UNIQUE,

    decor_id INTEGER UNIQUE,

    digging_id INTEGER UNIQUE,

    delivery_id INTEGER UNIQUE,

    deceased_id INTEGER UNIQUE,
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_order_items_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_coffin
        FOREIGN KEY (coffin_id)
        REFERENCES coffins(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_decor
        FOREIGN KEY (decor_id)
        REFERENCES decor(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_digging
        FOREIGN KEY (digging_id)
        REFERENCES digging(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_delivery
        FOREIGN KEY (delivery_id)
        REFERENCES delivery(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_deceased
        FOREIGN KEY (deceased_id)
        REFERENCES deceased(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_order_items_quantity
        CHECK (quantity >= 1),

    CONSTRAINT chk_order_items_price
        CHECK (price > 0)
);


-- ============================================================
<<<<<<< HEAD
-- 11. ORDER_COMMENTS
-- Комментарии к заказам.
-- Клиент пишет к своим заказам, менеджер и администратор —
-- к любым. Удалить комментарий может его автор или админ.
-- ============================================================

CREATE TABLE order_comments (
    id SERIAL PRIMARY KEY,

    order_id INTEGER NOT NULL,

    author_id INTEGER NOT NULL,

    body VARCHAR(2000) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_comments_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_order_comments_author
        FOREIGN KEY (author_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_order_comments_body
        CHECK (LENGTH(TRIM(body)) > 0)
);


-- ============================================================
-- 12. AUDIT_LOG
-- Журнал аудита: фиксирует действия всех пользователей.
-- user_id допускает NULL (ON DELETE SET NULL), чтобы история
-- сохранялась даже после удаления учётной записи.
-- ============================================================

CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,

    user_id INTEGER,

    action VARCHAR(50) NOT NULL,

    entity_type VARCHAR(30),

    entity_id INTEGER,

    details TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- ============================================================
=======
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
-- Индексы
-- ============================================================

CREATE INDEX idx_clients_user_id
    ON clients(user_id);

CREATE INDEX idx_orders_client_id
    ON orders(client_id);

CREATE INDEX idx_orders_status
    ON orders(status);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);

<<<<<<< HEAD
CREATE INDEX idx_orders_is_archived
    ON orders(is_archived);

=======
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);

CREATE INDEX idx_order_items_service_id
    ON order_items(service_id);

<<<<<<< HEAD
CREATE INDEX idx_order_comments_order_id
    ON order_comments(order_id);

CREATE INDEX idx_audit_log_user_id
    ON audit_log(user_id);

CREATE INDEX idx_audit_log_created_at
    ON audit_log(created_at);

CREATE INDEX idx_audit_log_action
    ON audit_log(action);


-- ============================================================
-- Конец schema.sql
-- ============================================================
=======

-- ============================================================
-- Конец schema.sql
-- ============================================================
>>>>>>> d36279e7400e63d0f6a8d6fc745cd78bb36ce62b
