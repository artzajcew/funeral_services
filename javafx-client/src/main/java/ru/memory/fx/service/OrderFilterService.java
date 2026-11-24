package ru.memory.fx.service;

import ru.memory.fx.data.JdbcOrderGateway.OrderRow;

import java.time.LocalDate;
import java.util.Locale;

/** Pure filtering rules used by the JavaFX presentation layer and unit tests. */
public final class OrderFilterService {
    private OrderFilterService() {}

    public static boolean matches(OrderRow order, String rawQuery, String status, LocalDate from, LocalDate to) {
        String query = normalize(rawQuery);
        boolean matchesQuery = query.isBlank()
                || String.valueOf(order.id()).contains(query)
                || normalize(order.clientName()).contains(query)
                || normalize(order.services()).contains(query)
                || normalize(order.phone()).contains(query);
        boolean matchesStatus = status == null || status.equals("Все статусы") || order.status().label().equals(status);
        LocalDate created = order.createdAt().toLocalDate();
        boolean matchesFrom = from == null || !created.isBefore(from);
        boolean matchesTo = to == null || !created.isAfter(to);
        return matchesQuery && matchesStatus && matchesFrom && matchesTo;
    }

    public static String validatePeriod(LocalDate from, LocalDate to) {
        return from != null && to != null && from.isAfter(to)
                ? "Дата начала должна быть не позже даты окончания."
                : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.forLanguageTag("ru-RU"));
    }
}
