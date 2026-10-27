package ru.memory.fx.service;

import ru.memory.fx.data.JdbcOrderGateway;
import ru.memory.fx.data.JdbcOrderGateway.ClientOption;
import ru.memory.fx.data.JdbcOrderGateway.NewClientInput;
import ru.memory.fx.data.JdbcOrderGateway.OrderItemInput;
import ru.memory.fx.data.JdbcOrderGateway.OrderItemRow;
import ru.memory.fx.data.JdbcOrderGateway.OrderRow;
import ru.memory.fx.data.JdbcOrderGateway.PeriodStatistics;
import ru.memory.fx.data.JdbcOrderGateway.ServiceOption;
import ru.memory.model.Models.OrderStatus;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/** Application service: coordinates order use cases and keeps JDBC out of the controller. */
public final class OrderService {
    private final JdbcOrderGateway gateway;

    public OrderService(JdbcOrderGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    public DataSnapshot loadSnapshot() throws SQLException {
        if (!gateway.hasRequiredSchema()) {
            throw new SQLException("В базе отсутствуют таблицы orders, order_items, clients, services или users.", "42P01");
        }
        return new DataSnapshot(gateway.loadOrders(), gateway.loadClients(), gateway.loadServices());
    }

    public List<OrderItemRow> loadItems(int orderId) throws SQLException {
        return gateway.loadOrderItems(orderId);
    }

    public PeriodStatistics loadPeriodStatistics(LocalDate from, LocalDate to) throws SQLException {
        AnalyticsService.validatePeriod(from, to);
        return gateway.loadPeriodStatistics(from, to);
    }

    public int create(int clientId, List<OrderItemInput> items) throws SQLException {
        validateExistingClient(clientId);
        validateItems(items);
        return gateway.createOrder(clientId, items);
    }

    public int createWithClient(NewClientInput client, List<OrderItemInput> items) throws SQLException {
        Objects.requireNonNull(client, "Новый клиент не указан.");
        validateItems(items);
        return gateway.createOrderWithClient(client, items);
    }

    public void update(int orderId, int clientId, OrderStatus status, List<OrderItemInput> items) throws SQLException {
        validateOrderId(orderId);
        validateExistingClient(clientId);
        Objects.requireNonNull(status, "Статус заказа не указан.");
        validateItems(items);
        gateway.updateOrder(orderId, clientId, status, items);
    }

    public void updateWithClient(
            int orderId, NewClientInput client, OrderStatus status, List<OrderItemInput> items) throws SQLException {
        validateOrderId(orderId);
        Objects.requireNonNull(client, "Новый клиент не указан.");
        Objects.requireNonNull(status, "Статус заказа не указан.");
        validateItems(items);
        gateway.updateOrderWithClient(orderId, client, status, items);
    }

    public void updateStatus(int orderId, OrderStatus status) throws SQLException {
        validateOrderId(orderId);
        Objects.requireNonNull(status, "Статус заказа не указан.");
        gateway.updateStatus(orderId, status);
    }

    public void delete(int orderId) throws SQLException {
        validateOrderId(orderId);
        gateway.deleteOrder(orderId);
    }

    private void validateOrderId(int orderId) {
        if (orderId <= 0) throw new IllegalArgumentException("Заказ указан некорректно.");
    }

    private void validateExistingClient(int clientId) {
        if (clientId <= 0) throw new IllegalArgumentException("Клиент заказа указан некорректно.");
    }

    private void validateItems(List<OrderItemInput> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы одну услугу.");
        Set<Integer> serviceIds = new HashSet<>();
        for (OrderItemInput item : items) {
            if (item == null || item.serviceId() <= 0 || item.quantity() <= 0 || item.price() <= 0) {
                throw new IllegalArgumentException("Состав заказа заполнен некорректно.");
            }
            if (!serviceIds.add(item.serviceId())) {
                throw new IllegalArgumentException("Одна услуга не может повторяться в заказе.");
            }
        }
    }

    public record DataSnapshot(
            List<OrderRow> orders,
            List<ClientOption> clients,
            List<ServiceOption> services) {}
}
