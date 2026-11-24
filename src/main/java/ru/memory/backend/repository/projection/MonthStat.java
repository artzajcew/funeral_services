package ru.memory.backend.repository.projection;

import java.math.BigDecimal;

/** Проекция: статистика заказов за месяц. */
public interface MonthStat {

    Integer getYr();

    Integer getMo();

    Long getOrderCount();

    BigDecimal getTotalSum();
}
