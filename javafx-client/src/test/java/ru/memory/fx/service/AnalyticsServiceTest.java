package ru.memory.fx.service;

import org.junit.jupiter.api.Test;
import ru.memory.fx.data.JdbcOrderGateway.OrderRow;
import ru.memory.fx.service.AnalyticsService.DashboardStatistics;
import ru.memory.model.Models.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsServiceTest {
    @Test
    void calculatesDashboardValuesFromOrders() {
        List<OrderRow> orders = List.of(
                row(1, OrderStatus.NEW, 10_000),
                row(2, OrderStatus.IN_PROGRESS, 20_000),
                row(3, OrderStatus.COMPLETED, 30_000),
                row(4, OrderStatus.CANCELLED, 40_000));

        DashboardStatistics result = AnalyticsService.calculate(orders);

        assertEquals(4, result.total());
        assertEquals(2, result.activeCount());
        assertEquals(30_000, result.activeSum());
        assertEquals(100_000, result.portfolioSum());
        assertEquals(25_000, result.average());
        assertEquals(0.25, result.share(result.completedCount()));
    }

    private OrderRow row(int id, OrderStatus status, long total) {
        return new OrderRow(id, id, "Клиент", "+70000000000", "", LocalDateTime.now(), status, total, "Услуга");
    }
}
