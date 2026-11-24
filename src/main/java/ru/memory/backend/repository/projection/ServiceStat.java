package ru.memory.backend.repository.projection;

import java.math.BigDecimal;

/** Проекция: статистика продаж услуги за период. */
public interface ServiceStat {

    String getServiceName();

    Long getSoldCount();

    BigDecimal getRevenue();
}
