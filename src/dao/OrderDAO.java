package dao;

import db.DatabaseConnector;
import models.Order;
import models.OrderItem;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {
    private static final String ORDER_COLUMNS =
            "id, client_id, created_at, status, total_cost";

    // Создание заказа вместе с позициями в одной транзакции.
    public int createOrder(int clientId, List<OrderItem> items, double totalCost) {
        if (clientId <= 0 || items == null || items.isEmpty() || totalCost < 0) {
            System.out.println("Некорректные данные заказа.");
            return -1;
        }

        String orderSql = "INSERT INTO orders (client_id, status, total_cost) VALUES (?, 'NEW', ?) RETURNING id";
        String itemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId;
                try (PreparedStatement orderStmt = conn.prepareStatement(orderSql)) {
                    orderStmt.setInt(1, clientId);
                    orderStmt.setDouble(2, totalCost);
                    try (ResultSet rs = orderStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return -1;
                        }
                        orderId = rs.getInt("id");
                    }
                }

                try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                    for (OrderItem item : items) {
                        itemStmt.setInt(1, orderId);
                        itemStmt.setInt(2, item.getServiceId());
                        itemStmt.setInt(3, item.getQuantity());
                        itemStmt.setDouble(4, item.getPrice());
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
                conn.commit();
                System.out.println("Заказ успешно оформлен! Номер заказа: " + orderId);
                return orderId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при оформлении заказа: " + e.getMessage());
            return -1;
        }
    }

    public List<Order> getOrdersByClientId(int clientId) {
        return queryOrders("SELECT " + ORDER_COLUMNS + " FROM orders WHERE client_id = ? ORDER BY created_at DESC",
                clientId);
    }

    public List<Order> getAllOrders() {
        return getAllOrdersSorted("created_at", "DESC");
    }

    /**
     * Сортировка по разрешённому списку столбцов.
     * Имя столбца нельзя передать параметром PreparedStatement, поэтому оно проверяется по whitelist.
     */
    public List<Order> getAllOrdersSorted(String sortBy, String direction) {
        String column;
        switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "id": column = "id"; break;
            case "client_id": column = "client_id"; break;
            case "status": column = "status"; break;
            case "total_cost": column = "total_cost"; break;
            case "created_at":
            default: column = "created_at";
        }
        String order = "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return queryOrders("SELECT " + ORDER_COLUMNS + " FROM orders ORDER BY " + column + " " + order + ", id DESC");
    }

    public Order getOrderById(int orderId) {
        String sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?";
        List<Order> result = queryOrders(sql, orderId);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Полное обновление полей заказа. Допустимые статусы ограничены схемой БД.
     */
    public boolean updateOrder(int orderId, int clientId, String status, double totalCost) {
        if (orderId <= 0 || clientId <= 0 || status == null || totalCost < 0) return false;
        String sql = "UPDATE orders SET client_id = ?, status = ?, total_cost = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clientId);
            stmt.setString(2, status);
            stmt.setDouble(3, totalCost);
            stmt.setInt(4, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            System.out.println("Ошибка при обновлении заказа: " + e.getMessage());
            return false;
        }
    }

    public boolean updateOrderStatus(int orderId, String newStatus) {
        if (newStatus == null || !newStatus.matches("NEW|IN_PROGRESS|COMPLETED|CANCELLED")) {
            System.out.println("Недопустимый статус заказа.");
            return false;
        }
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderId);
            boolean updated = stmt.executeUpdate() == 1;
            System.out.println(updated
                    ? "Статус заказа №" + orderId + " изменён на " + newStatus
                    : "Заказ с ID " + orderId + " не найден.");
            return updated;
        } catch (SQLException e) {
            System.out.println("Ошибка при обновлении статуса: " + e.getMessage());
            return false;
        }
    }

    // Удаление заказа удаляет его позиции благодаря ON DELETE CASCADE в schema.sql.
    public boolean deleteOrder(int orderId) {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            System.out.println("Ошибка при удалении заказа: " + e.getMessage());
            return false;
        }
    }

    public OrderStatistics getStatistics(LocalDate from, LocalDate to) throws SQLException {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Укажите корректный период: дата начала не позже даты окончания.");
        }
        String sql = "SELECT " +
                "COUNT(*) AS order_count, " +
                "COALESCE(MIN(total_cost), 0) AS min_cost, " +
                "COALESCE(MAX(total_cost), 0) AS max_cost, " +
                "COALESCE(SUM(total_cost) FILTER (WHERE status = 'COMPLETED'), 0) AS revenue " +
                "FROM orders WHERE created_at >= ? AND created_at < ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return new OrderStatistics(
                        rs.getLong("order_count"),
                        rs.getDouble("min_cost"),
                        rs.getDouble("max_cost"),
                        rs.getDouble("revenue"));
            }
        }
    }

    private List<Order> queryOrders(String sql, int... parameters) {
        List<Order> orders = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) stmt.setInt(i + 1, parameters[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) orders.add(mapOrder(rs));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при получении заказов: " + e.getMessage());
        }
        return orders;
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getInt("id"), rs.getInt("client_id"),
                rs.getTimestamp("created_at"), rs.getString("status"),
                rs.getDouble("total_cost"));
    }

    public static class OrderStatistics {
        private final long orderCount;
        private final double minCost;
        private final double maxCost;
        private final double revenue;

        public OrderStatistics(long orderCount, double minCost, double maxCost, double revenue) {
            this.orderCount = orderCount;
            this.minCost = minCost;
            this.maxCost = maxCost;
            this.revenue = revenue;
        }
        public long getOrderCount() { return orderCount; }
        public double getMinCost() { return minCost; }
        public double getMaxCost() { return maxCost; }
        public double getRevenue() { return revenue; }
    }
}
