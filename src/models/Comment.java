package models;

import java.sql.Timestamp;

/** Комментарий к заказу. */
public class Comment {
    private int id;
    private int orderId;
    private int authorId;
    private String authorName;
    private String authorRole;
    private String body;
    private Timestamp createdAt;

    public Comment(int id, int orderId, int authorId, String body, Timestamp createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public int getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public String getBody() { return body; }
    public Timestamp getCreatedAt() { return createdAt; }
}
