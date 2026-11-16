package ui;

import dao.OrderDAO;
import dao.UserDAO;
import models.Order;
import utils.ConsoleHelper;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class AdminMenu {
    private final Scanner scanner = ConsoleHelper.newScanner();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();

    public void showMenu() {
        while (true) {
            System.out.println("\n--- Меню управления ---");
            System.out.println("1. Посмотреть все заказы");
            System.out.println("2. Изменить статус заказа");
            System.out.println("3. Статистика за период");
            System.out.println("4. Сортировка заказов");
            System.out.println("5. Получить заказ по ID");
            System.out.println("6. Изменить заказ");
            System.out.println("7. Удалить заказ");
            System.out.println("8. Выйти");
            System.out.print("Выберите действие: ");

            switch (readInt()) {
                case 1: viewAllOrders(); break;
                case 2: updateOrderStatus(); break;
                case 3: showStatistics(); break;
                case 4: sortOrders(); break;
                case 5: viewOrderById(); break;
                case 6: updateOrder(); break;
                case 7: deleteOrder(); break;
                case 8: return;
                default: System.out.println("Неверный выбор.");
            }
        }
    }

    private void viewAllOrders() { printOrders(orderDAO.getAllOrders()); }

    private void printOrders(List<Order> orders) {
        System.out.println("\n--- Заказы ---");
        if (orders.isEmpty()) System.out.println("Заказов нет.");
        else for (Order order : orders) System.out.println(order);
    }

    private void updateOrderStatus() {
        viewAllOrders();
        System.out.print("ID заказа: ");
        int id = readInt();
        System.out.println("1. NEW\n2. IN_PROGRESS\n3. COMPLETED\n4. CANCELLED");
        System.out.print("Новый статус: ");
        String status;
        switch (readInt()) {
            case 1: status = "NEW"; break;
            case 2: status = "IN_PROGRESS"; break;
            case 3: status = "COMPLETED"; break;
            case 4: status = "CANCELLED"; break;
            default: System.out.println("Неверный статус."); return;
        }
        orderDAO.updateOrderStatus(id, status);
    }

    private void showStatistics() {
        try {
            System.out.print("Дата начала (ГГГГ-ММ-ДД): ");
            LocalDate from = LocalDate.parse(scanner.nextLine().trim());
            System.out.print("Дата окончания (ГГГГ-ММ-ДД): ");
            LocalDate to = LocalDate.parse(scanner.nextLine().trim());
            OrderDAO.OrderStatistics stats = orderDAO.getStatistics(from, to);
            System.out.println("\n--- Статистика ---");
            System.out.println("Всего пользователей: " + userDAO.countUsers());
            System.out.println("Количество заказов за период: " + stats.getOrderCount());
            System.out.printf("Минимальная сумма заказа: %.2f руб.%n", stats.getMinCost());
            System.out.printf("Максимальная сумма заказа: %.2f руб.%n", stats.getMaxCost());
            System.out.printf("Выручка за период (заказы COMPLETED): %.2f руб.%n", stats.getRevenue());
            System.out.println("Примечание: в текущей схеме нет отдельного статуса оплаты; выручка считается по COMPLETED.");
        } catch (DateTimeParseException e) {
            System.out.println("Неверный формат даты. Пример: 2026-10-01");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Ошибка получения статистики: " + e.getMessage());
        }
    }

    private void sortOrders() {
        System.out.println("Поле сортировки: 1-ID, 2-клиент, 3-статус, 4-сумма, 5-дата");
        String field;
        switch (readInt()) {
            case 1: field = "id"; break;
            case 2: field = "client_id"; break;
            case 3: field = "status"; break;
            case 4: field = "total_cost"; break;
            case 5: field = "created_at"; break;
            default: System.out.println("Неверное поле."); return;
        }
        System.out.print("Направление (1 — по возрастанию, 2 — по убыванию): ");
        int d = readInt();
        String direction = d == 1 ? "ASC" : "DESC";
        printOrders(orderDAO.getAllOrdersSorted(field, direction));
    }

    private void viewOrderById() {
        System.out.print("ID заказа: ");
        Order order = orderDAO.getOrderById(readInt());
        if (order == null) System.out.println("Заказ не найден.");
        else System.out.println(order);
    }

    private void updateOrder() {
        System.out.print("ID заказа: ");
        int id = readInt();
        Order existing = orderDAO.getOrderById(id);
        if (existing == null) { System.out.println("Заказ не найден."); return; }
        System.out.print("Новый ID клиента [" + existing.getClientId() + "]: ");
        String clientText = scanner.nextLine().trim();
        int clientId = clientText.isEmpty() ? existing.getClientId() : parseInt(clientText, -1);
        System.out.print("Новый статус [" + existing.getStatus() + "]: ");
        String status = scanner.nextLine().trim();
        if (status.isEmpty()) status = existing.getStatus();
        System.out.print("Новая сумма [" + existing.getTotalCost() + "]: ");
        String costText = scanner.nextLine().trim();
        double cost = existing.getTotalCost();
        try { if (!costText.isEmpty()) cost = Double.parseDouble(costText); }
        catch (NumberFormatException e) { System.out.println("Некорректная сумма."); return; }
        if (clientId <= 0 || cost < 0 || !status.matches("NEW|IN_PROGRESS|COMPLETED|CANCELLED")) {
            System.out.println("Некорректные данные заказа."); return;
        }
        System.out.println(orderDAO.updateOrder(id, clientId, status, cost)
                ? "Заказ обновлён." : "Не удалось обновить заказ.");
    }

    private void deleteOrder() {
        System.out.print("ID заказа для удаления: ");
        int id = readInt();
        System.out.print("Подтвердите удаление (y/n): ");
        if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
            System.out.println(orderDAO.deleteOrder(id) ? "Заказ удалён." : "Заказ не найден или не удалён.");
        }
    }

    private int readInt() {
        try { return Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { return -1; }
    }
    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return fallback; }
    }
}
