package  ui;

import  dao.OrderDAO;
import  models.Order;

import utils.ConsoleHelper;

import java.util.List;
import java.util.Scanner;

public class AdminMenu {
    private Scanner scanner = ConsoleHelper.newScanner();
    private OrderDAO orderDAO = new OrderDAO();

    public void showMenu() {
        while (true) {
            System.out.println("\n--- Меню Администратора ---");
            System.out.println("1. Посмотреть все заказы");
            System.out.println("2. Изменить статус заказа");
            System.out.println("3. Выйти из аккаунта");
            System.out.print("Выберите действие: ");

            int choice = readInt();

            switch (choice) {
                case 1:
                    viewAllOrders();
                    break;
                case 2:
                    updateOrderStatus();
                    break;
                case 3:
                    System.out.println("Выход из аккаунта...");
                    return;
                default:
                    System.out.println("Неверный выбор.");
            }
        }
    }

    private void viewAllOrders() {
        List<Order> orders = orderDAO.getAllOrders();
        System.out.println("\n--- Все заказы ---");
        if (orders.isEmpty()) {
            System.out.println("Заказов нет.");
        } else {
            for (Order o : orders) {
                System.out.println(o);
            }
        }
    }

    private void updateOrderStatus() {
        viewAllOrders();
        System.out.print("Введите ID заказа для изменения статуса: ");
        int orderId = readInt();
        
        System.out.println("Выберите новый статус:");
        System.out.println("1. NEW");
        System.out.println("2. IN_PROGRESS");
        System.out.println("3. COMPLETED");
        System.out.println("4. CANCELLED");
        System.out.print("Ваш выбор: ");
        int statusChoice = readInt();

        String newStatus = "";
        switch (statusChoice) {
            case 1: newStatus = "NEW"; break;
            case 2: newStatus = "IN_PROGRESS"; break;
            case 3: newStatus = "COMPLETED"; break;
            case 4: newStatus = "CANCELLED"; break;
            default: 
                System.out.println("Неверный статус."); 
                return;
        }

        orderDAO.updateOrderStatus(orderId, newStatus);
    }

    private int readInt() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}