package ru.memory.ui;

import java.util.List;

import ru.memory.model.Models.Client;
import ru.memory.model.Models.Order;
import ru.memory.model.Models.OrderItem;
import ru.memory.model.Models.OrderStatus;
import ru.memory.model.Models.RegistrationData;
import ru.memory.model.Models.Role;
import ru.memory.model.Models.Service;
import ru.memory.model.Models.Statistics;
import ru.memory.model.Models.User;

/**
 * Точка подключения консольного интерфейса к уже существующей логике проекта.
 * Здесь нет HTTP, SQL, хранения данных или реализации бизнес-операций.
 * Команда бэкенда реализует этот интерфейс через свои сервисы и БД.
 */
public interface FrontendDataSource {
    User login(String login, String password);
    User register(RegistrationData data);
    List<Service> services(String query, String category, Long minPrice, Long maxPrice, boolean includeInactive);
    List<String> categories();
    Service service(int id);
    Service addService(String name, String category, long price, String description);
    void updateService(int id, String name, String category, long price, String description);
    void deleteService(int id);
    Client clientForUser(int userId);
    List<Client> clients(String query);
    Client addClient(String login, String password, String fullName, String phone, String email);
    void updateClient(int id, String fullName, String phone, String email);
    void deleteClient(int id);
    void updateProfile(int userId, String fullName, String phone, String email);
    List<User> users(String query);
    User addUser(String login, String password, String fullName, Role role);
    void updateUser(int id, String fullName, Role role);
    void deleteUser(int id);
    List<Order> orders(Integer clientId);
    Order order(int id);
    Order createOrder(int clientId, List<OrderItem> items);
    void updateOrderItems(int id, List<OrderItem> items);
    void setOrderStatus(int id, OrderStatus status);
    void cancelOrder(int id, int clientId);
    void deleteOrder(int id);
    Statistics statistics();
}
