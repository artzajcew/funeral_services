package dao;

import db.DatabaseConnector;
import models.Comment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Комментарии к заказам. Клиент пишет к своим заказам,
 * менеджер и администратор — к любым.
 */
public class CommentDAO {

    public Comment add(int orderId, int authorId, String body) {
        String sql = "INSERT INTO order_comments (order_id, author_id, body) "
                + "VALUES (?, ?, ?) RETURNING id, created_at";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, authorId);
            stmt.setString(3, body);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Comment(rs.getInt("id"), orderId, authorId,
                            body, rs.getTimestamp("created_at"));
                }
            }
            throw new DaoException("Не удалось добавить комментарий");
        } catch (SQLException e) {
            throw new DaoException("Ошибка при добавлении комментария", e);
        }
    }

    public List<Comment> listByOrder(int orderId) {
        List<Comment> comments = new ArrayList<>();
        String sql = "SELECT oc.id, oc.order_id, oc.author_id, oc.body, oc.created_at, "
                + " u.full_name AS author_name, u.role AS author_role "
                + "FROM order_comments oc "
                + "JOIN users u ON u.id = oc.author_id "
                + "WHERE oc.order_id = ? ORDER BY oc.created_at ASC, oc.id ASC";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Comment comment = new Comment(
                            rs.getInt("id"),
                            rs.getInt("order_id"),
                            rs.getInt("author_id"),
                            rs.getString("body"),
                            rs.getTimestamp("created_at"));
                    comment.setAuthorName(rs.getString("author_name"));
                    comment.setAuthorRole(rs.getString("author_role"));
                    comments.add(comment);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при получении комментариев", e);
        }
        return comments;
    }

    public Comment getById(int commentId) {
        String sql = "SELECT oc.id, oc.order_id, oc.author_id, oc.body, oc.created_at, "
                + " u.full_name AS author_name, u.role AS author_role "
                + "FROM order_comments oc JOIN users u ON u.id = oc.author_id "
                + "WHERE oc.id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, commentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Comment comment = new Comment(
                            rs.getInt("id"),
                            rs.getInt("order_id"),
                            rs.getInt("author_id"),
                            rs.getString("body"),
                            rs.getTimestamp("created_at"));
                    comment.setAuthorName(rs.getString("author_name"));
                    comment.setAuthorRole(rs.getString("author_role"));
                    return comment;
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при поиске комментария", e);
        }
        return null;
    }

    /** Удаление комментария. Возвращает false, если не найден. */
    public boolean delete(int commentId) {
        String sql = "DELETE FROM order_comments WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, commentId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DaoException("Ошибка при удалении комментария", e);
        }
    }

    public long countByOrder(int orderId) {
        String sql = "SELECT COUNT(*) FROM order_comments WHERE order_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DaoException("Ошибка при подсчёте комментариев", e);
        }
    }
}
