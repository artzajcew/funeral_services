package ru.memory.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Параметры выборки заказов: поиск, фильтры, сортировка, пагинация.
 * Обязательные проверки (статус, даты, сортировка) выполняются
 * в OrderController перед вызовом сервиса.
 */
public record OrderQuery(
        String search,
        String status,
        Integer clientId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal minCost,
        BigDecimal maxCost,
        String archived,
        String sortBy,
        String sortDir,
        int page,
        int limit) {

    public static final Set<String> SORT_COLUMNS = Set.of(
            "created_at", "id", "status", "total_cost", "client_name");

    public static final Set<String> STATUSES = Set.of(
            "NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED");
}
