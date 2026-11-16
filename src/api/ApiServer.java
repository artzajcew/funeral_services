package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.ServiceDAO;
import models.Service;
import models.User;
import utils.Json;
import utils.Json.JsonException;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * REST-сервер (JDK HttpServer, без внешних зависимостей).
 *
 * Все запросы проходят через /api, маршрутизация — по сегментам пути.
 * Аутентификация — JWT в заголовке Authorization: Bearer <token>.
 * Ответы — JSON, ошибки в формате { "error": "..." }.
 */
public class ApiServer {

    private final AuthService auth = new AuthService();
    private final OrderHandler.AuditDAOHolder audit = new OrderHandler.AuditDAOHolder();
    private final OrderHandler orderHandler = new OrderHandler(audit);
    private final UserHandler userHandler = new UserHandler(auth, audit);
    private final AdminHandler adminHandler = new AdminHandler(audit);
    private final ServiceDAO serviceDAO = new ServiceDAO();

    public static void main(String[] args) {
        int port = resolvePort();
        try {
            ApiServer server = new ApiServer();
            HttpServer http = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.registerRoutes(http);
            http.setExecutor(Executors.newFixedThreadPool(16));
            http.start();
            System.out.println("==================================================");
            System.out.println("  REST API бюро ритуальных услуг запущен");
            System.out.println("  Порт: " + port);
            System.out.println("  Базовый URL: http://localhost:" + port + "/api");
            System.out.println("  Документация: docs/API.md");
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("Не удалось запустить сервер: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static int resolvePort() {
        String port = System.getenv("HTTP_PORT");
        if (port == null || port.isBlank()) {
            port = System.getProperty("http.port");
        }
        if (port == null || port.isBlank()) {
            port = System.getenv("PORT");
        }
        try {
            return port == null || port.isBlank() ? 8080 : Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    void registerRoutes(HttpServer http) {
        http.createContext("/api", this::dispatch);
        http.createContext("/", exchange -> {
            sendError(exchange, 404, "Маршрут не найден. API доступен по адресу /api/...");
        });
    }

    // ================== Маршрутизация ==================

    private void dispatch(HttpExchange exchange) {
        String requestId = Long.toHexString(System.nanoTime());
        ApiContext ctx = new ApiContext(exchange, requestId);
        try {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(ctx.method())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            route(ctx);
        } catch (ApiError e) {
            if (e.getStatus() == 403) {
                // Фиксируем отказы в доступе для уже авторизованных пользователей.
                try {
                    User user = auth.authenticate(ctx);
                    audit.log(user.getId(), "ACCESS_DENIED",
                            ctx.segment(0).toUpperCase(), null,
                            ctx.method() + " /api/" + String.join("/", java.util.Arrays.copyOfRange(
                                    segmentsOf(ctx), 0, Math.min(4, ctx.segmentCount())))
                                    + ": " + e.getMessage());
                } catch (ApiError ignored) {
                    // Неавторизованный или заблокированный — отказ уже зафиксирован в login.
                }
            }
            ctx.sendError(e.getStatus(), e.getMessage());
        } catch (JsonException e) {
            ctx.sendError(400, "Некорректный JSON: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Ошибки валидации данных из DAO-слоя и обработчиков.
            ctx.sendError(400, e.getMessage() == null ? "Некорректные данные запроса" : e.getMessage());
        } catch (dao.DaoException e) {
            logError(ctx, e);
            ctx.sendError(500, "Ошибка базы данных: " + e.getMessage());
        } catch (Exception e) {
            logError(ctx, e);
            ctx.sendError(500, "Внутренняя ошибка сервера (запрос " + requestId + ")");
        } finally {
            exchange.close();
        }
    }

    private static String[] segmentsOf(ApiContext ctx) {
        String[] result = new String[ctx.segmentCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ctx.segment(i) == null ? "" : ctx.segment(i);
        }
        return result;
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Authorization, Content-Type");
    }

    private void route(ApiContext ctx) {
        String root = ctx.segment(0);
        if (root == null) {
            ctx.sendJson(200, Json.obj(
                    "service", "REST API бюро ритуальных услуг",
                    "docs", "docs/API.md",
                    "health", "/api/health"));
            return;
        }
        switch (root) {
            case "health" -> ctx.sendJson(200, Json.obj("status", "ok"));
            case "auth" -> routeAuth(ctx);
            case "services" -> routeServices(ctx);
            case "orders" -> routeOrders(ctx);
            case "analytics" -> routeAnalytics(ctx);
            case "audit" -> routeAudit(ctx);
            case "users" -> routeUsers(ctx);
            default -> throw ApiError.notFound("Неизвестный раздел API: /api/" + root);
        }
    }

    // ================== /api/auth ==================

    private void routeAuth(ApiContext ctx) {
        String action = ctx.segment(1);
        if (action == null) {
            throw ApiError.notFound("Укажите действие: register, login или me");
        }
        switch (action) {
            case "register" -> {
                requireMethod(ctx, "POST");
                ctx.sendJson(201, auth.register(ctx.readJsonBody()));
            }
            case "login" -> {
                requireMethod(ctx, "POST");
                ctx.sendJson(200, auth.login(ctx.readJsonBody()));
            }
            case "me" -> {
                requireMethod(ctx, "GET");
                ctx.sendJson(200, me(auth.authenticate(ctx)));
            }
            default -> throw ApiError.notFound("Неизвестное действие: /api/auth/" + action);
        }
    }

    private Map<String, Object> me(User user) {
        Map<String, Object> json = AuthService.userJson(user);
        var profile = new dao.ClientDAO().getClientByUserId(user.getId());
        if (profile != null) {
            json.put("client", AuthService.clientJson(profile));
        }
        return json;
    }

    // ================== /api/services ==================

    private void routeServices(ApiContext ctx) {
        requireMethod(ctx, "GET");
        auth.authenticate(ctx);
        var services = new java.util.ArrayList<Map<String, Object>>();
        for (Service service : serviceDAO.getAllServices()) {
            services.add(Json.obj(
                    "id", service.getId(),
                    "name", service.getName(),
                    "price", service.getPrice()));
        }
        ctx.sendJson(200, Json.obj("data", services, "total", services.size()));
    }

    // ================== /api/orders ==================

    private void routeOrders(ApiContext ctx) {
        String second = ctx.segment(1);
        if (second == null) {
            User user = auth.authenticate(ctx);
            if ("GET".equals(ctx.method())) {
                orderHandler.list(ctx, user);
                return;
            }
            if ("POST".equals(ctx.method())) {
                orderHandler.create(ctx, user);
                return;
            }
            throw methodNotAllowed();
        }

        // Служебные маршруты без ID
        if ("bulk".equals(second) || "export".equals(second) || "import".equals(second)) {
            User admin = auth.authenticate(ctx);
            auth.requireAdmin(admin);
            switch (second) {
                case "bulk" -> {
                    requireMethod(ctx, "POST");
                    orderHandler.bulk(ctx, admin);
                }
                case "export" -> {
                    requireMethod(ctx, "GET");
                    orderHandler.exportCsv(ctx, admin);
                }
                case "import" -> {
                    requireMethod(ctx, "POST");
                    orderHandler.importCsv(ctx, admin);
                }
            }
            return;
        }

        int orderId = parseId(second);
        String third = ctx.segment(2);
        if (third == null) {
            User user = auth.authenticate(ctx);
            switch (ctx.method()) {
                case "GET" -> orderHandler.detail(ctx, user, orderId);
                case "PUT" -> orderHandler.update(ctx, user, orderId);
                case "DELETE" -> orderHandler.delete(ctx, user, orderId);
                default -> throw methodNotAllowed();
            }
            return;
        }

        switch (third) {
            case "archive" -> {
                requireMethod(ctx, "POST");
                User admin = auth.authenticate(ctx);
                auth.requireAdmin(admin);
                orderHandler.archive(ctx, admin, orderId);
            }
            case "restore" -> {
                requireMethod(ctx, "POST");
                User admin = auth.authenticate(ctx);
                auth.requireAdmin(admin);
                orderHandler.restore(ctx, admin, orderId);
            }
            case "comments" -> {
                String fourth = ctx.segment(3);
                User user = auth.authenticate(ctx);
                if (fourth == null) {
                    if ("GET".equals(ctx.method())) {
                        orderHandler.listComments(ctx, user, orderId);
                        return;
                    }
                    if ("POST".equals(ctx.method())) {
                        orderHandler.addComment(ctx, user, orderId);
                        return;
                    }
                    throw methodNotAllowed();
                }
                requireMethod(ctx, "DELETE");
                orderHandler.deleteComment(ctx, user, orderId, parseId(fourth));
            }
            default -> throw ApiError.notFound("Неизвестное действие: /api/orders/" + orderId + "/" + third);
        }
    }

    // ================== /api/analytics ==================

    private void routeAnalytics(ApiContext ctx) {
        requireMethod(ctx, "GET");
        User admin = auth.authenticate(ctx);
        auth.requireAdmin(admin);
        adminHandler.analytics(ctx, admin);
    }

    // ================== /api/audit ==================

    private void routeAudit(ApiContext ctx) {
        User admin = auth.authenticate(ctx);
        auth.requireAdmin(admin);
        String action = ctx.segment(1);
        if (action == null) {
            requireMethod(ctx, "GET");
            adminHandler.audit(ctx, admin);
            return;
        }
        if ("actions".equals(action)) {
            requireMethod(ctx, "GET");
            adminHandler.auditActions(ctx, admin);
            return;
        }
        throw ApiError.notFound("Неизвестное действие: /api/audit/" + action);
    }

    // ================== /api/users ==================

    private void routeUsers(ApiContext ctx) {
        User admin = auth.authenticate(ctx);
        auth.requireAdmin(admin);
        String second = ctx.segment(1);
        if (second == null) {
            requireMethod(ctx, "GET");
            userHandler.list(ctx, admin);
            return;
        }
        int userId = parseId(second);
        String third = ctx.segment(2);
        if (third == null) {
            switch (ctx.method()) {
                case "GET" -> userHandler.detail(ctx, admin, userId);
                case "PUT" -> userHandler.updateProfile(ctx, admin, userId);
                default -> throw methodNotAllowed();
            }
            return;
        }
        switch (third) {
            case "role" -> {
                requireMethod(ctx, "PUT");
                userHandler.changeRole(ctx, admin, userId);
            }
            case "reset-password" -> {
                requireMethod(ctx, "POST");
                userHandler.resetPassword(ctx, admin, userId);
            }
            case "block" -> {
                requireMethod(ctx, "POST");
                userHandler.block(ctx, admin, userId);
            }
            case "unblock" -> {
                requireMethod(ctx, "POST");
                userHandler.unblock(ctx, admin, userId);
            }
            default -> throw ApiError.notFound("Неизвестное действие: /api/users/" + userId + "/" + third);
        }
    }

    // ================== Вспомогательные методы ==================

    private static int parseId(String segment) {
        if (segment == null || !segment.matches("\\d+")) {
            throw ApiError.badRequest("Ожидался числовой ID, получено: " + segment);
        }
        return Integer.parseInt(segment);
    }

    private static boolean requireMethod(ApiContext ctx, String expected) {
        if (!expected.equals(ctx.method())) {
            throw new ApiError(405, "Метод " + ctx.method() + " не поддерживается для этого маршрута. Ожидается " + expected);
        }
        return true;
    }

    private static ApiError methodNotAllowed() {
        return new ApiError(405, "Метод не поддерживается для этого маршрута");
    }

    private void sendError(HttpExchange exchange, int status, String message) {
        try {
            setCorsHeaders(exchange);
            byte[] bytes = Json.write(Json.obj("error", message)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (java.io.IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    private void logError(ApiContext ctx, Exception e) {
        System.err.println("[error] " + ctx.method() + " /api/" + String.join("/",
                segmentsOf(ctx)) + " (запрос " + ctx.requestId() + "): " + e.getMessage());
        e.printStackTrace();
    }
}
