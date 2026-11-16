package  models;

public class Service {
    private int id;
    private String name;
    private double price;

    public Service(int id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }

    @Override
    public String toString() {
        return String.format("ID: %d | %-40s | Цена: %.2f руб.", id, name, price);
    }
}