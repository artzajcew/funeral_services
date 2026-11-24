package ru.memory.fx.service;

import ru.memory.fx.data.JdbcOrderGateway.OrderRow;
import ru.memory.model.Models.OrderStatus;

import java.time.LocalDate;
import java.util.List;

/** Pure business calculations for dashboard and analytics screens. */
public final class AnalyticsService {
    private AnalyticsService() {}

    public static DashboardStatistics calculate(List<OrderRow> orders) {
        long newCount = count(orders, OrderStatus.NEW);
        long progressCount = count(orders, OrderStatus.IN_PROGRESS);
        long completedCount = count(orders, OrderStatus.COMPLETED);
        long cancelledCount = count(orders, OrderStatus.CANCELLED);
        long activeSum = orders.stream()
                .filter(order -> order.status() == OrderStatus.NEW || order.status() == OrderStatus.IN_PROGRESS)
                .mapToLong(OrderRow::total)
                .sum();
        long portfolioSum = orders.stream().mapToLong(OrderRow::total).sum();
        long average = orders.isEmpty() ? 0 : portfolioSum / orders.size();
        return new DashboardStatistics(
                orders.size(), newCount, progressCount, completedCount, cancelledCount,
                newCount + progressCount, activeSum, portfolioSum, average);
    }

    public static void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Укажите дату начала и дату окончания периода.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Дата начала должна быть не позже даты окончания.");
        }
    }

    private static long count(List<OrderRow> orders, OrderStatus status) {
        return orders.stream().filter(order -> order.status() == status).count();
    }

    public record DashboardStatistics(
            long total,
            long newCount,
            long progressCount,
            long completedCount,
            long cancelledCount,
            long activeCount,
            long activeSum,
            long portfolioSum,
            long average) {
        public double share(long count) {
            return total == 0 ? 0 : (double) count / total;
        }
    }
}
