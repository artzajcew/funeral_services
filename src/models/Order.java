package  models;

import java.sql.Timestamp;

public class Order {
    private int id;
    private int clientId;
    private Timestamp createdAt;
    private String status;
    private double totalCost;

    public Order(int id, int clientId, Timestamp createdAt, String status, double totalCost) {
        this.id = id;
        this.clientId = clientId;
        this.createdAt = createdAt;
        this.status = status;
        this.totalCost = totalCost;
    }

    public int getId() { return id; }
    public int getClientId() { return clientId; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public double getTotalCost() { return totalCost; }

    @Override
    public String toString() {
        return String.format("Заказ №%d | Дата: %s | Статус: %-12s | Сумма: %.2f руб.", 
                id, createdAt.toString().substring(0, 16), status, totalCost);
    }
}