package ru.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import ru.memory.model.Models.*;
import ru.memory.ui.Console;
import ru.memory.ui.FrontendDataSource;
import ru.memory.ui.FuneralConsoleApp;

/** Тестовая подмена находится только в src/test и не входит в рабочий frontend. */
public final class FrontendUiTest {
    private FrontendUiTest() {}

    @Test
    void rendersGuestCatalog() {
        String input = "1\n\n6\n\n";
        var output = new ByteArrayOutputStream();
        var console = new Console(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8));
        new FuneralConsoleApp(new TestDataSource(), console).run();
        String rendered = output.toString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(rendered.contains("КАТАЛОГ УСЛУГ"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.contains("Копка могилы"));
        org.junit.jupiter.api.Assertions.assertTrue(rendered.contains("Гостевой режим"));
    }

    private static final class TestDataSource implements FrontendDataSource {
        private UnsupportedOperationException unused() {
            return new UnsupportedOperationException("Операция не используется в UI-тесте");
        }
        @Override public List<Service> services(String q, String c, Long min, Long max, boolean inactive) {
            return List.of(new Service(1, "Копка могилы", "Захоронение", 18_000, ""));
        }
        @Override public User login(String l, String p) { throw unused(); }
        @Override public User register(RegistrationData d) { throw unused(); }
        @Override public List<String> categories() { throw unused(); }
        @Override public Service service(int id) { throw unused(); }
        @Override public Service addService(String n, String c, long p, String d) { throw unused(); }
        @Override public void updateService(int id, String n, String c, long p, String d) { throw unused(); }
        @Override public void deleteService(int id) { throw unused(); }
        @Override public Client clientForUser(int id) { throw unused(); }
        @Override public List<Client> clients(String q) { return List.of(); }
        @Override public Client addClient(String l, String p, String n, String phone, String email) { throw unused(); }
        @Override public void updateClient(int id, String n, String p, String e) { throw unused(); }
        @Override public void deleteClient(int id) { throw unused(); }
        @Override public void updateProfile(int id, String n, String p, String e) { throw unused(); }
        @Override public List<User> users(String q) { throw unused(); }
        @Override public User addUser(String l, String p, String n, Role r) { throw unused(); }
        @Override public void updateUser(int id, String n, Role r) { throw unused(); }
        @Override public void deleteUser(int id) { throw unused(); }
        @Override public List<Order> orders(Integer id) { throw unused(); }
        @Override public Order order(int id) { throw unused(); }
        @Override public Order createOrder(int id, List<OrderItem> i) { throw unused(); }
        @Override public void updateOrderItems(int id, List<OrderItem> i) { throw unused(); }
        @Override public void setOrderStatus(int id, OrderStatus s) { throw unused(); }
        @Override public void cancelOrder(int id, int clientId) { throw unused(); }
        @Override public void deleteOrder(int id) { throw unused(); }
        @Override public Statistics statistics() { throw unused(); }
    }
}
