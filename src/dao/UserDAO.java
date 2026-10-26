package  dao;

import  db.DatabaseConnector;
import  models.User;
import utils.PasswordUtils;
import java.sql.*;

public class UserDAO {

    public User login(String login, String password) {
        String hashedPassword = PasswordUtils.hashPassword(password);
        String sql = "SELECT * FROM users WHERE login = ? AND password = ?";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, login);
            stmt.setString(2, hashedPassword);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new User(
                    rs.getInt("id"),
                    rs.getString("login"),
                    rs.getString("password"),
                    rs.getString("full_name")
                );
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при авторизации: " + e.getMessage());
        }
        return null;
    }

    public int register(String login, String password, String fullName) {
        String hashedPassword = PasswordUtils.hashPassword(password);
        String sql = "INSERT INTO users (login, password, full_name) VALUES (?, ?, ?) RETURNING id";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, login);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, fullName);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id"); // Возвращаем ID нового пользователя
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при регистрации: " + e.getMessage());
        }
        return -1;
    }
    public long countUsers() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            System.out.println("Ошибка при подсчёте пользователей: " + e.getMessage());
            return 0;
        }
    }

}