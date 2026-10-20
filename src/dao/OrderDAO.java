package  dao;

import  db.DatabaseConnector;
import  models.Order;
import  models.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    // Создание заказа с позициями (транзакция)
    public void createOrder(int clientId, List<OrderItem> items, double totalCost) {
        String orderSql = "INSERT INTO orders (client_id, status, total_cost) VALUES (?, 'NEW', ?) RETURNING id";
        String itemSql = "INSERT INTO order_items (order_id, service_id, quantity, price) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false); // Начинаем транзакцию
            
            try (PreparedStatement orderStmt = conn.prepareStatement(orderSql);
                 PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                
                orderStmt.setInt(1, clientId);
                orderStmt.setDouble(2, totalCost);
                
                ResultSet rs = orderStmt.executeQuery();
                int orderId = -1;
                if (rs.next()) {
                    orderId = rs.getInt("id");
                }
                
                if (orderId == -1) {
                    conn.rollback();
                    System.out.println("Ошибка при оформлении заказа: не удалось создать заказ.");
                    return;
                }

                for (OrderItem item : items) {
                    itemStmt.setInt(1, orderId);
                    itemStmt.setInt(2, item.getServiceId());
                    itemStmt.setInt(3, item.getQuantity());
                    itemStmt.setDouble(4, item.getPrice());
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();

                conn.commit(); // Фиксируем транзакцию
                System.out.println("Заказ успешно оформлен! Номер заказа: " + orderId);
                
            } catch (SQLException e) {
                conn.rollback(); // Откат при ошибке
                System.out.println("Ошибка при оформлении заказа: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Ошибка подключения к БД: " + e.getMessage());
        }
    }

    public List<Order> getOrdersByClientId(int clientId) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE client_id = ? ORDER BY created_at DESC";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, clientId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                orders.add(new Order(
                    rs.getInt("id"),
                    rs.getInt("client_id"),
                    rs.getTimestamp("created_at"),
                    rs.getString("status"),
                    rs.getDouble("total_cost")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при получении заказов: " + e.getMessage());
        }
        return orders;
    }

    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders ORDER BY created_at DESC";
        
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                orders.add(new Order(
                    rs.getInt("id"),
                    rs.getInt("client_id"),
                    rs.getTimestamp("created_at"),
                    rs.getString("status"),
                    rs.getDouble("total_cost")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при получении заказов: " + e.getMessage());
        }
        return orders;
    }

    public void updateOrderStatus(int orderId, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderId);
            stmt.executeUpdate();
            System.out.println("Статус заказа №" + orderId + " изменен на " + newStatus);
            
        } catch (SQLException e) {
            System.out.println("Ошибка при обновлении статуса: " + e.getMessage());
        }
    }
}