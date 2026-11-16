package ru.memory.fx.service;

import ru.memory.fx.data.JdbcOrderGateway;

import java.sql.SQLException;
import java.util.Objects;

/** Application service for client and service-catalog use cases. */
public final class DirectoryService {
    private final JdbcOrderGateway gateway;

    public DirectoryService(JdbcOrderGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    public int createClient(String fullName, String phone, String email, String login, String password)
            throws SQLException {
        requireText(fullName, "ФИО");
        requireText(phone, "Телефон");
        requireText(login, "Логин");
        requireText(password, "Пароль");
        return gateway.createClient(fullName, phone, email, login, password);
    }

    public void updateClient(int clientId, int userId, String fullName, String phone, String email)
            throws SQLException {
        if (clientId <= 0 || userId <= 0) throw new IllegalArgumentException("Клиент указан некорректно.");
        requireText(fullName, "ФИО");
        requireText(phone, "Телефон");
        gateway.updateClient(clientId, userId, fullName, phone, email);
    }

    public void deleteClient(int clientId, int userId) throws SQLException {
        if (clientId <= 0 || userId <= 0) throw new IllegalArgumentException("Клиент указан некорректно.");
        gateway.deleteClient(clientId, userId);
    }

    public int createCatalogService(String name, long price) throws SQLException {
        validateCatalogService(name, price);
        return gateway.createService(name, price);
    }

    public void updateCatalogService(int id, String name, long price) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Услуга указана некорректно.");
        validateCatalogService(name, price);
        gateway.updateService(id, name, price);
    }

    public void deleteCatalogService(int id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Услуга указана некорректно.");
        gateway.deleteService(id);
    }

    private void validateCatalogService(String name, long price) {
        requireText(name, "Название услуги");
        if (price <= 0) throw new IllegalArgumentException("Цена услуги должна быть больше нуля.");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + ": обязательное поле.");
        }
    }
}
