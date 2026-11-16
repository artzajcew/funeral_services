package dao;

import db.DatabaseConnector;
import models.AuditEntry;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Журнал аудита: все действия пользователей системы.
 * Просмотр — только для администратора.
 */
public class AuditDAO {

    /** Запись действия. Безопасна: при ошибке журнал не ломает основной сценарий. */
    public void log(Integer userId, String action, String entityType,
                    Integer entityId, String details) {
        String sql = "INSERT INTO audit_log (user_id, action, entity_type, entity_id, details) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (userId == null) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, userId);
            }
            stmt.setString(2, action);
            stmt.setString(3, entityType);
            if (entityId == null) {
                stmt.setNull(4, Types.INTEGER);
            } else {
                stmt.setInt(4, entityId);
            }
            stmt.setString(5, details);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[audit] Не удалось записать действие " + action
                    + " пользователя " + userId + ": " + e.getMessage());
        }
    }

    /** Выборка журнала с фильтрами и пагинацией (админ). */
    public Page<AuditEntry> search(Integer userId, String action, String entityType,
                                   LocalDate dateFrom, LocalDate dateTo,
                                   int page, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND a.user_id = ? ");
            params.add(userId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = ? ");
            params.add(action);
        }
        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND a.entity_type = ? ");
            params.add(entityType);
        }
        if (dateFrom != null) {
            where.append(" AND a.created_at >= ? ");
            params.add(Timestamp.valueOf(dateFrom.atStartOfDay()));
        }
        if (dateTo != null) {
            where.append(" AND a.created_at < ? ");
            params.add(Timestamp.valueOf(dateTo.plusDays(1).atStartOfDay()));
        }

        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        int offset = (safePage - 1) * safeLimit;

        String baseSql = " FROM audit_log a LEFT JOIN users u ON u.id = a.user_id " + where;
        String dataSql = "SELECT a.id, a.user_id, u.login AS user_login, a.action, "
                + " a.entity_type, a.entity_id, a.details, a.created_at "
                + baseSql
                + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) " + baseSql;

        List<AuditEntry> items = new ArrayList<>();
        long total = 0;
        try (Connection conn = DatabaseConnector.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object p : params) stmt.setObject(idx++, p);
                stmt.setInt(idx++, safeLimit);
                stmt.setInt(idx, offset);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(new AuditEntry(
                                rs.getInt("id"),
                                (Integer) rs.getObject("user_id"),
                                rs.getString("user_login"),
                                rs.getString("action"),
                                rs.getString("entity_type"),
                                (Integer) rs.getObject("entity_id"),
                                rs.getString("details"),
                                rs.getTimestamp("created_at")));
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                int idx = 1;
                for (Object p : params) stmt.setObject(idx++, p);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при чтении журнала аудита", e);
        }
        return new Page<>(items, total, safePage, safeLimit);
    }

    /** Список типов действий (для фильтров в интерфейсе админа). */
    public List<String> distinctActions() {
        List<String> actions = new ArrayList<>();
        String sql = "SELECT DISTINCT action FROM audit_log ORDER BY action";
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении списка действий", e);
        }
        return actions;
    }
}
