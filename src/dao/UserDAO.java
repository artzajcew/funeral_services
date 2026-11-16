package dao;

import db.DatabaseConnector;
import models.User;
import utils.PasswordUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Работа с пользователями: аутентификация, роли, блокировки,
 * управление (для админа). Списки поддерживают поиск, фильтры
 * и пагинацию.
 */
public class UserDAO {

    // ================== Аутентификация ==================

    /** Проверка логина и пароля (PBKDF2 и legacy SHA-256). */
    public User verifyLogin(String login, String password) {
        User user = findByLogin(login);
        if (user == null) {
            // Сравнение с фиктивным хешем, чтобы не раскрывать
            // существование логина по времени ответа.
            PasswordUtils.verify(password,
                    "pbkdf2$120000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            return null;
        }
        if (!PasswordUtils.verify(password, user.getPassword())) {
            return null;
        }
        if (PasswordUtils.needsUpgrade(user.getPassword())) {
            // Перезапись legacy SHA-256 хеша на PBKDF2 после успешного входа.
            updatePasswordHash(user.getId(), PasswordUtils.hashNewPassword(password));
        }
        return user;
    }

    // ================== Поиск ==================

    public User findByLogin(String login) {
        String sql = "SELECT id, login, password, full_name, role, is_blocked "
                + "FROM users WHERE login = ?";
        return queryOne(sql, login);
    }

    public User findById(int id) {
        String sql = "SELECT id, login, password, full_name, role, is_blocked "
                + "FROM users WHERE id = ?";
        return queryOne(sql, id);
    }

    private User queryOne(String sql, Object... params) {
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при поиске пользователя", e);
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("login"),
                rs.getString("password"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getBoolean("is_blocked"));
    }

    // ================== Создание ==================

    public int createUser(String login, String hashedPassword, String fullName, String role) {
        String sql = "INSERT INTO users (login, password, full_name, role) "
                + "VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, fullName);
            stmt.setString(4, role);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при создании пользователя", e);
        }
    }

    /** Регистрация клиента (совместимость с консольным меню). */
    public int register(String login, String password, String fullName) {
        return createUser(login, PasswordUtils.hashNewPassword(password), fullName,
                User.ROLE_CLIENT);
    }

    // ================== Управление (админ) ==================

    public Page<UserView> listUsers(String search, String role, Boolean blocked,
                                    int page, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            // ILIKE по исходному тексту + LIKE по нижнему регистру:
            // корректный поиск в любой локали базы данных.
            String raw = "%" + search.trim() + "%";
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" AND (u.login ILIKE ? OR u.full_name ILIKE ? ")
                    .append(" OR COALESCE(c.full_name, '') ILIKE ? ")
                    .append(" OR COALESCE(c.phone, '') ILIKE ? ")
                    .append(" OR COALESCE(c.email, '') ILIKE ? ")
                    .append(" OR LOWER(u.login) LIKE ? OR LOWER(u.full_name) LIKE ? ")
                    .append(" OR LOWER(COALESCE(c.full_name, '')) LIKE ? ")
                    .append(" OR LOWER(COALESCE(c.phone, '')) LIKE ? ")
                    .append(" OR LOWER(COALESCE(c.email, '')) LIKE ? ")
                    .append(" OR CAST(u.id AS TEXT) = ?) ");
            for (int i = 0; i < 5; i++) params.add(raw);
            for (int i = 0; i < 5; i++) params.add(pattern);
            params.add(search.trim());
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND u.role = ? ");
            params.add(role);
        }
        if (blocked != null) {
            where.append(" AND u.is_blocked = ? ");
            params.add(blocked);
        }

        String baseSql =
                " FROM users u "
                + " LEFT JOIN clients c ON c.user_id = u.id "
                + " LEFT JOIN (SELECT client_id, COUNT(*) AS orders_count FROM orders GROUP BY client_id) oc "
                + "   ON oc.client_id = c.id "
                + where;

        String dataSql = "SELECT u.id, u.login, u.password, u.full_name, u.role, u.is_blocked, "
                + " COALESCE(c.full_name, u.full_name) AS profile_name, c.phone, c.email, "
                + " COALESCE(oc.orders_count, 0) AS orders_count, "
                + " (SELECT MAX(a.created_at) FROM audit_log a WHERE a.user_id = u.id) AS last_action_at"
                + baseSql
                + " ORDER BY u.id LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) " + baseSql;

        List<UserView> items = new ArrayList<>();
        long total = 0;
        int offset = (page - 1) * limit;
        try (Connection conn = DatabaseConnector.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object p : params) stmt.setObject(idx++, p);
                stmt.setInt(idx++, limit);
                stmt.setInt(idx, offset);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(new UserView(
                                rs.getInt("id"),
                                rs.getString("login"),
                                rs.getString("full_name"),
                                rs.getString("role"),
                                rs.getBoolean("is_blocked"),
                                rs.getString("phone"),
                                rs.getString("email"),
                                rs.getLong("orders_count"),
                                rs.getTimestamp("last_action_at")));
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
            throw new DaoException("Ошибка при получении списка пользователей", e);
        }
        return new Page<>(items, total, page, limit);
    }

    public void updateRole(int userId, String role) {
        execUpdate("UPDATE users SET role = ? WHERE id = ?", role, userId,
                "Не удалось изменить роль");
    }

    public void updateFullName(int userId, String fullName) {
        execUpdate("UPDATE users SET full_name = ? WHERE id = ?", fullName, userId,
                "Не удалось изменить имя пользователя");
    }

    public void updatePasswordHash(int userId, String hashedPassword) {
        execUpdate("UPDATE users SET password = ? WHERE id = ?", hashedPassword, userId,
                "Не удалось обновить пароль");
    }

    public void setBlocked(int userId, boolean blocked) {
        execUpdate("UPDATE users SET is_blocked = ? WHERE id = ?", blocked, userId,
                "Не удалось изменить блокировку");
    }

    private void execUpdate(String sql, Object param, int id, String errorPrefix) {
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, param);
            stmt.setInt(2, id);
            if (stmt.executeUpdate() != 1) {
                throw new DaoException(errorPrefix + ": пользователь не найден");
            }
        } catch (SQLException e) {
            throw new DaoException(errorPrefix, e);
        }
    }

    public long countUsers() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("Ошибка при подсчёте пользователей", e);
        }
    }

    /** Вход (совместимость с консольным меню). */
    public User login(String login, String password) {
        return verifyLogin(login, password);
    }

    // ================== Модель представления ==================

    public static class UserView {
        public final int id;
        public final String login;
        public final String fullName;
        public final String role;
        public final boolean blocked;
        public final String phone;
        public final String email;
        public final long ordersCount;
        public final Timestamp lastActionAt;

        public UserView(int id, String login, String fullName, String role, boolean blocked,
                        String phone, String email, long ordersCount, Timestamp lastActionAt) {
            this.id = id;
            this.login = login;
            this.fullName = fullName;
            this.role = role;
            this.blocked = blocked;
            this.phone = phone;
            this.email = email;
            this.ordersCount = ordersCount;
            this.lastActionAt = lastActionAt;
        }
    }
}
