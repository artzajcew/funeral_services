package dao;

import db.DatabaseConnector;
import utils.Json;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Аналитика по заказам и пользователям (только для администратора).
 */
public class AnalyticsDAO {

    /** Общие показатели за период: количество заказов, выручка, min/max/среднее. */
    public Map<String, Object> overview(LocalDate from, LocalDate to) {
        String sql = "SELECT "
                + " COUNT(*) AS order_count, "
                + " COALESCE(SUM(total_cost) FILTER (WHERE status = 'COMPLETED'), 0) AS revenue, "
                + " COALESCE(AVG(total_cost), 0) AS avg_cost, "
                + " COALESCE(MIN(total_cost), 0) AS min_cost, "
                + " COALESCE(MAX(total_cost), 0) AS max_cost, "
                + " COUNT(*) FILTER (WHERE is_archived) AS archived_count "
                + "FROM orders WHERE created_at >= ? AND created_at < ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("orderCount", rs.getLong("order_count"));
                result.put("revenue", rs.getDouble("revenue"));
                result.put("avgOrderCost", rs.getDouble("avg_cost"));
                result.put("minOrderCost", rs.getDouble("min_cost"));
                result.put("maxOrderCost", rs.getDouble("max_cost"));
                result.put("archivedCount", rs.getLong("archived_count"));
                return result;
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при расчёте аналитики", e);
        }
    }

    /** Распределение заказов по статусам за период. */
    public Map<String, Long> byStatus(LocalDate from, LocalDate to) {
        String sql = "SELECT status, COUNT(*) AS cnt FROM orders "
                + "WHERE created_at >= ? AND created_at < ? GROUP BY status";
        Map<String, Long> result = new LinkedHashMap<>();
        for (String status : new String[]{"NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED"}) {
            result.put(status, 0L);
        }
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("status"), rs.getLong("cnt"));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при группировке по статусам", e);
        }
        return result;
    }

    /** Динамика по месяцам: количество заказов и сумма. */
    public List<Map<String, Object>> byMonth(LocalDate from, LocalDate to) {
        String sql = "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month, "
                + " COUNT(*) AS order_count, COALESCE(SUM(total_cost), 0) AS total_sum "
                + "FROM orders WHERE created_at >= ? AND created_at < ? "
                + "GROUP BY 1 ORDER BY 1";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(Json.obj(
                            "month", rs.getString("month"),
                            "orderCount", rs.getLong("order_count"),
                            "totalSum", rs.getDouble("total_sum")));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при группировке по месяцам", e);
        }
        return rows;
    }

    /** Топ услуг по количеству и выручке за период. */
    public List<Map<String, Object>> topServices(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT s.name AS service_name, SUM(oi.quantity) AS sold_count, "
                + " COALESCE(SUM(oi.quantity * oi.price), 0) AS revenue "
                + "FROM order_items oi "
                + "JOIN services s ON s.id = oi.service_id "
                + "JOIN orders o ON o.id = oi.order_id "
                + "WHERE o.created_at >= ? AND o.created_at < ? "
                + "GROUP BY s.name ORDER BY revenue DESC, sold_count DESC LIMIT ?";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            stmt.setInt(3, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(Json.obj(
                            "serviceName", rs.getString("service_name"),
                            "soldCount", rs.getLong("sold_count"),
                            "revenue", rs.getDouble("revenue")));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при расчёте топа услуг", e);
        }
        return rows;
    }

    /** Пользователи по ролям: всего и заблокированных. */
    public Map<String, Object> usersByRole() {
        String sql = "SELECT role, COUNT(*) AS cnt, COUNT(*) FILTER (WHERE is_blocked) AS blocked "
                + "FROM users GROUP BY role";
        Map<String, Long> byRole = new LinkedHashMap<>();
        Map<String, Long> blockedByRole = new LinkedHashMap<>();
        for (String role : new String[]{"ADMIN", "MANAGER", "CLIENT"}) {
            byRole.put(role, 0L);
            blockedByRole.put(role, 0L);
        }
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String role = rs.getString("role");
                byRole.put(role, rs.getLong("cnt"));
                blockedByRole.put(role, rs.getLong("blocked"));
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при подсчёте пользователей", e);
        }
        long total = byRole.values().stream().mapToLong(Long::longValue).sum();
        long blockedTotal = blockedByRole.values().stream().mapToLong(Long::longValue).sum();
        return Json.obj(
                "total", total,
                "blockedTotal", blockedTotal,
                "byRole", byRole,
                "blockedByRole", blockedByRole);
    }
}
