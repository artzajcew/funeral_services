package  dao;

import  db.DatabaseConnector;
import  models.Client;

import java.sql.*;

public class ClientDAO {

    public void createClient(int userId, String phone, String email, String fullName) {
        String sql = "INSERT INTO clients (user_id, phone, email, full_name) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, phone);
            stmt.setString(3, email);
            stmt.setString(4, fullName);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            System.out.println("Ошибка при создании профиля клиента: " + e.getMessage());
        }
    }

    public Client getClientByUserId(int userId) {
        String sql = "SELECT * FROM clients WHERE user_id = ?";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new Client(
                    rs.getInt("id"),
                    rs.getInt("user_id"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("full_name")
                );
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при получении клиента: " + e.getMessage());
        }
        return null;
    }
}