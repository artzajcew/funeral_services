package ru.memory.fx.data;

import ru.memory.model.Models.OrderStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.sql.Timestamp;

public final class JdbcOrderGateway {
    private static final String ORDER_QUERY = """
            SELECT o.id,
                   o.client_id,
                   c.full_name AS client_name,
                   c.phone,
                   c.email,
                   o.created_at,
                   o.status,
                   o.total_cost,
                   COALESCE(
                       string_agg(s.name || CASE WHEN oi.quantity > 1 THEN ' × ' || oi.quantity ELSE '' END,
                                  ', ' ORDER BY oi.id),
                       'Без позиций'
                   ) AS services
            FROM orders o
            JOIN clients c ON c.id = o.client_id
            LEFT JOIN order_items oi ON oi.order_id = o.id
            LEFT JOIN services s ON s.id = oi.service_id
            GROUP BY o.id, o.client_id, c.full_name, c.phone, c.email,
                     o.created_at, o.status, o.total_cost
            ORDER BY o.created_at DESC, o.id DESC
            """;

    private final DatabaseConfig config;

    public JdbcOrderGateway(DatabaseConfig config) {
        this.config = config;
    }

    public DatabaseConfig config() {
        return config;
    }

    public List<OrderRow> loadOrders() throws SQLException {
        List<OrderRow> orders = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(ORDER_QUERY);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                orders.add(new OrderRow(
                        result.getInt("id"),
                        result.getInt("client_id"),
                        result.getString("client_name"),
                        result.getString("phone"),
                        result.getString("email"),
                        result.getTimestamp("created_at").toLocalDateTime(),
                        OrderStatus.valueOf(result.getString("status")),
                        result.getLong("total_cost"),
                        result.getString("services")));
            }
        }
        return orders;
    }

    public List<ClientOption> loadClients() throws SQLException {
        String sql = """
                SELECT c.id, c.user_id, c.full_name, c.phone, c.email, COUNT(o.id) AS order_count
                FROM clients c
                LEFT JOIN orders o ON o.client_id = c.id
                GROUP BY c.id, c.user_id, c.full_name, c.phone, c.email
                ORDER BY c.full_name
                """;
        List<ClientOption> clients = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                clients.add(new ClientOption(
                        result.getInt("id"),
                        result.getInt("user_id"),
                        result.getString("full_name"),
                        result.getString("phone"),
                        result.getString("email"),
                        result.getInt("order_count")));
            }
        }
        return clients;
    }

    public List<ServiceOption> loadServices() throws SQLException {
        String sql = """
                SELECT s.id, s.name, s.price, COUNT(oi.id) AS usage_count
                FROM services s
                LEFT JOIN order_items oi ON oi.service_id = s.id
                GROUP BY s.id, s.name, s.price
                ORDER BY s.name
                """;
        List<ServiceOption> services = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                services.add(new ServiceOption(
                        result.getInt("id"),
                        result.getString("name"),
                        result.getLong("price"),
                        result.getInt("usage_count")));
            }
        }
        return services;
    }

    public List<OrderItemRow> loadOrderItems(int orderId) throws SQLException {
        String sql = "SELECT service_id, quantity, price FROM order_items WHERE order_id = ? ORDER BY id";
        List<OrderItemRow> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new OrderItemRow(
                            result.getInt("service_id"),
                            result.getInt("quantity"),
                            result.getLong("price")));
                }
            }
        }
        return items;
    }

    public PeriodStatistics loadPeriodStatistics(LocalDate from, LocalDate to) throws SQLException {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Дата начала периода должна быть не позже даты окончания.");
        }
        String sql = """
                SELECT COUNT(*) AS order_count,
                       COALESCE(MIN(total_cost), 0) AS min_cost,
                       COALESCE(MAX(total_cost), 0) AS max_cost,
                       COALESCE(SUM(total_cost) FILTER (WHERE status = 'COMPLETED'), 0) AS revenue
                FROM orders
                WHERE created_at >= ? AND created_at < ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            statement.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("База данных не вернула статистику за период.");
                return new PeriodStatistics(
                        result.getLong("order_count"),
                        result.getBigDecimal("min_cost"),
                        result.getBigDecimal("max_cost"),
                        result.getBigDecimal("revenue"));
            }
        }
    }

    public int createOrder(int clientId, List<OrderItemInput> items) throws SQLException {
        return createOrder(null, clientId, items);
    }

    public int createOrderWithClient(NewClientInput client, List<OrderItemInput> items) throws SQLException {
        return createOrder(client, null, items);
    }

    private int createOrder(NewClientInput newClient, Integer existingClientId, List<OrderItemInput> items) throws SQLException {
        validateOrderInput(newClient, existingClientId, items);
        String orderSql = "INSERT INTO orders (client_id, status, total_cost) VALUES (?, 'NEW', ?) RETURNING id";
        String itemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int clientId = newClient == null ? existingClientId : insertClient(connection, newClient);
                long total = 0;
                for (OrderItemInput item : items) {
                    total = Math.addExact(total, Math.multiplyExact(item.price(), item.quantity()));
                }
                int orderId;
                try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                    statement.setInt(1, clientId);
                    statement.setLong(2, total);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("База данных не вернула номер нового заказа.");
                        orderId = result.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
                    for (OrderItemInput item : items) {
                        statement.setInt(1, orderId);
                        statement.setInt(2, item.serviceId());
                        statement.setInt(3, item.quantity());
                        statement.setLong(4, item.price());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                return orderId;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void validateOrderInput(NewClientInput newClient, Integer existingClientId, List<OrderItemInput> items) {
        if ((newClient == null && (existingClientId == null || existingClientId <= 0))
                || (newClient != null && existingClientId != null)) {
            throw new IllegalArgumentException("Клиент заказа указан некорректно.");
        }
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы одну услугу.");
        Set<Integer> serviceIds = new HashSet<>();
        for (OrderItemInput item : items) {
            if (item.serviceId() <= 0 || item.quantity() <= 0 || item.price() <= 0) {
                throw new IllegalArgumentException("Состав заказа заполнен некорректно.");
            }
            if (!serviceIds.add(item.serviceId())) {
                throw new IllegalArgumentException("Одна услуга не может повторяться в заказе.");
            }
        }
    }

    public void updateOrder(int orderId, int clientId, OrderStatus status, List<OrderItemInput> items) throws SQLException {
        updateOrder(orderId, null, clientId, status, items);
    }

    public void updateOrderWithClient(
            int orderId,
            NewClientInput client,
            OrderStatus status,
            List<OrderItemInput> items) throws SQLException {
        updateOrder(orderId, client, null, status, items);
    }

    private void updateOrder(
            int orderId,
            NewClientInput newClient,
            Integer existingClientId,
            OrderStatus status,
            List<OrderItemInput> items) throws SQLException {
        validateOrderInput(newClient, existingClientId, items);
        if (status == null) throw new IllegalArgumentException("Статус заказа не указан.");
        String updateSql = "UPDATE orders SET client_id = ?, status = ?, total_cost = ? WHERE id = ?";
        String deleteItemsSql = "DELETE FROM order_items WHERE order_id = ?";
        String insertItemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int clientId = newClient == null ? existingClientId : insertClient(connection, newClient);
                long total = orderTotal(items);
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setInt(1, clientId);
                    statement.setString(2, status.name());
                    statement.setLong(3, total);
                    statement.setInt(4, orderId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Заказ № " + orderId + " не найден.");
                }
                try (PreparedStatement statement = connection.prepareStatement(deleteItemsSql)) {
                    statement.setInt(1, orderId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(insertItemSql)) {
                    for (OrderItemInput item : items) {
                        statement.setInt(1, orderId);
                        statement.setInt(2, item.serviceId());
                        statement.setInt(3, item.quantity());
                        statement.setLong(4, item.price());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private long orderTotal(List<OrderItemInput> items) {
        long total = 0;
        for (OrderItemInput item : items) {
            total = Math.addExact(total, Math.multiplyExact(item.price(), item.quantity()));
        }
        return total;
    }

    public void updateStatus(int orderId, OrderStatus status) throws SQLException {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, orderId);
            if (statement.executeUpdate() != 1) throw new SQLException("Заказ № " + orderId + " не найден.");
        }
    }

    public void deleteOrder(int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            if (statement.executeUpdate() != 1) throw new SQLException("Заказ № " + orderId + " не найден.");
        }
    }

    public int createClient(String fullName, String phone, String email, String login, String password) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int clientId = insertClient(connection, new NewClientInput(fullName, phone, email, login, password));
                connection.commit();
                return clientId;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int insertClient(Connection connection, NewClientInput client) throws SQLException {
        String userSql = "INSERT INTO users (login, password, full_name) VALUES (?, ?, ?) RETURNING id";
        String clientSql = "INSERT INTO clients (user_id, phone, email, full_name) VALUES (?, ?, ?, ?) RETURNING id";
        int userId;
        try (PreparedStatement statement = connection.prepareStatement(userSql)) {
            statement.setString(1, client.login());
            statement.setString(2, hashPassword(client.password()));
            statement.setString(3, client.fullName());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("База данных не вернула номер пользователя.");
                userId = result.getInt(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(clientSql)) {
            statement.setInt(1, userId);
            statement.setString(2, client.phone());
            if (client.email() == null || client.email().isBlank()) statement.setNull(3, Types.VARCHAR);
            else statement.setString(3, client.email());
            statement.setString(4, client.fullName());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("База данных не вернула номер клиента.");
                return result.getInt(1);
            }
        }
    }

    public void updateClient(int clientId, int userId, String fullName, String phone, String email) throws SQLException {
        String clientSql = "UPDATE clients SET full_name = ?, phone = ?, email = ? WHERE id = ?";
        String userSql = "UPDATE users SET full_name = ? WHERE id = ?";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(clientSql)) {
                    statement.setString(1, fullName);
                    statement.setString(2, phone);
                    if (email == null || email.isBlank()) statement.setNull(3, Types.VARCHAR);
                    else statement.setString(3, email);
                    statement.setInt(4, clientId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Клиент № " + clientId + " не найден.");
                }
                try (PreparedStatement statement = connection.prepareStatement(userSql)) {
                    statement.setString(1, fullName);
                    statement.setInt(2, userId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void deleteClient(int clientId, int userId) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clients WHERE id = ?")) {
                    statement.setInt(1, clientId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Клиент № " + clientId + " не найден.");
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    statement.setInt(1, userId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public int createService(String name, long price) throws SQLException {
        String sql = "INSERT INTO services (name, price) VALUES (?, ?) RETURNING id";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, price);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("База данных не вернула номер услуги.");
                return result.getInt(1);
            }
        }
    }

    public void updateService(int serviceId, String name, long price) throws SQLException {
        String sql = "UPDATE services SET name = ?, price = ? WHERE id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, price);
            statement.setInt(3, serviceId);
            if (statement.executeUpdate() != 1) throw new SQLException("Услуга № " + serviceId + " не найдена.");
        }
    }

    public void deleteService(int serviceId) throws SQLException {
        String sql = "DELETE FROM services WHERE id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, serviceId);
            if (statement.executeUpdate() != 1) throw new SQLException("Услуга № " + serviceId + " не найдена.");
        }
    }

    public boolean hasRequiredSchema() throws SQLException {
        String sql = """
                SELECT COUNT(*) = 5
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('orders', 'order_items', 'clients', 'services', 'users')
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() && result.getBoolean(1);
        }
    }

    private Connection connection() throws SQLException {
        if (!config.isConfigured()) {
            throw new SQLException(
                    "Настройки базы данных не найдены. Создайте database.properties или задайте MEMORY_DB_URL, MEMORY_DB_USER и MEMORY_DB_PASSWORD.",
                    "08001");
        }
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен", error);
        }
    }

    public record OrderRow(
            int id,
            int clientId,
            String clientName,
            String phone,
            String email,
            LocalDateTime createdAt,
            OrderStatus status,
            long total,
            String services) {}

    public record ClientOption(int id, int userId, String fullName, String phone, String email, int orderCount) {
        @Override public String toString() { return fullName; }
    }

    public record ServiceOption(int id, String name, long price, int usageCount) {
        @Override public String toString() { return name; }
    }

    public record OrderItemInput(int serviceId, int quantity, long price) {}
    public record OrderItemRow(int serviceId, int quantity, long price) {}
    public record NewClientInput(String fullName, String phone, String email, String login, String password) {}
    public record PeriodStatistics(long orderCount, BigDecimal minCost, BigDecimal maxCost, BigDecimal revenue) {}
}
