-- ============================================================
-- migration_v2.sql
-- Миграция СУЩЕСТВУЮЩЕЙ базы (без потери данных) до версии 2
-- согласно ТЗ:
--   1) роли пользователей (три уровня доступа);
--   2) блокировка учётных записей;
--   3) архивирование заказов;
--   4) комментарии к заказам;
--   5) журнал аудита.
--
-- Скрипт идемпотентный: его можно запускать повторно.
-- Для НОВОЙ базы используйте schema.sql — там всё уже есть.
-- ============================================================

-- ---------- 1. Роли и блокировка пользователей ----------

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'CLIENT';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Ограничение на значения роли (добавляем, только если его нет)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_role
            CHECK (role IN ('ADMIN', 'MANAGER', 'CLIENT'));
    END IF;
END $$;

-- Первый администратор: учётная запись admin становится админом.
UPDATE users SET role = 'ADMIN' WHERE login = 'admin';

-- ---------- 2. Архивирование заказов ----------

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

-- ---------- 3. Комментарии к заказам ----------

CREATE TABLE IF NOT EXISTS order_comments (
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

-- ---------- 4. Журнал аудита ----------

CREATE TABLE IF NOT EXISTS audit_log (
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

-- ---------- 5. Индексы (безопасно при повторном запуске) ----------

CREATE INDEX IF NOT EXISTS idx_orders_is_archived
    ON orders(is_archived);

CREATE INDEX IF NOT EXISTS idx_order_comments_order_id
    ON order_comments(order_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id
    ON audit_log(user_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at
    ON audit_log(created_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_action
    ON audit_log(action);

-- ============================================================
-- Конец migration_v2.sql
-- ============================================================
