package  ui;

import  dao.ClientDAO;
import  dao.OrderDAO;
import  dao.ServiceDAO;
import  models.Client;
import  models.Order;
import  models.OrderItem;
import  models.Service;
import  models.User;

import utils.ConsoleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ClientMenu {
    private Scanner scanner = ConsoleHelper.newScanner();
    private User currentUser;
    private Client currentClient;
    private ServiceDAO serviceDAO = new ServiceDAO();
    private OrderDAO orderDAO = new OrderDAO();
    private ClientDAO clientDAO = new ClientDAO();

    public ClientMenu(User user) {
        this.currentUser = user;
        // Получаем профиль клиента по ID пользователя
        this.currentClient = clientDAO.getClientByUserId(user.getId());
    }

    public void showMenu() {
        if (currentClient == null) {
            System.out.println("Ошибка: Профиль клиента не найден.");
            return;
        }

        while (true) {
            System.out.println("\n--- Меню Клиента ---");
            System.out.println("1. Мои заказы");
            System.out.println("2. Оформить новый заказ");
            System.out.println("3. Посмотреть услуги");
            System.out.println("4. Выйти из аккаунта");
            System.out.print("Выберите действие: ");

            int choice = readInt();

            switch (choice) {
                case 1:
                    viewMyOrders();
                    break;
                case 2:
                    createOrder();
                    break;
                case 3:
                    showServices();
                    break;
                case 4:
                    System.out.println("Выход из аккаунта...");
                    return;
                default:
                    System.out.println("Неверный выбор.");
            }
        }
    }

    private void viewMyOrders() {
        List<Order> orders = orderDAO.getOrdersByClientId(currentClient.getId());
        System.out.println("\n--- Мои заказы ---");
        if (orders.isEmpty()) {
            System.out.println("У вас пока нет заказов.");
        } else {
            for (Order o : orders) {
                System.out.println(o);
            }
        }
    }

    private void createOrder() {
        System.out.println("\n--- Оформление заказа ---");
        List<OrderItem> cart = new ArrayList<>();
        double totalCost = 0;

        while (true) {
            showServices();
            System.out.print("Введите ID услуги для добавления (или 0 для завершения): ");
            int serviceId = readInt();

            if (serviceId == 0) break;

            Service service = serviceDAO.getServiceById(serviceId);
            if (service == null) {
                System.out.println("Услуга с таким ID не найдена.");
                continue;
            }

            System.out.print("Введите количество: ");
            int quantity = readInt();
            if (quantity < 1) {
                System.out.println("Количество должно быть больше 0.");
                continue;
            }

            cart.add(new OrderItem(service.getId(), quantity, service.getPrice()));
            totalCost += (service.getPrice() * quantity);
            System.out.println("Услуга добавлена в корзину.");
        }

        if (cart.isEmpty()) {
            System.out.println("Заказ отменен (корзина пуста).");
            return;
        }

        System.out.printf("Итоговая стоимость заказа: %.2f руб.%n", totalCost);
        System.out.print("Подтвердить заказ? (y/n): ");
        String confirm = scanner.nextLine();

        if (confirm.equalsIgnoreCase("y")) {
            orderDAO.createOrder(currentClient.getId(), cart, totalCost);
        } else {
            System.out.println("Заказ отменен.");
        }
    }

    private void showServices() {
        List<Service> services = serviceDAO.getAllServices();
        System.out.println("\n--- Доступные услуги ---");
        for (Service s : services) {
            System.out.println(s);
        }
    }

    private int readInt() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}