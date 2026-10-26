-- Миграция для существующей БД: роли больше не используются.
ALTER TABLE users DROP COLUMN IF EXISTS role;
