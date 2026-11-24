package ru.memory.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import ru.memory.model.Models.*;

/** Все экраны и переходы консольного фронтенда. */
public final class FuneralConsoleApp {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final NumberFormat MONEY = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));
    private final FrontendDataSource api;
    private final Console ui;
    private User user;
    private boolean running = true;

    public FuneralConsoleApp(FrontendDataSource api) { this(api, new Console()); }
    public FuneralConsoleApp(FrontendDataSource api, Console ui) { this.api = api; this.ui = ui; }

    public void run() {
        try {
            while (running) {
                if (user == null) guestMenu();
                else if (user.role() == Role.ADMIN) adminMenu();
                else clientMenu();
            }
        } catch (NoSuchElementException e) {
            ui.println("\nРабота завершена.");
        }
    }

    private void guestMenu() {
        ui.title("БЮРО РИТУАЛЬНЫХ УСЛУГ «ПАМЯТЬ»", "Гостевой режим");
        ui.info("Деликатная помощь в организации прощания. Демонстрационная версия.");
        int c = ui.menu("Просмотреть услуги", "Поиск услуги", "Фильтрация услуг", "Регистрация", "Вход", "Выход");
        safe(() -> {
            switch (c) {
                case 1 -> showServices(activeServices(), "КАТАЛОГ УСЛУГ");
                case 2 -> searchServices();
                case 3 -> filterServices();
                case 4 -> register();
                case 5 -> login();
                case 6 -> { if (ui.confirm("Завершить работу программы?", true)) running = false; }
                default -> throw new IllegalStateException();
            }
        });
    }

    private void clientMenu() {
        Client client = api.clientForUser(user.id());
        ui.title("ЛИЧНЫЙ КАБИНЕТ", "Клиент: " + client.fullName());
        int c = ui.menu("Просмотреть услуги", "Найти или отфильтровать услугу", "Создать заказ",
                "Мои заказы", "Просмотреть заказ", "Отменить заказ", "Мой профиль", "Выйти из аккаунта");
        safe(() -> {
            switch (c) {
                case 1 -> showServices(activeServices(), "КАТАЛОГ УСЛУГ");
                case 2 -> { int x = ui.menu("Поиск", "Фильтрация", "Назад"); if (x == 1) searchServices(); else if (x == 2) filterServices(); }
                case 3 -> createOrder(client);
                case 4 -> showOrders(api.orders(client.id()), "МОИ ЗАКАЗЫ", true);
                case 5 -> viewOwnOrder(client);
                case 6 -> cancelOrder(client);
                case 7 -> profile(client);
                case 8 -> user = null;
                default -> throw new IllegalStateException();
            }
        });
    }

    private void adminMenu() {
        ui.title("ПАНЕЛЬ АДМИНИСТРАТОРА", "Пользователь: " + user.fullName());
        int c = ui.menu("Пользователи", "Клиенты", "Услуги", "Заказы", "Поиск по системе",
                "Фильтрация и сортировка заказов", "Статистика", "Экспорт данных", "Выйти из аккаунта");
        safe(() -> {
            switch (c) {
                case 1 -> manageUsers();
                case 2 -> manageClients();
                case 3 -> manageServices();
                case 4 -> manageOrders();
                case 5 -> globalSearch();
                case 6 -> filterOrders();
                case 7 -> statistics();
                case 8 -> exportScreen();
                case 9 -> user = null;
                default -> throw new IllegalStateException();
            }
        });
    }

    private void login() {
        ui.title("ВХОД", "Введите данные учётной записи");
        user = api.login(ui.ask("Логин"), ui.secret("Пароль"));
        message("Добро пожаловать, " + user.fullName() + "!", false);
    }

    private void register() {
        ui.title("РЕГИСТРАЦИЯ", "Новая учётная запись клиента");
        String name = ui.ask("ФИО"), phone = ui.ask("Телефон"), email = ui.ask("Email"), login = ui.ask("Логин");
        String password = ui.secret("Пароль (минимум 6 символов)");
        if (!password.equals(ui.secret("Повторите пароль"))) throw new FrontendException("Пароли не совпадают.");
        user = api.register(new RegistrationData(login, password, name, phone, email));
        message("Регистрация завершена. Вы вошли как клиент.", false);
    }

    private void searchServices() {
        String q = ui.ask("Название или ключевое слово");
        showServices(api.services(q, "", null, null, false), "РЕЗУЛЬТАТЫ ПОИСКА: " + q);
    }

    private void filterServices() {
        List<String> categories = api.categories();
        ui.title("ФИЛЬТРАЦИЯ УСЛУГ");
        ui.println("  0. Все категории");
        for (int i = 0; i < categories.size(); i++) ui.println("  " + (i + 1) + ". " + categories.get(i));
        int index = ui.askInt("Категория", 0, categories.size());
        Long min = ui.askOptionalLong("Цена от, ₽ (Enter — без ограничения)", 0);
        Long max = ui.askOptionalLong("Цена до, ₽ (Enter — без ограничения)", 0);
        if (min != null && max != null && min > max) throw new FrontendException("Минимальная цена больше максимальной.");
        showServices(api.services("", index == 0 ? "" : categories.get(index - 1), min, max, false), "УСЛУГИ ПО ФИЛЬТРУ");
    }

    private void createOrder(Client client) {
        List<OrderItem> items = new ArrayList<>();
        String deceased = "";
        while (true) {
            ui.title("НОВЫЙ ЗАКАЗ", "Позиций: " + items.size() + " · Итого: " + money(total(items)));
            itemsTable(items);
            int c = ui.menu("Добавить услугу", "Удалить позицию", "Оформить заказ", "Отменить создание");
            if (c == 1) {
                servicesTable(activeServices());
                Service s = api.service(ui.askInt("ID услуги", 1, Integer.MAX_VALUE));
                int quantity = ui.askInt("Количество", 1, Integer.MAX_VALUE);
                Map<String, String> details = new LinkedHashMap<>();
                if (s.name().contains("Копка")) {
                    details.put("кладбище", ui.ask("Кладбище")); details.put("место", ui.ask("Место/участок"));
                } else if (s.name().contains("Доставка")) {
                    details.put("откуда", ui.ask("Адрес отправления")); details.put("докуда", ui.ask("Адрес назначения"));
                }
                if (deceased.isBlank()) {
                    deceased = ui.ask("ФИО покойного");
                    LocalDate birth = askDate("Дата рождения (ДД.ММ.ГГГГ)");
                    LocalDate death = askDate("Дата смерти (ДД.ММ.ГГГГ)");
                    if (death.isBefore(birth)) throw new FrontendException("Дата смерти не может быть раньше даты рождения.");
                    details.put("дата рождения", birth.format(DATE)); details.put("дата смерти", death.format(DATE));
                }
                details.put("покойный", deceased);
                items.add(new OrderItem(s.id(), s.name(), quantity, s.price(), details));
            } else if (c == 2) {
                requireItems(items); items.remove(ui.askInt("Номер позиции", 1, items.size()) - 1);
            } else if (c == 3) {
                requireItems(items);
                if (ui.confirm("Оформить заказ на сумму " + money(total(items)) + "?", false)) {
                    Order order = api.createOrder(client.id(), items);
                    message("Заказ № " + order.id() + " успешно создан.", false); return;
                }
            } else if (items.isEmpty() || ui.confirm("Отменить создание заказа?", false)) return;
        }
    }

    private void viewOwnOrder(Client client) {
        Order order = api.order(ui.askInt("Номер заказа", 1, Integer.MAX_VALUE));
        if (order.clientId() != client.id()) throw new FrontendException("Этот заказ недоступен.");
        orderDetails(order);
    }

    private void cancelOrder(Client client) {
        showOrders(api.orders(client.id()), "ОТМЕНА ЗАКАЗА", false);
        int id = ui.askInt("Номер заказа", 1, Integer.MAX_VALUE);
        if (ui.confirm("Отменить заказ № " + id + "?", false)) { api.cancelOrder(id, client.id()); message("Заказ отменён.", false); }
    }

    private void profile(Client client) {
        ui.title("МОЙ ПРОФИЛЬ");
        ui.println("ФИО:     " + client.fullName()); ui.println("Телефон: " + client.phone()); ui.println("Email:   " + client.email());
        if (ui.menu("Изменить контактные данные", "Назад") == 1) {
            String name = ui.askDefault("ФИО", client.fullName());
            api.updateProfile(user.id(), name, ui.askDefault("Телефон", client.phone()), ui.askDefault("Email", client.email()));
            user.setFullName(name); message("Профиль обновлён.", false);
        }
    }

    private void manageUsers() {
        boolean open = true;
        while (open) {
            ui.title("УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ");
            switch (ui.menu("Список", "Добавить", "Найти", "Изменить ФИО/роль", "Удалить", "Назад")) {
                case 1 -> pauseAfter(() -> usersTable(api.users("")));
                case 2 -> { User x = api.addUser(ui.ask("Логин"), ui.secret("Пароль"), ui.ask("ФИО"), chooseRole()); message("Пользователь #" + x.id() + " добавлен.", false); }
                case 3 -> pauseAfter(() -> usersTable(api.users(ui.ask("Логин или ФИО"))));
                case 4 -> {
                    usersTable(api.users("")); int id = ui.askInt("ID пользователя", 1, Integer.MAX_VALUE);
                    User x = api.users("").stream().filter(v -> v.id() == id).findFirst().orElseThrow(() -> new FrontendException("Пользователь не найден."));
                    api.updateUser(id, ui.askDefault("ФИО", x.fullName()), chooseRole()); message("Пользователь изменён.", false);
                }
                case 5 -> { usersTable(api.users("")); int id = ui.askInt("ID пользователя", 1, Integer.MAX_VALUE); if (ui.confirm("Удалить пользователя?", false)) api.deleteUser(id); }
                case 6 -> open = false;
                default -> throw new IllegalStateException();
            }
        }
    }

    private void manageClients() {
        boolean open = true;
        while (open) {
            ui.title("УПРАВЛЕНИЕ КЛИЕНТАМИ");
            switch (ui.menu("Список", "Добавить", "Найти", "Изменить", "Удалить", "Назад")) {
                case 1 -> pauseAfter(() -> clientsTable(api.clients("")));
                case 2 -> { Client x = api.addClient(ui.ask("Логин"), ui.secret("Пароль"), ui.ask("ФИО"), ui.ask("Телефон"), ui.ask("Email")); message("Клиент #" + x.id() + " добавлен.", false); }
                case 3 -> pauseAfter(() -> clientsTable(api.clients(ui.ask("ФИО, телефон или email"))));
                case 4 -> {
                    clientsTable(api.clients("")); int id = ui.askInt("ID клиента", 1, Integer.MAX_VALUE);
                    Client x = api.clients("").stream().filter(v -> v.id() == id).findFirst().orElseThrow(() -> new FrontendException("Клиент не найден."));
                    api.updateClient(id, ui.askDefault("ФИО", x.fullName()), ui.askDefault("Телефон", x.phone()), ui.askDefault("Email", x.email()));
                    message("Клиент изменён.", false);
                }
                case 5 -> { clientsTable(api.clients("")); int id = ui.askInt("ID клиента", 1, Integer.MAX_VALUE); if (ui.confirm("Удалить клиента?", false)) api.deleteClient(id); }
                case 6 -> open = false;
                default -> throw new IllegalStateException();
            }
        }
    }

    private void manageServices() {
        boolean open = true;
        while (open) {
            ui.title("УПРАВЛЕНИЕ УСЛУГАМИ");
            switch (ui.menu("Список", "Добавить", "Изменить", "Удалить из каталога", "Назад")) {
                case 1 -> showServices(api.services("", "", null, null, true), "ВСЕ УСЛУГИ");
                case 2 -> { Service x = api.addService(ui.ask("Название"), ui.ask("Категория"), ui.askLong("Стоимость, ₽", 1), ui.askOptional("Описание")); message("Услуга #" + x.id() + " добавлена.", false); }
                case 3 -> {
                    servicesTable(api.services("", "", null, null, true)); Service x = api.service(ui.askInt("ID услуги", 1, Integer.MAX_VALUE));
                    api.updateService(x.id(), ui.askDefault("Название", x.name()), ui.askDefault("Категория", x.category()), ui.askLong("Стоимость, ₽", 1), ui.askDefault("Описание", x.description()));
                    message("Услуга изменена.", false);
                }
                case 4 -> { servicesTable(activeServices()); int id = ui.askInt("ID услуги", 1, Integer.MAX_VALUE); if (ui.confirm("Скрыть услугу?", false)) api.deleteService(id); }
                case 5 -> open = false;
                default -> throw new IllegalStateException();
            }
        }
    }

    private void manageOrders() {
        boolean open = true;
        while (open) {
            ui.title("УПРАВЛЕНИЕ ЗАКАЗАМИ");
            switch (ui.menu("Список", "Просмотреть", "Изменить состав", "Изменить статус", "Удалить", "Назад")) {
                case 1 -> showOrders(api.orders(null), "ВСЕ ЗАКАЗЫ", true);
                case 2 -> orderDetails(api.order(ui.askInt("Номер заказа", 1, Integer.MAX_VALUE)));
                case 3 -> editOrder();
                case 4 -> { int id = ui.askInt("Номер заказа", 1, Integer.MAX_VALUE); api.setOrderStatus(id, chooseStatus()); message("Статус изменён.", false); }
                case 5 -> { int id = ui.askInt("Номер заказа", 1, Integer.MAX_VALUE); if (ui.confirm("Удалить заказ?", false)) api.deleteOrder(id); }
                case 6 -> open = false;
                default -> throw new IllegalStateException();
            }
        }
    }

    private void editOrder() {
        int id = ui.askInt("Номер заказа", 1, Integer.MAX_VALUE);
        Order order = api.order(id);
        if (order.status() == OrderStatus.COMPLETED) throw new FrontendException("Выполненный заказ изменять нельзя.");
        List<OrderItem> items = new ArrayList<>(order.items());
        boolean open = true;
        while (open) {
            ui.title("ИЗМЕНЕНИЕ ЗАКАЗА № " + id, "Новая сумма: " + money(total(items))); itemsTable(items);
            switch (ui.menu("Изменить количество", "Добавить услугу", "Удалить позицию", "Сохранить", "Отмена")) {
                case 1 -> { requireItems(items); int i = ui.askInt("Номер позиции", 1, items.size()) - 1; items.get(i).setQuantity(ui.askInt("Количество", 1, Integer.MAX_VALUE)); }
                case 2 -> { servicesTable(activeServices()); Service s = api.service(ui.askInt("ID услуги", 1, Integer.MAX_VALUE)); items.add(new OrderItem(s.id(), s.name(), ui.askInt("Количество", 1, Integer.MAX_VALUE), s.price())); }
                case 3 -> { requireItems(items); items.remove(ui.askInt("Номер позиции", 1, items.size()) - 1); }
                case 4 -> { api.updateOrderItems(id, items); message("Состав заказа изменён, итог пересчитан.", false); open = false; }
                case 5 -> open = false;
                default -> throw new IllegalStateException();
            }
        }
    }

    private void globalSearch() {
        String q = ui.ask("Поисковый запрос");
        ui.section("Пользователи"); usersTable(api.users(q));
        ui.section("Клиенты"); clientsTable(api.clients(q));
        ui.section("Услуги"); servicesTable(api.services(q, "", null, null, true));
        ui.pause();
    }

    private void filterOrders() {
        ui.println("  0. Любой статус");
        OrderStatus[] statuses = OrderStatus.values();
        for (int i = 0; i < statuses.length; i++) ui.println("  " + (i + 1) + ". " + statuses[i].label());
        int status = ui.askInt("Статус", 0, statuses.length);
        int sort = ui.menu("Сначала новые", "Сначала старые", "По сумме: дороже", "По сумме: дешевле");
        List<Order> rows = new ArrayList<>(api.orders(null));
        if (status > 0) rows.removeIf(o -> o.status() != statuses[status - 1]);
        if (sort == 2) rows.sort(Comparator.comparing(Order::createdAt));
        if (sort == 3) rows.sort(Comparator.comparingLong(Order::totalCost).reversed());
        if (sort == 4) rows.sort(Comparator.comparingLong(Order::totalCost));
        showOrders(rows, "РЕЗУЛЬТАТ", true);
    }

    private void statistics() {
        Statistics s = api.statistics();
        ui.title("СТАТИСТИКА");
        ui.println("Клиентов: " + s.clients()); ui.println("Заказов: " + s.orders()); ui.println("Сумма активных заказов: " + money(s.activeOrderSum()));
        ui.section("Заказы по статусам");
        for (OrderStatus status : OrderStatus.values()) ui.println(String.format("%-18s %d", status.label(), s.byStatus().get(status)));
        ui.pause();
    }

    private void exportScreen() {
        String[] names = {"Пользователи", "Клиенты", "Услуги", "Заказы"};
        String selected = names[ui.menu(names) - 1];
        ui.info("Выбран набор «" + selected + "», формат XLSX.");
        ui.println("Фронтенд сформировал запрос. Файл будет создаваться бэкендом после подключения API.");
        ui.pause();
    }

    private void orderDetails(Order o) {
        ui.title("ЗАКАЗ № " + o.id(), o.createdAt().format(DATE_TIME) + " · " + o.status().label());
        itemsTable(o.items());
        for (OrderItem i : o.items()) if (!i.details().isEmpty()) ui.println("  " + i.serviceName() + ": " + i.details());
        ui.println("\nИТОГО: " + money(o.totalCost())); ui.pause();
    }

    private void showServices(List<Service> values, String title) { ui.title(title); servicesTable(values); ui.pause(); }
    private void servicesTable(List<Service> v) {
        List<String[]> rows = v.stream().map(s -> new String[]{"" + s.id(), s.name(), s.category(), money(s.price()), s.active() ? "Да" : "Нет"}).toList();
        ui.table(new String[]{"ID", "Наименование", "Категория", "Стоимость", "Активна"}, rows, new int[]{4, 34, 19, 14, 7});
    }
    private void usersTable(List<User> v) {
        ui.table(new String[]{"ID", "Логин", "ФИО", "Роль"}, v.stream().map(x -> new String[]{"" + x.id(), x.login(), x.fullName(), x.role().name()}).toList(), new int[]{4, 16, 42, 10});
    }
    private void clientsTable(List<Client> v) {
        ui.table(new String[]{"ID", "ФИО", "Телефон", "Email"}, v.stream().map(x -> new String[]{"" + x.id(), x.fullName(), x.phone(), x.email()}).toList(), new int[]{4, 31, 19, 27});
    }
    private void itemsTable(List<OrderItem> v) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) rows.add(new String[]{"" + (i + 1), v.get(i).serviceName(), "" + v.get(i).quantity(), money(v.get(i).subtotal())});
        ui.table(new String[]{"№", "Услуга", "Кол-во", "Сумма"}, rows, new int[]{3, 48, 8, 18});
    }
    private void showOrders(List<Order> v, String title, boolean pause) {
        ui.title(title); Map<Integer, String> names = new HashMap<>(); api.clients("").forEach(c -> names.put(c.id(), c.fullName()));
        List<String[]> rows = v.stream().map(o -> new String[]{"" + o.id(), o.createdAt().format(DATE), names.getOrDefault(o.clientId(), "#" + o.clientId()), o.status().label(), money(o.totalCost())}).toList();
        ui.table(new String[]{"№", "Дата", "Клиент", "Статус", "Сумма"}, rows, new int[]{6, 10, 27, 16, 15}); if (pause) ui.pause();
    }

    private List<Service> activeServices() { return api.services("", "", null, null, false); }
    private Role chooseRole() { return ui.menu("Клиент", "Администратор") == 1 ? Role.CLIENT : Role.ADMIN; }
    private OrderStatus chooseStatus() {
        OrderStatus[] v = OrderStatus.values(); String[] labels = Arrays.stream(v).map(OrderStatus::label).toArray(String[]::new);
        return v[ui.menu(labels) - 1];
    }
    private LocalDate askDate(String label) {
        while (true) try { return LocalDate.parse(ui.ask(label), DATE); }
        catch (DateTimeParseException e) { ui.error("Используйте формат ДД.ММ.ГГГГ."); }
    }
    private void safe(Runnable action) { try { action.run(); } catch (FrontendException e) { message(e.getMessage(), true); } }
    private void message(String text, boolean error) { if (error) ui.error(text); else ui.success(text); ui.pause(); }
    private void pauseAfter(Runnable action) { action.run(); ui.pause(); }
    private static void requireItems(List<OrderItem> v) { if (v.isEmpty()) throw new FrontendException("В заказе пока нет позиций."); }
    private static long total(List<OrderItem> v) { return v.stream().mapToLong(OrderItem::subtotal).sum(); }
    private static String money(long v) { return MONEY.format(v) + " ₽"; }
}
