package models;

import java.sql.Timestamp;

/**
 * Заказ (основная сущность системы).
 * Дополнительно к полям БД содержит данные, полученные при JOIN:
 * имя клиента и число комментариев.
 */
public class Order {
    private int id;
    private int clientId;
    private Timestamp createdAt;
    private String status;
    private double totalCost;
    private boolean archived;
    private Timestamp archivedAt;

    // Данные из JOIN (заполняются DAO при выборке списков)
    private String clientName;
    private String clientPhone;
    private int commentsCount;

    /** Совместимость с прежним кодом (консольное меню). */
    public Order(int id, int clientId, Timestamp createdAt, String status, double totalCost) {
        this(id, clientId, createdAt, status, totalCost, false, null);
    }

    public Order(int id, int clientId, Timestamp createdAt, String status, double totalCost,
                 boolean archived, Timestamp archivedAt) {
        this.id = id;
        this.clientId = clientId;
        this.createdAt = createdAt;
        this.status = status;
        this.totalCost = totalCost;
        this.archived = archived;
        this.archivedAt = archivedAt;
    }

    public int getId() { return id; }
    public int getClientId() { return clientId; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public double getTotalCost() { return totalCost; }
    public boolean isArchived() { return archived; }
    public Timestamp getArchivedAt() { return archivedAt; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }
    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }

    @Override
    public String toString() {
        return String.format("Заказ №%d | Дата: %s | Статус: %-12s | Сумма: %.2f руб.",
                id, createdAt.toString().substring(0, 16), status, totalCost);
    }
}
