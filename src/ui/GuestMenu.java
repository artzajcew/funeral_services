package  ui;

import  dao.ClientDAO;
import  dao.ServiceDAO;
import  dao.UserDAO;
import  models.Service;
import  models.User;

import utils.ConsoleHelper;

import java.util.List;
import java.util.Scanner;

public class GuestMenu {
    private Scanner scanner = ConsoleHelper.newScanner();
    private UserDAO userDAO = new UserDAO();
    private ClientDAO clientDAO = new ClientDAO();
    private ServiceDAO serviceDAO = new ServiceDAO();

    public void showMenu() {
        while (true) {
            System.out.println("\n--- Меню Гостя ---");
            System.out.println("1. Авторизация");
            System.out.println("2. Регистрация");
            System.out.println("3. Показать доступные услуги");
            System.out.println("4. Поиск услуги");
            System.out.println("5. Управление заказами и статистика");
            System.out.println("6. Выход");
            System.out.print("Выберите действие: ");

            int choice = readInt();

            switch (choice) {
                case 1:
                    login();
                    break;
                case 2:
                    register();
                    break;
                case 3:
                    showServices(serviceDAO.getAllServices());
                    break;
                case 4:
                    searchService();
                    break;
                case 5:
                    new AdminMenu().showMenu();
                    break;
                case 6:
                    System.out.println("Выход из программы...");
                    return;
                default:
                    System.out.println("Неверный выбор. Попробуйте снова.");
            }
        }
    }

    private void login() {
        System.out.print("Введите логин: ");
        String login = scanner.nextLine();
        System.out.print("Введите пароль: ");
        String password = scanner.nextLine();

        User user = userDAO.login(login, password);
        if (user != null) {
            System.out.println("Успешный вход! Добро пожаловать, " + user.getFullName());
            new ClientMenu(user).showMenu();
        } else {
            System.out.println("Неверный логин или пароль.");
        }
    }

    private void register() {
        System.out.println("\n--- Регистрация ---");
        System.out.print("Придумайте логин: ");
        String login = scanner.nextLine();
        System.out.print("Придумайте пароль: ");
        String password = scanner.nextLine();
        System.out.print("Введите ваше ФИО: ");
        String fullName = scanner.nextLine();
        System.out.print("Введите телефон: ");
        String phone = scanner.nextLine();
        System.out.print("Введите email: ");
        String email = scanner.nextLine();

        int userId = userDAO.register(login, password, fullName);
        if (userId != -1) {
            clientDAO.createClient(userId, phone, email, fullName);
            System.out.println("Регистрация успешна! Теперь вы можете войти.");
        } else {
            System.out.println("Ошибка регистрации. Возможно, такой логин уже занят.");
        }
    }

    private void showServices(List<Service> services) {
        System.out.println("\n--- Доступные услуги ---");
        if (services.isEmpty()) {
            System.out.println("Список услуг пуст.");
        } else {
            for (Service s : services) {
                System.out.println(s);
            }
        }
    }

    private void searchService() {
        System.out.print("Введите ключевое слово для поиска: ");
        String keyword = scanner.nextLine().trim();
        if (keyword.isEmpty()) {
            System.out.println("Ключевое слово не может быть пустым.");
            return;
        }
        showServices(serviceDAO.searchServices(keyword));
    }

    private int readInt() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}