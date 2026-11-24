package ru.memory.backend.repository.projection;

/** Проекция: счётчик по ключу (статус заказа / роль пользователя). */
public interface StatusStat {

    String getStatus();

    Long getCnt();

    Long getBlocked();
}
