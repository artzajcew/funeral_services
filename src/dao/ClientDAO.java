package dao;

import db.DatabaseConnector;
import models.Client;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Профили клиентов (привязаны к учётным записям через user_id).
 */
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
            throw new DaoException("Ошибка при создании профиля клиента", e);
        }
    }

    public Client getClientByUserId(int userId) {
        String sql = "SELECT id, user_id, phone, email, full_name FROM clients WHERE user_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapClient(rs);
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении клиента", e);
        }
        return null;
    }

    public Client getClientById(int id) {
        String sql = "SELECT id, user_id, phone, email, full_name FROM clients WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapClient(rs);
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении клиента", e);
        }
        return null;
    }

    public boolean phoneExists(String phone) {
        return existsBy("phone", phone);
    }

    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) return false;
        return existsBy("email", email);
    }

    public boolean phoneExistsOther(int userId, String phone) {
        return existsByOther("phone", phone, userId);
    }

    private boolean existsBy(String column, String value) {
        if ("phone".equals(column)) {
            if (value == null || value.isBlank()) return false;
        }
        String sql = "SELECT 1 FROM clients WHERE " + column + " = ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка проверки данных клиента", e);
        }
    }

    private boolean existsByOther(String column, String value, int userId) {
        String sql = "SELECT 1 FROM clients c JOIN users u ON u.id = c.user_id "
                + "WHERE c." + column + " = ? AND u.id <> ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка проверки данных клиента", e);
        }
    }

    public void updateProfile(int userId, String fullName, String phone, String email) {
        String sql = "UPDATE clients SET full_name = ?, phone = ?, email = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fullName);
            stmt.setString(2, phone);
            stmt.setString(3, email);
            stmt.setInt(4, userId);
            if (stmt.executeUpdate() == 0) {
                throw new DaoException("Профиль клиента не найден");
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при обновлении профиля клиента", e);
        }
    }

    /** Список всех профилей клиентов (для справочных целей). */
    public List<Client> getAllClients() {
        List<Client> clients = new ArrayList<>();
        String sql = "SELECT id, user_id, phone, email, full_name FROM clients ORDER BY id";
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                clients.add(mapClient(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении клиентов", e);
        }
        return clients;
    }

    private Client mapClient(ResultSet rs) throws SQLException {
        return new Client(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("full_name"));
    }
}
