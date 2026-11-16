package  models;

public class OrderItem {
    private int serviceId;
    private int quantity;
    private double price;

    public OrderItem(int serviceId, int quantity, double price) {
        this.serviceId = serviceId;
        this.quantity = quantity;
        this.price = price;
    }

    public int getServiceId() { return serviceId; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getTotal() { return quantity * price; }
}