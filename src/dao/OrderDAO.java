package dao;

import db.DatabaseConnector;
import models.Order;
import models.OrderItem;
import utils.ConsoleHelper;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Заказы: CRUD, поиск с фильтрами, сортировка и пагинация,
 * архивирование, массовые операции, статистика.
 */
public class OrderDAO {
    private static final String ORDER_COLUMNS =
            "id, client_id, created_at, status, total_cost";

    // ================== Вспомогательные модели ==================

    /** Позиция для создания/обновления заказа: услуга + количество. */
    public static class ItemInput {
        public final int serviceId;
        public final int quantity;

        public ItemInput(int serviceId, int quantity) {
            this.serviceId = serviceId;
            this.quantity = quantity;
        }
    }

    /** Строка позиции заказа с названием услуги (для детального ответа). */
    public static class ItemRow {
        public final int id;
        public final int serviceId;
        public final String serviceName;
        public final int quantity;
        public final double price;

        public ItemRow(int id, int serviceId, String serviceName, int quantity, double price) {
            this.id = id;
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.quantity = quantity;
            this.price = price;
        }
    }

    /** Параметры выборки заказов (поиск/фильтры/сортировка/пагинация). */
    public static class OrderFilter {
        public Integer clientId;
        public Integer ownerUserId;   // клиент видит только свои заказы
        public String status;
        public LocalDate dateFrom;
        public LocalDate dateTo;
        public Double minCost;
        public Double maxCost;
        public String search;
        public Boolean archived = Boolean.FALSE; // null — показывать всё
        public String sortBy = "created_at";
        public String sortDir = "DESC";
        public int page = 1;
        public int limit = 10;
    }

    // ================== Создание ==================

    /**
     * Создание заказа с валидацией услуг: цены берутся из каталога
     * на сервере, сумма пересчитывается сама (клиент не может
     * подменить цену). Выполняется в одной транзакции.
     */
    public int createOrderValidated(int clientId, List<ItemInput> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Заказ должен содержать хотя бы одну услугу");
        }
        String orderSql = "INSERT INTO orders (client_id, status, total_cost) "
                + "VALUES (?, 'NEW', ?) RETURNING id";
        String itemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double total = 0;
                List<double[]> priced = new ArrayList<>();
                for (ItemInput item : items) {
                    if (item.serviceId <= 0) {
                        throw new IllegalArgumentException("Некорректный ID услуги");
                    }
                    if (item.quantity < 1) {
                        throw new IllegalArgumentException("Количество должно быть не меньше 1");
                    }
                    Double price = servicePrice(conn, item.serviceId);
                    if (price == null) {
                        throw new IllegalArgumentException("Услуга с ID " + item.serviceId + " не найдена");
                    }
                    total += price * item.quantity;
                    priced.add(new double[]{item.serviceId, item.quantity, price});
                }

                int orderId;
                try (PreparedStatement stmt = conn.prepareStatement(orderSql)) {
                    stmt.setInt(1, clientId);
                    stmt.setDouble(2, total);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new DaoException("Не удалось создать заказ");
                        }
                        orderId = rs.getInt("id");
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement(itemSql)) {
                    for (double[] p : priced) {
                        stmt.setInt(1, orderId);
                        stmt.setInt(2, (int) p[0]);
                        stmt.setInt(3, (int) p[1]);
                        stmt.setDouble(4, p[2]);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
                return orderId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при создании заказа", e);
        }
    }

    private Double servicePrice(Connection conn, int serviceId) throws SQLException {
        String sql = "SELECT price FROM services WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serviceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("price") : null;
            }
        }
    }

    /**
     * Создание заказа (совместимость с консольным меню).
     */
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

    // ================== Обновление ==================

    /**
     * Обновление состава позиций: старые позиции удаляются,
     * новые вставляются, сумма пересчитывается по каталогу.
     */
    public double updateOrderItems(int orderId, List<ItemInput> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Заказ должен содержать хотя бы одну услугу");
        }
        String deleteSql = "DELETE FROM order_items WHERE order_id = ?";
        String itemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) VALUES (?, ?, ?, ?)";
        String totalSql = "UPDATE orders SET total_cost = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double total = 0;
                List<double[]> priced = new ArrayList<>();
                for (ItemInput item : items) {
                    if (item.serviceId <= 0) {
                        throw new IllegalArgumentException("Некорректный ID услуги");
                    }
                    if (item.quantity < 1) {
                        throw new IllegalArgumentException("Количество должно быть не меньше 1");
                    }
                    Double price = servicePrice(conn, item.serviceId);
                    if (price == null) {
                        throw new IllegalArgumentException("Услуга с ID " + item.serviceId + " не найдена");
                    }
                    total += price * item.quantity;
                    priced.add(new double[]{item.serviceId, item.quantity, price});
                }
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setInt(1, orderId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(itemSql)) {
                    for (double[] p : priced) {
                        stmt.setInt(1, orderId);
                        stmt.setInt(2, (int) p[0]);
                        stmt.setInt(3, (int) p[1]);
                        stmt.setDouble(4, p[2]);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                try (PreparedStatement stmt = conn.prepareStatement(totalSql)) {
                    stmt.setDouble(1, total);
                    stmt.setInt(2, orderId);
                    stmt.executeUpdate();
                }
                conn.commit();
                return total;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при обновлении состава заказа", e);
        }
    }

    /** Обновление клиента и статуса заказа (менеджер/админ). */
    public boolean updateOrderMeta(int orderId, Integer clientId, String status) {
        StringBuilder sql = new StringBuilder("UPDATE orders SET ");
        List<Object> params = new ArrayList<>();
        if (clientId != null) {
            sql.append("client_id = ?, ");
            params.add(clientId);
        }
        if (status != null) {
            sql.append("status = ?, ");
            params.add(status);
        }
        if (params.isEmpty()) {
            return true;
        }
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE id = ?");
        params.add(orderId);
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("Ошибка при обновлении заказа", e);
        }
    }

    /**
     * Полное обновление полей заказа (совместимость с консольным меню).
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

    // ================== Чтение ==================

    public Order getOrderById(int orderId) {
        String sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?";
        List<Order> result = queryOrders(sql, orderId);
        return result.isEmpty() ? null : result.get(0);
    }

    public Order getOrderDetail(int orderId) {
        String sql = "SELECT o.id, o.client_id, o.created_at, o.status, o.total_cost, "
                + " o.is_archived, o.archived_at, "
                + " c.full_name AS client_name, c.phone AS client_phone, "
                + " (SELECT COUNT(*) FROM order_comments oc WHERE oc.order_id = o.id) AS comments_count "
                + "FROM orders o JOIN clients c ON c.id = o.client_id WHERE o.id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Order order = mapOrderFull(rs);
                    return order;
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении заказа", e);
        }
        return null;
    }

    public List<ItemRow> getOrderItems(int orderId) {
        List<ItemRow> items = new ArrayList<>();
        String sql = "SELECT oi.id, oi.service_id, s.name AS service_name, oi.quantity, oi.price "
                + "FROM order_items oi JOIN services s ON s.id = oi.service_id "
                + "WHERE oi.order_id = ? ORDER BY oi.id";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new ItemRow(
                            rs.getInt("id"),
                            rs.getInt("service_id"),
                            rs.getString("service_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("price")));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении позиций заказа", e);
        }
        return items;
    }

    /**
     * Поиск заказов с фильтрами, сортировкой и пагинацией.
     * Все названия столбцов для сортировки проверяются по whitelist.
     */
    public Page<Order> searchOrders(OrderFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (filter.ownerUserId != null) {
            where.append(" AND c.user_id = ? ");
            params.add(filter.ownerUserId);
        }
        if (filter.clientId != null) {
            where.append(" AND o.client_id = ? ");
            params.add(filter.clientId);
        }
        if (filter.status != null && !filter.status.isBlank()) {
            where.append(" AND o.status = ? ");
            params.add(filter.status);
        }
        if (filter.archived != null) {
            where.append(" AND o.is_archived = ? ");
            params.add(filter.archived);
        }
        if (filter.dateFrom != null) {
            where.append(" AND o.created_at >= ? ");
            params.add(Timestamp.valueOf(filter.dateFrom.atStartOfDay()));
        }
        if (filter.dateTo != null) {
            where.append(" AND o.created_at < ? ");
            params.add(Timestamp.valueOf(filter.dateTo.plusDays(1).atStartOfDay()));
        }
        if (filter.minCost != null) {
            where.append(" AND o.total_cost >= ? ");
            params.add(filter.minCost);
        }
        if (filter.maxCost != null) {
            where.append(" AND o.total_cost <= ? ");
            params.add(filter.maxCost);
        }
        if (filter.search != null && !filter.search.isBlank()) {
            // Двойной паттерн: ILIKE по исходному тексту + LIKE по нижнему
            // регистру. Это работает и в локали C, и в UTF-8 локалях.
            String raw = "%" + filter.search.trim() + "%";
            String lower = "%" + filter.search.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" AND (c.full_name ILIKE ? ")
                    .append(" OR c.phone ILIKE ? ")
                    .append(" OR COALESCE(c.email, '') ILIKE ? ")
                    .append(" OR o.status ILIKE ? ")
                    .append(" OR LOWER(c.full_name) LIKE ? ")
                    .append(" OR LOWER(c.phone) LIKE ? ")
                    .append(" OR LOWER(COALESCE(c.email, '')) LIKE ? ")
                    .append(" OR LOWER(o.status) LIKE ? ");
            for (int i = 0; i < 4; i++) params.add(raw);
            for (int i = 0; i < 4; i++) params.add(lower);
            String trimmed = filter.search.trim();
            if (trimmed.matches("\\d+")) {
                where.append(" OR o.id = ? ");
                params.add(Integer.parseInt(trimmed));
            }
            where.append(") ");
        }

        String sortColumn = switch (filter.sortBy == null ? "" : filter.sortBy.toLowerCase(Locale.ROOT)) {
            case "id" -> "o.id";
            case "status" -> "o.status";
            case "total_cost", "totalcost" -> "o.total_cost";
            case "client_name", "client" -> "c.full_name";
            default -> "o.created_at";
        };
        String sortDir = "ASC".equalsIgnoreCase(filter.sortDir) ? "ASC" : "DESC";
        int page = Math.max(1, filter.page);
        int limit = Math.max(1, filter.limit);
        int offset = (page - 1) * limit;

        String baseSql = " FROM orders o JOIN clients c ON c.id = o.client_id " + where;
        String dataSql = "SELECT o.id, o.client_id, o.created_at, o.status, o.total_cost, "
                + " o.is_archived, o.archived_at, "
                + " c.full_name AS client_name, c.phone AS client_phone, "
                + " (SELECT COUNT(*) FROM order_comments oc WHERE oc.order_id = o.id) AS comments_count "
                + baseSql
                + " ORDER BY " + sortColumn + " " + sortDir + ", o.id DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) " + baseSql;

        List<Order> orders = new ArrayList<>();
        long total = 0;
        try (Connection conn = DatabaseConnector.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object p : params) stmt.setObject(idx++, p);
                stmt.setInt(idx++, limit);
                stmt.setInt(idx, offset);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        orders.add(mapOrderFull(rs));
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
            throw new DaoException("Ошибка при поиске заказов", e);
        }
        return new Page<>(orders, total, page, limit);
    }

    // ================== Архивирование ==================

    /** Перенос заказа в архив. Возвращает false, если заказ не найден. */
    public boolean archiveOrder(int orderId) {
        String sql = "UPDATE orders SET is_archived = TRUE, archived_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND is_archived = FALSE";
        return execFlag(sql, orderId);
    }

    /** Восстановление заказа из архива. */
    public boolean restoreOrder(int orderId) {
        String sql = "UPDATE orders SET is_archived = FALSE, archived_at = NULL "
                + "WHERE id = ? AND is_archived = TRUE";
        return execFlag(sql, orderId);
    }

    private boolean execFlag(String sql, int orderId) {
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("Ошибка при изменении состояния архива", e);
        }
    }

    public boolean isArchived(int orderId) {
        String sql = "SELECT is_archived FROM orders WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("is_archived");
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при проверке архива", e);
        }
    }

    // ================== Удаление ==================

    /** Удаление заказа (позиции удаляются каскадно по schema.sql). */
    public boolean deleteOrder(int orderId) {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("Ошибка при удалении заказа", e);
        }
    }

    // ================== Списки для консоли (совместимость) ==================

    public List<Order> getOrdersByClientId(int clientId) {
        return queryOrders("SELECT " + ORDER_COLUMNS + " FROM orders "
                + "WHERE client_id = ? AND is_archived = FALSE ORDER BY created_at DESC", clientId);
    }

    public List<Order> getAllOrders() {
        return getAllOrdersSorted("created_at", "DESC");
    }

    /**
     * Сортировка по разрешённому списку столбцов (whitelist).
     * Скрытые в архиве заказы в общем списке не показываются.
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
        return queryOrders("SELECT " + ORDER_COLUMNS + " FROM orders "
                + "WHERE is_archived = FALSE ORDER BY " + column + " " + order + ", id DESC");
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

    // ================== Статистика ==================

    public OrderStatistics getStatistics(LocalDate from, LocalDate to) throws SQLException {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Укажите корректный период: дата начала не позже даты окончания.");
        }
        String sql = "SELECT " +
                "COUNT(*) AS order_count, " +
                "COALESCE(MIN(total_cost), 0) AS min_cost, " +
                "COALESCE(MAX(total_cost), 0) AS max_cost, " +
                "COALESCE(SUM(total_cost) FILTER (WHERE status = 'COMPLETED'), 0) AS revenue " +
                "FROM orders WHERE created_at >= ? AND created_at < ? AND is_archived = FALSE";
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

    private Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getInt("id"), rs.getInt("client_id"),
                rs.getTimestamp("created_at"), rs.getString("status"),
                rs.getDouble("total_cost"));
    }

    private Order mapOrderFull(ResultSet rs) throws SQLException {
        Order order = new Order(
                rs.getInt("id"),
                rs.getInt("client_id"),
                rs.getTimestamp("created_at"),
                rs.getString("status"),
                rs.getDouble("total_cost"),
                rs.getBoolean("is_archived"),
                rs.getTimestamp("archived_at"));
        order.setClientName(rs.getString("client_name"));
        order.setClientPhone(rs.getString("client_phone"));
        order.setCommentsCount(rs.getInt("comments_count"));
        return order;
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
