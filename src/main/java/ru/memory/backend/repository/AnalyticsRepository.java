package ru.memory.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.memory.backend.entity.AuditEntry;
import ru.memory.backend.entity.Order;
import ru.memory.backend.entity.Service;
import ru.memory.backend.entity.User;
import ru.memory.backend.repository.projection.MonthStat;
import ru.memory.backend.repository.projection.ServiceStat;
import ru.memory.backend.repository.projection.RoleStat;
import ru.memory.backend.repository.projection.StatusStat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Аналитика по заказам и пользователям (только для администратора).
 * Запросы на JPQL — работают одинаково в PostgreSQL и H2.
 * Выручка — сумма заказов COMPLETED за период.
 */
public interface AnalyticsRepository extends JpaRepository<AuditEntry, Integer> {

    // ================== Обзор за период ==================

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            """)
    long countOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(o.totalCost), 0) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
              AND o.status = 'COMPLETED'
            """)
    BigDecimal revenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(AVG(o.totalCost), 0) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal avgCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(MIN(o.totalCost), 0) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal minCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(MAX(o.totalCost), 0) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal maxCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to AND o.archived = true
            """)
    long archivedCount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ================== По статусам ==================

    @Query("""
            SELECT o.status AS status, COUNT(o) AS cnt
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.status
            """)
    List<StatusStat> byStatusRows(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // ================== По месяцам ==================

    @Query("""
            SELECT FUNCTION('year', o.createdAt) AS yr,
                   FUNCTION('month', o.createdAt) AS mo,
                   COUNT(o) AS orderCount,
                   COALESCE(SUM(o.totalCost), 0) AS totalSum
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY FUNCTION('year', o.createdAt), FUNCTION('month', o.createdAt)
            ORDER BY FUNCTION('year', o.createdAt), FUNCTION('month', o.createdAt)
            """)
    List<MonthStat> byMonthRows(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    // ================== Топ услуг ==================

    @Query("""
            SELECT s.name AS serviceName,
                   SUM(oi.quantity) AS soldCount,
                   COALESCE(SUM(oi.quantity * oi.price), 0) AS revenue
            FROM OrderItem oi
            JOIN oi.service s
            JOIN oi.order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY s.name
            ORDER BY COALESCE(SUM(oi.quantity * oi.price), 0) DESC, SUM(oi.quantity) DESC
            """)
    List<ServiceStat> topServicesRows(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    // ================== Пользователи по ролям ==================

    @Query("""
            SELECT u.role AS role, COUNT(u) AS cnt,
                   COUNT(CASE WHEN u.blocked = true THEN 1 END) AS blocked
            FROM User u
            GROUP BY u.role
            """)
    List<RoleStat> usersByRoleRows();

    // ================== Удобные обёртки ==================

    default java.util.Map<String, Object> overview(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderCount", countOrders(start, end));
        result.put("revenue", nz(revenue(start, end)));
        result.put("avgOrderCost", nz(avgCost(start, end)));
        result.put("minOrderCost", nz(minCost(start, end)));
        result.put("maxOrderCost", nz(maxCost(start, end)));
        result.put("archivedCount", archivedCount(start, end));
        return result;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    default java.util.Map<String, Long> byStatus(LocalDate from, LocalDate to) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (String status : new String[]{"NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED"}) {
            result.put(status, 0L);
        }
        for (StatusStat row : byStatusRows(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            result.put(row.getStatus(), row.getCnt());
        }
        return result;
    }

    default List<java.util.Map<String, Object>> byMonth(LocalDate from, LocalDate to) {
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (MonthStat row : byMonthRows(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            java.util.Map<String, Object> json = new java.util.LinkedHashMap<>();
            json.put("month", String.format("%04d-%02d", row.getYr(), row.getMo()));
            json.put("orderCount", row.getOrderCount());
            json.put("totalSum", row.getTotalSum());
            rows.add(json);
        }
        return rows;
    }

    default List<java.util.Map<String, Object>> topServices(LocalDate from, LocalDate to, int limit) {
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        int added = 0;
        for (ServiceStat row : topServicesRows(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            if (added >= limit) {
                break;
            }
            java.util.Map<String, Object> json = new java.util.LinkedHashMap<>();
            json.put("serviceName", row.getServiceName());
            json.put("soldCount", row.getSoldCount());
            json.put("revenue", row.getRevenue());
            rows.add(json);
            added++;
        }
        return rows;
    }

    default java.util.Map<String, Object> usersByRole() {
        java.util.Map<String, Long> byRole = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> blockedByRole = new java.util.LinkedHashMap<>();
        for (String role : new String[]{"ADMIN", "MANAGER", "CLIENT"}) {
            byRole.put(role, 0L);
            blockedByRole.put(role, 0L);
        }
        for (RoleStat row : usersByRoleRows()) {
            byRole.put(row.getRole(), row.getCnt());
            blockedByRole.put(row.getRole(), row.getBlocked());
        }
        long total = byRole.values().stream().mapToLong(Long::longValue).sum();
        long blockedTotal = blockedByRole.values().stream().mapToLong(Long::longValue).sum();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", total);
        result.put("blockedTotal", blockedTotal);
        result.put("byRole", byRole);
        result.put("blockedByRole", blockedByRole);
        return result;
    }
}
