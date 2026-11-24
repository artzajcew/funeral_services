package ru.memory.backend.repository.projection;

/** Проекция: пользователи по ролям (всего и заблокированных). */
public interface RoleStat {

    String getRole();

    Long getCnt();

    Long getBlocked();
}
