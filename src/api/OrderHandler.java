package api;

import dao.CommentDAO;
import dao.DaoException;
import dao.OrderDAO;
import dao.ClientDAO;
import models.Client;
import models.Comment;
import models.Order;
import models.User;
import utils.Json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Заказы — основная сущность системы:
 * CRUD, поиск/фильтры/сортировка/пагинация, архив (админ),
 * комментарии, массовые операции (админ), CSV-импорт/экспорт (админ).
 */
public class OrderHandler {
    private static final Set<String> STATUSES = Set.of("NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private final OrderDAO orderDAO = new OrderDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final AuditDAOHolder audit;

    OrderHandler(AuditDAOHolder audit) {
        this.audit = audit;
    }

    // ================== Список и поиск ==================

    public void list(ApiContext ctx, User user) {
        OrderDAO.OrderFilter filter = buildFilter(ctx, user);
        boolean clientRole = user.isClient();
        String archivedParam = ctx.query("archived", "false");
        if (clientRole) {
            // Клиент архивные заказы не видит вообще.
            filter.archived = Boolean.FALSE;
        } else {
            switch (archivedParam) {
                case "true" -> filter.archived = Boolean.TRUE;
                case "all" -> filter.archived = null;
                default -> filter.archived = Boolean.FALSE;
            }
        }
        Page(ctx, orderDAO.searchOrders(filter));
    }

    private OrderDAO.OrderFilter buildFilter(ApiContext ctx, User user) {
        OrderDAO.OrderFilter filter = new OrderDAO.OrderFilter();
        if (user.isClient()) {
            filter.ownerUserId = user.getId(); // только свои заказы
        } else {
            filter.clientId = ctx.queryInt("clientId");
        }
        String status = ctx.query("status");
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status.trim())) {
                throw ApiError.badRequest("Недопустимый статус. Допустимо: NEW, IN_PROGRESS, COMPLETED, CANCELLED");
            }
            filter.status = status.trim();
        }
        filter.dateFrom = ctx.queryDate("dateFrom");
        filter.dateTo = ctx.queryDate("dateTo");
        if (filter.dateFrom != null && filter.dateTo != null && filter.dateFrom.isAfter(filter.dateTo)) {
            throw ApiError.badRequest("dateFrom не может быть позже dateTo");
        }
        filter.minCost = ctx.queryDouble("minCost");
        filter.maxCost = ctx.queryDouble("maxCost");
        if (filter.minCost != null && filter.maxCost != null && filter.minCost > filter.maxCost) {
            throw ApiError.badRequest("minCost не может быть больше maxCost");
        }
        filter.search = ctx.query("search");
        filter.sortBy = ctx.query("sortBy", "created_at");
        String dir = ctx.query("sortDir", "DESC");
        if (!dir.equalsIgnoreCase("ASC") && !dir.equalsIgnoreCase("DESC")) {
            throw ApiError.badRequest("sortDir должен быть ASC или DESC");
        }
        filter.sortDir = dir;
        filter.page = ctx.page();
        filter.limit = ctx.limit();
        return filter;
    }

    private void Page(ApiContext ctx, dao.Page<Order> page) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Order order : page.getItems()) {
            items.add(orderJson(order, false));
        }
        ctx.sendJson(200, Json.obj(
                "data", items,
                "page", page.getPage(),
                "limit", page.getLimit(),
                "total", page.getTotal(),
                "totalPages", page.getTotalPages()));
    }

    // ================== Детализация ==================

    public void detail(ApiContext ctx, User user, int orderId) {
        Order order = requireAccessibleOrder(user, orderId, true);
        Map<String, Object> response = orderJson(order, true);
        response.put("items", itemsJson(orderDAO.getOrderItems(orderId)));
        ctx.sendJson(200, response);
    }

    private Map<String, Object> orderJson(Order order, boolean detail) {
        Map<String, Object> json = Json.obj(
                "id", order.getId(),
                "clientId", order.getClientId(),
                "clientName", order.getClientName(),
                "clientPhone", order.getClientPhone(),
                "createdAt", formatTimestamp(order.getCreatedAt()),
                "status", order.getStatus(),
                "totalCost", order.getTotalCost(),
                "archived", order.isArchived(),
                "commentsCount", order.getCommentsCount());
        if (detail) {
            json.put("archivedAt", formatTimestamp(order.getArchivedAt()));
        }
        return json;
    }

    private List<Map<String, Object>> itemsJson(List<OrderDAO.ItemRow> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderDAO.ItemRow item : items) {
            result.add(Json.obj(
                    "id", item.id,
                    "serviceId", item.serviceId,
                    "serviceName", item.serviceName,
                    "quantity", item.quantity,
                    "price", item.price));
        }
        return result;
    }

    // ================== Создание ==================

    public void create(ApiContext ctx, User user) {
        Map<String, Object> body = ctx.readJsonBody();
        List<OrderDAO.ItemInput> items = parseItems(Json.getList(body, "items"));

        int clientId;
        if (user.isClient()) {
            Client profile = clientDAO.getClientByUserId(user.getId());
            if (profile == null) {
                throw ApiError.badRequest("У вашей учётной записи нет профиля клиента");
            }
            clientId = profile.getId();
        } else {
            Integer requested = Json.getInt(body, "clientId");
            if (requested == null) {
                throw ApiError.badRequest("Укажите clientId клиента, для которого создаётся заказ");
            }
            requireClientExists(requested);
            clientId = requested;
        }

        int orderId = orderDAO.createOrderValidated(clientId, items);
        audit.log(user.getId(), "CREATE_ORDER", "ORDER", orderId,
                "Создан заказ №" + orderId + " (клиент " + clientId + ")");
        Order order = orderDAO.getOrderDetail(orderId);
        Map<String, Object> response = orderJson(order, true);
        response.put("items", itemsJson(orderDAO.getOrderItems(orderId)));
        ctx.sendJson(201, response);
    }

    private List<OrderDAO.ItemInput> parseItems(List<Object> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw ApiError.badRequest("Поле 'items' обязательно: список услуг заказа");
        }
        List<OrderDAO.ItemInput> items = new ArrayList<>();
        for (Object raw : rawItems) {
            if (!(raw instanceof Map)) {
                throw ApiError.badRequest("Каждый элемент items должен быть объектом {serviceId, quantity}");
            }
            Map<String, Object> item = (Map<String, Object>) raw;
            Integer serviceId = Json.getInt(item, "serviceId");
            Integer quantity = Json.getInt(item, "quantity");
            if (serviceId == null || quantity == null) {
                throw ApiError.badRequest("В позициях заказа обязательны serviceId и quantity");
            }
            items.add(new OrderDAO.ItemInput(serviceId, quantity));
        }
        return items;
    }

    private void requireClientExists(int clientId) {
        if (clientDAO.getClientById(clientId) == null) {
            throw ApiError.badRequest("Клиент с ID " + clientId + " не найден");
        }
    }

    // ================== Обновление ==================

    public void update(ApiContext ctx, User user, int orderId) {
        Map<String, Object> body = ctx.readJsonBody();
        Order order = requireAccessibleOrder(user, orderId, false);
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ находится в архиве: сначала восстановите его");
        }

        boolean staff = user.isStaff();
        String requestedStatus = Json.getString(body, "status");
        Integer requestedClientId = Json.getInt(body, "clientId");
        List<Object> rawItems = Json.getList(body, "items");

        if (!staff) {
            // Клиент работает только со своим заказом.
            if (requestedClientId != null) {
                throw ApiError.forbidden("Смену клиента заказа выполняет сотрудник бюро");
            }
            if (requestedStatus != null && !"CANCELLED".equals(requestedStatus)) {
                throw ApiError.forbidden("Клиент может только отменить заказ (status=CANCELLED)");
            }
            if (rawItems != null && !"NEW".equals(order.getStatus())) {
                throw ApiError.badRequest("Состав заказа можно менять, только пока он в статусе NEW");
            }
            if ("CANCELLED".equals(requestedStatus) && "COMPLETED".equals(order.getStatus())) {
                throw ApiError.badRequest("Выполненный заказ нельзя отменить");
            }
        }

        if (requestedStatus != null && !STATUSES.contains(requestedStatus)) {
            throw ApiError.badRequest("Недопустимый статус. Допустимо: NEW, IN_PROGRESS, COMPLETED, CANCELLED");
        }
        if (requestedClientId != null) {
            requireClientExists(requestedClientId);
        }

        List<String> changes = new ArrayList<>();
        if (rawItems != null) {
            double newTotal = orderDAO.updateOrderItems(orderId, parseItems(rawItems));
            changes.add("состав заказа (новая сумма " + String.format("%.2f", newTotal) + ")");
        }
        if (requestedClientId != null || requestedStatus != null) {
            orderDAO.updateOrderMeta(orderId, requestedClientId, requestedStatus);
            if (requestedClientId != null) changes.add("clientId -> " + requestedClientId);
            if (requestedStatus != null) changes.add("статус -> " + requestedStatus);
        }
        if (changes.isEmpty()) {
            throw ApiError.badRequest("Нечего обновлять: передайте items, status или clientId");
        }
        audit.log(user.getId(), "UPDATE_ORDER", "ORDER", orderId, "Изменено: " + String.join(", ", changes));

        Order updated = orderDAO.getOrderDetail(orderId);
        Map<String, Object> response = orderJson(updated, true);
        response.put("items", itemsJson(orderDAO.getOrderItems(orderId)));
        ctx.sendJson(200, response);
    }

    // ================== Удаление ==================

    public void delete(ApiContext ctx, User user, int orderId) {
        Order order = requireAccessibleOrder(user, orderId, false);
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ в архиве: сначала восстановите его или оставьте в архиве");
        }
        if (user.isClient() && "COMPLETED".equals(order.getStatus())) {
            throw ApiError.badRequest("Выполненный заказ нельзя удалить");
        }
        orderDAO.deleteOrder(orderId);
        audit.log(user.getId(), "DELETE_ORDER", "ORDER", orderId,
                "Удалён заказ №" + orderId + " (клиент " + order.getClientId() + ")");
        ctx.sendJson(200, Json.obj("message", "Заказ №" + orderId + " удалён", "id", orderId));
    }

    // ================== Архив (только админ) ==================

    public void archive(ApiContext ctx, User user, int orderId) {
        Order order = orderDAO.getOrderDetail(orderId);
        if (order == null) {
            throw ApiError.notFound("Заказ не найден");
        }
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ уже находится в архиве");
        }
        orderDAO.archiveOrder(orderId);
        audit.log(user.getId(), "ARCHIVE_ORDER", "ORDER", orderId, "Заказ №" + orderId + " перенесён в архив");
        ctx.sendJson(200, Json.obj("message", "Заказ №" + orderId + " перенесён в архив", "id", orderId));
    }

    public void restore(ApiContext ctx, User user, int orderId) {
        Order order = orderDAO.getOrderDetail(orderId);
        if (order == null) {
            throw ApiError.notFound("Заказ не найден");
        }
        if (!order.isArchived()) {
            throw ApiError.conflict("Заказ не находится в архиве");
        }
        orderDAO.restoreOrder(orderId);
        audit.log(user.getId(), "RESTORE_ORDER", "ORDER", orderId, "Заказ №" + orderId + " восстановлен из архива");
        ctx.sendJson(200, Json.obj("message", "Заказ №" + orderId + " восстановлен", "id", orderId));
    }

    // ================== Комментарии ==================

    public void listComments(ApiContext ctx, User user, int orderId) {
        requireAccessibleOrder(user, orderId, true);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Comment comment : commentDAO.listByOrder(orderId)) {
            items.add(commentJson(comment));
        }
        ctx.sendJson(200, Json.obj("data", items, "total", items.size()));
    }

    public void addComment(ApiContext ctx, User user, int orderId) {
        requireAccessibleOrder(user, orderId, false);
        if (orderDAO.isArchived(orderId)) {
            throw ApiError.conflict("Заказ в архиве: комментарии недоступны");
        }
        Map<String, Object> body = ctx.readJsonBody();
        String text = Json.getString(body, "text");
        if (text == null || text.trim().isEmpty()) {
            throw ApiError.badRequest("Поле 'text' обязательно");
        }
        text = text.trim();
        if (text.length() > 2000) {
            throw ApiError.badRequest("Комментарий не может быть длиннее 2000 символов");
        }
        Comment comment = commentDAO.add(orderId, user.getId(), text);
        audit.log(user.getId(), "ADD_COMMENT", "ORDER", orderId,
                "Добавлен комментарий к заказу №" + orderId);
        ctx.sendJson(201, commentJson(comment));
    }

    public void deleteComment(ApiContext ctx, User user, int orderId, int commentId) {
        requireAccessibleOrder(user, orderId, true);
        Comment comment = commentDAO.getById(commentId);
        if (comment == null || comment.getOrderId() != orderId) {
            throw ApiError.notFound("Комментарий не найден");
        }
        if (!user.isAdmin() && comment.getAuthorId() != user.getId()) {
            throw ApiError.forbidden("Удалить комментарий может его автор или администратор");
        }
        commentDAO.delete(commentId);
        audit.log(user.getId(), "DELETE_COMMENT", "ORDER", orderId,
                "Удалён комментарий №" + commentId + " к заказу №" + orderId);
        ctx.sendJson(200, Json.obj("message", "Комментарий удалён", "id", commentId));
    }

    private Map<String, Object> commentJson(Comment comment) {
        return Json.obj(
                "id", comment.getId(),
                "orderId", comment.getOrderId(),
                "authorId", comment.getAuthorId(),
                "authorName", comment.getAuthorName(),
                "authorRole", comment.getAuthorRole(),
                "text", comment.getBody(),
                "createdAt", formatTimestamp(comment.getCreatedAt()));
    }

    // ================== Массовые операции (только админ) ==================

    public void bulk(ApiContext ctx, User user) {
        Map<String, Object> body = ctx.readJsonBody();
        List<Object> rawIds = Json.getList(body, "ids");
        String action = Json.getString(body, "action");
        if (rawIds == null || rawIds.isEmpty()) {
            throw ApiError.badRequest("Передайте массив ID записей в поле 'ids'");
        }
        if (action == null) {
            throw ApiError.badRequest("Передайте 'action': archive, restore, delete или setStatus");
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (Object raw : rawIds) {
            if (raw instanceof Number n) {
                ids.add(n.intValue());
            } else if (raw instanceof String s && s.matches("\\d+")) {
                ids.add(Integer.parseInt(s));
            } else {
                throw ApiError.badRequest("Массив 'ids' должен содержать целые числа");
            }
        }
        if (ids.size() > 1000) {
            throw ApiError.badRequest("За одну операцию можно изменить не более 1000 записей");
        }

        String status = null;
        if ("setStatus".equals(action)) {
            status = Json.getString(body, "status");
            if (status == null || !STATUSES.contains(status)) {
                throw ApiError.badRequest("Для setStatus передайте допустимый статус: "
                        + "NEW, IN_PROGRESS, COMPLETED, CANCELLED");
            }
        } else if (!"archive".equals(action) && !"restore".equals(action) && !"delete".equals(action)) {
            throw ApiError.badRequest("Допустимые действия: archive, restore, delete, setStatus");
        }

        List<Integer> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Integer id : ids) {
            try {
                String error = applyBulkAction(action, status, id);
                if (error == null) {
                    success.add(id);
                } else {
                    failed.add(Json.obj("id", id, "error", error));
                }
            } catch (DaoException e) {
                failed.add(Json.obj("id", id, "error", e.getMessage()));
            }
        }
        Map<String, Object> result = Json.obj(
                "action", action,
                "requested", ids.size(),
                "successCount", success.size(),
                "failedCount", failed.size(),
                "success", success,
                "failed", failed);
        audit.log(user.getId(), "BULK_OPERATION", "ORDER", null,
                "Массовая операция '" + action + "' над " + ids.size()
                        + " записями: успешно " + success.size() + ", с ошибками " + failed.size());
        ctx.sendJson(200, result);
    }

    private String applyBulkAction(String action, String status, int id) {
        Order order = orderDAO.getOrderDetail(id);
        if (order == null) {
            return "заказ не найден";
        }
        switch (action) {
            case "archive":
                return orderDAO.archiveOrder(id) ? null : "уже в архиве";
            case "restore":
                return orderDAO.restoreOrder(id) ? null : "не в архиве";
            case "delete":
                return orderDAO.deleteOrder(id) ? null : "не удалось удалить";
            case "setStatus":
                if (order.getStatus().equals(status)) {
                    return "статус уже " + status;
                }
                orderDAO.updateOrderMeta(id, null, status);
                return null;
            default:
                return "неизвестное действие";
        }
    }

    // ================== Доступ к заказу ==================

    /**
     * Возвращает заказ с проверкой прав: клиент — только свои
     * неархивные заказы, сотрудник — любые.
     */
    private Order requireAccessibleOrder(User user, int orderId, boolean allowArchivedForStaff) {
        Order order = orderDAO.getOrderDetail(orderId);
        if (order == null) {
            throw ApiError.notFound("Заказ не найден");
        }
        if (user.isClient()) {
            Client profile = clientDAO.getClientById(order.getClientId());
            if (profile == null || profile.getUserId() != user.getId()) {
                throw ApiError.notFound("Заказ не найден");
            }
            if (order.isArchived()) {
                // Архивные заказы клиенту скрыты.
                throw ApiError.notFound("Заказ не найден");
            }
        }
        return order;
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null
                : timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ================== Общая ссылка на AuditDAO ==================

    /** Небольшая обёртка, чтобы не дублировать зависимость в каждом обработчике. */
    public static class AuditDAOHolder {
        private final dao.AuditDAO auditDAO = new dao.AuditDAO();

        public void log(Integer userId, String action, String entityType,
                        Integer entityId, String details) {
            auditDAO.log(userId, action, entityType, entityId, details);
        }

        public dao.AuditDAO dao() {
            return auditDAO;
        }
    }

    // ================== CSV (только админ) ==================

    public void exportCsv(ApiContext ctx, User user) {
        OrderDAO.OrderFilter filter = buildFilter(ctx, user);
        String archivedParam = ctx.query("archived", "all");
        switch (archivedParam) {
            case "true" -> filter.archived = Boolean.TRUE;
            case "false" -> filter.archived = Boolean.FALSE;
            default -> filter.archived = null;
        }
        filter.page = 1;
        filter.limit = 100;
        filter.sortBy = ctx.query("sortBy", "id");
        filter.sortDir = ctx.query("sortDir", "ASC");

        // Экспорт может быть больше одной страницы — выгружаем порциями.
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF'); // BOM для Excel
        csv.append("id;client_id;client_name;created_at;status;total_cost;archived;comments_count\n");
        long exported = 0;
        while (true) {
            dao.Page<Order> page = orderDAO.searchOrders(filter);
            for (Order order : page.getItems()) {
                csv.append(escapeCsv(String.valueOf(order.getId()))).append(';')
                        .append(escapeCsv(String.valueOf(order.getClientId()))).append(';')
                        .append(escapeCsv(nullSafe(order.getClientName()))).append(';')
                        .append(escapeCsv(formatTimestamp(order.getCreatedAt()))).append(';')
                        .append(escapeCsv(order.getStatus())).append(';')
                        .append(escapeCsv(String.format("%.2f", order.getTotalCost()))).append(';')
                        .append(order.isArchived() ? "да" : "нет").append(';')
                        .append(order.getCommentsCount()).append('\n');
                exported++;
            }
            if (page.getPage() >= page.getTotalPages() || page.getItems().isEmpty()) {
                break;
            }
            filter.page++;
        }
        audit.log(user.getId(), "EXPORT_ORDERS", "ORDER", null, "Экспорт CSV: выгружено " + exported + " заказов");
        ctx.sendCsv(200, "orders_export.csv", csv.toString());
    }

    public void importCsv(ApiContext ctx, User user) {
        String text = ctx.readTextBody();
        if (text == null || text.isBlank()) {
            throw ApiError.badRequest("Ожидается CSV-текст в теле запроса");
        }
        text = text.replace('\uFEFF', ' ');
        String[] lines = text.split("\r\n|\n|\r");
        if (lines.length == 0) {
            throw ApiError.badRequest("CSV-файл пуст");
        }

        // Заголовок и определение разделителя.
        String headerLine = firstNonEmpty(lines);
        if (headerLine == null) {
            throw ApiError.badRequest("CSV-файл пуст");
        }
        char delimiter = headerLine.indexOf(';') >= 0 ? ';' : ',';
        List<String> header = parseCsvLine(headerLine, delimiter);
        Map<String, Integer> col = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase(), i);
        }
        for (String required : new String[]{"client_id", "total_cost"}) {
            if (!col.containsKey(required)) {
                throw ApiError.badRequest("В заголовке CSV нет обязательной колонки '" + required
                        + "'. Ожидаемые колонки: client_id, status, total_cost, created_at");
            }
        }

        int imported = 0;
        int lineNo = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (String line : lines) {
            lineNo++;
            if (line.isBlank() || line.equals(headerLine)) {
                continue;
            }
            try {
                List<String> cells = parseCsvLine(line, delimiter);
                importRow(col, cells);
                imported++;
            } catch (RuntimeException e) {
                errors.add(Json.obj("line", lineNo, "error",
                        e instanceof ApiError ? e.getMessage() : "Ошибка строки: " + e.getMessage()));
            }
        }
        audit.log(user.getId(), "IMPORT_ORDERS", "ORDER", null,
                "Импорт CSV: импортировано " + imported + ", ошибок " + errors.size());
        ctx.sendJson(200, Json.obj(
                "imported", imported,
                "totalLines", lineNo,
                "errorsCount", errors.size(),
                "errors", errors));
    }

    private void importRow(Map<String, Integer> col, List<String> cells) {
        Integer clientId = intOf(cell(col, cells, "client_id"));
        if (clientId == null) {
            throw ApiError.badRequest("Не указан client_id");
        }
        if (clientDAO.getClientById(clientId) == null) {
            throw ApiError.badRequest("Клиент с ID " + clientId + " не существует");
        }
        String status = cell(col, cells, "status");
        if (status == null || status.isBlank()) {
            status = "NEW";
        }
        status = status.trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw ApiError.badRequest("Недопустимый статус '" + status + "'");
        }
        Double total = doubleOf(cell(col, cells, "total_cost"));
        if (total == null || total < 0) {
            throw ApiError.badRequest("Некорректная сумма total_cost");
        }
        LocalDateTime createdAt = parseDateTime(cell(col, cells, "created_at"));

        String sql = "INSERT INTO orders (client_id, status, total_cost"
                + (createdAt == null ? "" : ", created_at") + ") VALUES (?, ?, ?"
                + (createdAt == null ? "" : ", ?") + ")";
        try (var conn = db.DatabaseConnector.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clientId);
            stmt.setString(2, status);
            stmt.setDouble(3, total);
            if (createdAt != null) {
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(createdAt));
            }
            stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new DaoException("Ошибка вставки заказа: " + e.getMessage());
        }
    }

    private String cell(Map<String, Integer> col, List<String> cells, String name) {
        Integer index = col.get(name);
        if (index == null || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    private Integer intOf(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Ожидалось целое число, получено: '" + value + "'");
        }
    }

    private Double doubleOf(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Ожидалось число, получено: '" + value + "'");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        // Только дата без времени -> начало дня.
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw ApiError.badRequest("Некорректная дата: '" + value + "' (ожидается ГГГГ-ММ-ДД[ ЧЧ:мм:сс])");
    }

    private String firstNonEmpty(String[] lines) {
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    /** Разбор строки CSV с поддержкой кавычек. */
    static List<String> parseCsvLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
