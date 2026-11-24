-- Выполните этот скрипт, если БД уже была заполнена старым data.sql
-- с паролями в открытом виде (до исправления).

UPDATE users
SET password = '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9'
WHERE login = 'admin';

UPDATE users
SET password = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'
WHERE login IN ('petrov', 'sidorova', 'smirnov', 'volkova');
