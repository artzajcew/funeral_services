package ru.memory.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {
    }

    public enum Role {
        CLIENT("Клиент"), ADMIN("Администратор");
        private final String label;
        Role(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum OrderStatus {
        NEW("Новый"), IN_PROGRESS("В обработке"),
        COMPLETED("Выполнен"), CANCELLED("Отменён");
        private final String label;
        OrderStatus(String label) { this.label = label; }
        public String label() { return label; }
    }

    public static final class User {
        private final int id;
        private final String login;
        private final String password;
        private String fullName;
        private Role role;

        public User(int id, String login, String password, String fullName, Role role) {
            this.id = id;
            this.login = login;
            this.password = password;
            this.fullName = fullName;
            this.role = role;
        }
        public int id() { return id; }
        public String login() { return login; }
        public String password() { return password; }
        public String fullName() { return fullName; }
        public Role role() { return role; }
        public void setFullName(String value) { fullName = value; }
        public void setRole(Role value) { role = value; }
    }

    public static final class Client {
        private final int id;
        private final int userId;
        private String fullName;
        private String phone;
        private String email;

        public Client(int id, int userId, String fullName, String phone, String email) {
            this.id = id;
            this.userId = userId;
            this.fullName = fullName;
            this.phone = phone;
            this.email = email;
        }
        public int id() { return id; }
        public int userId() { return userId; }
        public String fullName() { return fullName; }
        public String phone() { return phone; }
        public String email() { return email; }
        public void update(String name, String newPhone, String newEmail) {
            fullName = name;
            phone = newPhone;
            email = newEmail;
        }
    }

    public static final class Service {
        private final int id;
        private String name;
        private String category;
        private long price;
        private String description;
        private boolean active = true;

        public Service(int id, String name, String category, long price, String description) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.description = description;
        }
        public int id() { return id; }
        public String name() { return name; }
        public String category() { return category; }
        public long price() { return price; }
        public String description() { return description; }
        public boolean active() { return active; }
        public void update(String n, String c, long p, String d) {
            name = n; category = c; price = p; description = d;
        }
        public void deactivate() { active = false; }
    }

    public static final class OrderItem {
        private final int serviceId;
        private final String serviceName;
        private int quantity;
        private final long price;
        private final Map<String, String> details;

        public OrderItem(int serviceId, String serviceName, int quantity, long price) {
            this(serviceId, serviceName, quantity, price, Map.of());
        }
        public OrderItem(int serviceId, String serviceName, int quantity, long price, Map<String, String> details) {
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.quantity = quantity;
            this.price = price;
            this.details = new LinkedHashMap<>(details);
        }
        public int serviceId() { return serviceId; }
        public String serviceName() { return serviceName; }
        public int quantity() { return quantity; }
        public long price() { return price; }
        public Map<String, String> details() { return Map.copyOf(details); }
        public long subtotal() { return price * quantity; }
        public void setQuantity(int value) { quantity = value; }
        public OrderItem copy() { return new OrderItem(serviceId, serviceName, quantity, price, details); }
    }

    public static final class Order {
        private final int id;
        private final int clientId;
        private final LocalDateTime createdAt;
        private OrderStatus status;
        private List<OrderItem> items;

        public Order(int id, int clientId, LocalDateTime createdAt, OrderStatus status, List<OrderItem> items) {
            this.id = id;
            this.clientId = clientId;
            this.createdAt = createdAt;
            this.status = status;
            setItems(items);
        }
        public int id() { return id; }
        public int clientId() { return clientId; }
        public LocalDateTime createdAt() { return createdAt; }
        public OrderStatus status() { return status; }
        public List<OrderItem> items() { return items.stream().map(OrderItem::copy).toList(); }
        public long totalCost() { return items.stream().mapToLong(OrderItem::subtotal).sum(); }
        public void setStatus(OrderStatus value) { status = value; }
        public void setItems(List<OrderItem> value) {
            items = new ArrayList<>(value.stream().map(OrderItem::copy).toList());
        }
    }

    public record RegistrationData(String login, String password, String fullName, String phone, String email) {}
    public record Statistics(int clients, int orders, long activeOrderSum, Map<OrderStatus, Long> byStatus) {}
}
