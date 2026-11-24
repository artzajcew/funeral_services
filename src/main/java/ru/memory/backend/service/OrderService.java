package ru.memory.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import ru.memory.backend.entity.Client;
import ru.memory.backend.entity.Comment;
import ru.memory.backend.entity.Order;
import ru.memory.backend.entity.OrderItem;
import ru.memory.backend.entity.Service;
import ru.memory.backend.entity.User;
import ru.memory.backend.repository.ClientRepository;
import ru.memory.backend.repository.CommentRepository;
import ru.memory.backend.repository.OrderRepository;
import ru.memory.backend.repository.ServiceRepository;

import java.math.BigDecimal;
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
 * Заказы — основная сущность системы: CRUD, поиск/фильтры/сортировка/
 * пагинация, архив (админ), комментарии, массовые операции (админ),
 * CSV-импорт/экспорт (админ).
 */
@org.springframework.stereotype.Service
public class OrderService {

    public static final Set<String> STATUSES = Set.of(
            "NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter[] CSV_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME};

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final CommentRepository commentRepository;
    private final ServiceRepository serviceRepository;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository,
                        ClientRepository clientRepository,
                        CommentRepository commentRepository,
                        ServiceRepository serviceRepository,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.clientRepository = clientRepository;
        this.commentRepository = commentRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    // ================== Список и поиск ==================

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> list(User user, OrderQuery query) {
        Boolean archived;
        if (user.isStaff()) {
            archived = switch (query.archived() == null || query.archived().isBlank()
                    ? "false" : query.archived()) {
                case "true" -> Boolean.TRUE;
                case "all" -> null;
                default -> Boolean.FALSE;
            };
        } else {
            // Клиент архивные заказы не видит вообще.
            archived = Boolean.FALSE;
        }

        Pageable pageable = PageRequest.of(query.page() - 1, query.limit(),
                sort(query.sortBy(), query.sortDir()));
        Integer searchNumber = query.search() != null && query.search().trim().matches("\\d+")
                ? Integer.parseInt(query.search().trim()) : null;

        Page<Order> page = orderRepository.searchOrders(
                user.isStaff() ? null : user.getId(),
                user.isStaff() ? query.clientId() : null,
                query.status(), archived,
                query.dateFrom() == null ? null : query.dateFrom().atStartOfDay(),
                query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay(),
                query.minCost(), query.maxCost(),
                query.search() == null || query.search().isBlank() ? null : query.search().trim(),
                searchNumber, pageable);

        List<Map<String, Object>> items = new ArrayList<>();
        page.getContent().forEach(order -> items.add(orderJson(order, false)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", items);
        result.put("page", query.page());
        result.put("limit", query.limit());
        result.put("total", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return result;
    }

    /** Whitelist столбцов сортировки. */
    private Sort sort(String sortBy, String sortDir) {
        String column = switch (sortBy == null ? "" : sortBy) {
            case "id" -> "id";
            case "status" -> "status";
            case "total_cost", "totalCost" -> "totalCost";
            case "client_name", "client" -> "client.fullName";
            default -> "createdAt";
        };
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, column).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    // ================== Детализация ==================

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> detail(User user, int orderId) {
        Order order = requireAccessibleOrder(user, orderId);
        Map<String, Object> response = orderJson(order, true);
        response.put("items", itemsJson(order.getItems()));
        return response;
    }

    // ================== Создание ==================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> create(User user, Map<String, Object> body) {
        List<ItemInput> items = parseItems(body.get("items"));

        Client client;
        if (user.isStaff()) {
            Integer requested = intOf(body.get("clientId"));
            if (requested == null) {
                throw ApiError.badRequest("Укажите clientId клиента, для которого создаётся заказ");
            }
            client = clientRepository.findById(requested)
                    .orElseThrow(() -> ApiError.badRequest(
                            "Клиент с ID " + requested + " не найден"));
        } else {
            client = clientRepository.findByUserId(user.getId())
                    .orElseThrow(() -> ApiError.badRequest(
                            "У вашей учётной записи нет профиля клиента"));
        }

        // Цены берутся из каталога, сумма считается на сервере.
        Order order = new Order(client, Order.STATUS_NEW, BigDecimal.ZERO);
        for (ItemInput input : items) {
            Service service = serviceRepository.findById(input.serviceId)
                    .orElseThrow(() -> ApiError.badRequest(
                            "Услуга с ID " + input.serviceId + " не найдена"));
            order.getItems().add(new OrderItem(order, service, input.quantity, service.getPrice()));
        }
        order.setTotalCost(totalCost(order));
        order = orderRepository.save(order);

        auditService.log(user.getId(), "CREATE_ORDER", "ORDER", order.getId(),
                "Создан заказ №" + order.getId() + " (клиент " + client.getId() + ")");

        Map<String, Object> response = orderJson(orderRepository.findById(order.getId()).get(), true);
        response.put("items", itemsJson(orderRepository.findById(order.getId()).get().getItems()));
        return response;
    }

    // ================== Обновление ==================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> update(User user, int orderId, Map<String, Object> body) {
        Order order = requireAccessibleOrder(user, orderId);
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ находится в архиве: сначала восстановите его");
        }

        boolean staff = user.isStaff();
        String requestedStatus = str(body.get("status"));
        Integer requestedClientId = intOf(body.get("clientId"));
        boolean hasItems = body.containsKey("items") && body.get("items") != null;

        if (!staff) {
            // Клиент работает только со своим заказом.
            if (requestedClientId != null) {
                throw ApiError.forbidden("Смену клиента заказа выполняет сотрудник бюро");
            }
            if (requestedStatus != null && !"CANCELLED".equals(requestedStatus)) {
                throw ApiError.forbidden("Клиент может только отменить заказ (status=CANCELLED)");
            }
            if (hasItems && !Order.STATUS_NEW.equals(order.getStatus())) {
                throw ApiError.badRequest(
                        "Состав заказа можно менять, только пока он в статусе NEW");
            }
            if ("CANCELLED".equals(requestedStatus) && Order.STATUS_COMPLETED.equals(order.getStatus())) {
                throw ApiError.badRequest("Выполненный заказ нельзя отменить");
            }
        }

        if (requestedStatus != null && !STATUSES.contains(requestedStatus)) {
            throw ApiError.badRequest(
                    "Недопустимый статус. Допустимо: NEW, IN_PROGRESS, COMPLETED, CANCELLED");
        }
        Client newClient = null;
        if (requestedClientId != null) {
            newClient = clientRepository.findById(requestedClientId)
                    .orElseThrow(() -> ApiError.badRequest(
                            "Клиент с ID " + requestedClientId + " не найден"));
        }

        List<String> changes = new ArrayList<>();
        if (hasItems) {
            List<ItemInput> items = parseItems(body.get("items"));
            order.getItems().clear();
            for (ItemInput input : items) {
                Service service = serviceRepository.findById(input.serviceId)
                        .orElseThrow(() -> ApiError.badRequest(
                                "Услуга с ID " + input.serviceId + " не найдена"));
                order.getItems().add(new OrderItem(order, service, input.quantity, service.getPrice()));
            }
            order.setTotalCost(totalCost(order));
            changes.add("состав заказа (новая сумма "
                    + String.format("%.2f", order.getTotalCost()) + ")");
        }
        if (requestedClientId != null) {
            order.setClient(newClient);
            changes.add("clientId -> " + requestedClientId);
        }
        if (requestedStatus != null) {
            order.setStatus(requestedStatus);
            changes.add("статус -> " + requestedStatus);
        }
        if (changes.isEmpty()) {
            throw ApiError.badRequest("Нечего обновлять: передайте items, status или clientId");
        }
        orderRepository.save(order);
        auditService.log(user.getId(), "UPDATE_ORDER", "ORDER", orderId,
                "Изменено: " + String.join(", ", changes));

        Order updated = orderRepository.findById(orderId).get();
        Map<String, Object> response = orderJson(updated, true);
        response.put("items", itemsJson(updated.getItems()));
        return response;
    }

    // ================== Удаление ==================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> delete(User user, int orderId) {
        Order order = requireAccessibleOrder(user, orderId);
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ в архиве: сначала восстановите его или оставьте в архиве");
        }
        if (!user.isStaff() && Order.STATUS_COMPLETED.equals(order.getStatus())) {
            throw ApiError.badRequest("Выполненный заказ нельзя удалить");
        }
        orderRepository.delete(order);
        auditService.log(user.getId(), "DELETE_ORDER", "ORDER", orderId,
                "Удалён заказ №" + orderId + " (клиент " + order.getClient().getId() + ")");
        return Map.of("message", "Заказ №" + orderId + " удалён", "id", orderId);
    }

    // ================== Архив (только админ) ==================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> archive(User user, int orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiError.notFound("Заказ не найден"));
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ уже находится в архиве");
        }
        order.setArchived(true);
        order.setArchivedAt(LocalDateTime.now());
        orderRepository.save(order);
        auditService.log(user.getId(), "ARCHIVE_ORDER", "ORDER", orderId,
                "Заказ №" + orderId + " перенесён в архив");
        return Map.of("message", "Заказ №" + orderId + " перенесён в архив", "id", orderId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> restore(User user, int orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiError.notFound("Заказ не найден"));
        if (!order.isArchived()) {
            throw ApiError.conflict("Заказ не находится в архиве");
        }
        order.setArchived(false);
        order.setArchivedAt(null);
        orderRepository.save(order);
        auditService.log(user.getId(), "RESTORE_ORDER", "ORDER", orderId,
                "Заказ №" + orderId + " восстановлен из архива");
        return Map.of("message", "Заказ №" + orderId + " восстановлен", "id", orderId);
    }

    // ================== Комментарии ==================

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> listComments(User user, int orderId) {
        Order order = requireAccessibleOrder(user, orderId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Comment comment : commentRepository
                .findByOrderIdOrderByCreatedAtAscIdAsc(orderId)) {
            items.add(commentJson(comment));
        }
        return Map.of("data", items, "total", items.size());
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> addComment(User user, int orderId, Map<String, Object> body) {
        Order order = requireAccessibleOrder(user, orderId);
        if (order.isArchived()) {
            throw ApiError.conflict("Заказ в архиве: комментарии недоступны");
        }
        String text = str(body.get("text"));
        if (text == null || text.trim().isEmpty()) {
            throw ApiError.badRequest("Поле 'text' обязательно");
        }
        text = text.trim();
        if (text.length() > 2000) {
            throw ApiError.badRequest("Комментарий не может быть длиннее 2000 символов");
        }
        Comment comment = commentRepository.save(
                new Comment(order, user, text));
        auditService.log(user.getId(), "ADD_COMMENT", "ORDER", orderId,
                "Добавлен комментарий к заказу №" + orderId);
        return commentJson(comment);
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> deleteComment(User user, int orderId, int commentId) {
        requireAccessibleOrder(user, orderId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("Комментарий не найден"));
        if (comment.getOrder().getId() != orderId) {
            throw ApiError.notFound("Комментарий не найден");
        }
        if (!user.isAdmin() && comment.getAuthor().getId() != user.getId()) {
            throw ApiError.forbidden("Удалить комментарий может его автор или администратор");
        }
        commentRepository.delete(comment);
        auditService.log(user.getId(), "DELETE_COMMENT", "ORDER", orderId,
                "Удалён комментарий №" + commentId + " к заказу №" + orderId);
        return Map.of("message", "Комментарий удалён", "id", commentId);
    }

    // ================== Массовые операции (только админ) ==================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> bulk(User user, Map<String, Object> body) {
        Object rawIds = body.get("ids");
        String action = str(body.get("action"));
        if (!(rawIds instanceof List<?> list) || list.isEmpty()) {
            throw ApiError.badRequest("Передайте массив ID записей в поле 'ids'");
        }
        if (action == null) {
            throw ApiError.badRequest(
                    "Передайте 'action': archive, restore, delete или setStatus");
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (Object raw : list) {
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
            status = str(body.get("status"));
            if (status == null || !STATUSES.contains(status)) {
                throw ApiError.badRequest("Для setStatus передайте допустимый статус: "
                        + "NEW, IN_PROGRESS, COMPLETED, CANCELLED");
            }
        } else if (!"archive".equals(action) && !"restore".equals(action)
                && !"delete".equals(action)) {
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
                    failed.add(Map.of("id", id, "error", error));
                }
            } catch (RuntimeException e) {
                failed.add(Map.of("id", id, "error", e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("requested", ids.size());
        result.put("successCount", success.size());
        result.put("failedCount", failed.size());
        result.put("success", success);
        result.put("failed", failed);
        auditService.log(user.getId(), "BULK_OPERATION", "ORDER", null,
                "Массовая операция '" + action + "' над " + ids.size()
                        + " записями: успешно " + success.size() + ", с ошибками " + failed.size());
        return result;
    }

    private String applyBulkAction(String action, String status, int id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return "заказ не найден";
        }
        switch (action) {
            case "archive":
                if (order.isArchived()) {
                    return "уже в архиве";
                }
                order.setArchived(true);
                order.setArchivedAt(LocalDateTime.now());
                orderRepository.save(order);
                return null;
            case "restore":
                if (!order.isArchived()) {
                    return "не в архиве";
                }
                order.setArchived(false);
                order.setArchivedAt(null);
                orderRepository.save(order);
                return null;
            case "delete":
                orderRepository.delete(order);
                return null;
            case "setStatus":
                if (order.getStatus().equals(status)) {
                    return "статус уже " + status;
                }
                order.setStatus(status);
                orderRepository.save(order);
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
    private Order requireAccessibleOrder(User user, int orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiError.notFound("Заказ не найден"));
        if (!user.isStaff()) {
            Client profile = order.getClient();
            if (profile == null || profile.getUser().getId() != user.getId()) {
                throw ApiError.notFound("Заказ не найден");
            }
            if (order.isArchived()) {
                // Архивные заказы клиенту скрыты.
                throw ApiError.notFound("Заказ не найден");
            }
        }
        return order;
    }

    // ================== Вспомогательные ==================

    private record ItemInput(int serviceId, int quantity) {
    }

    private List<ItemInput> parseItems(Object rawItems) {
        if (!(rawItems instanceof List<?> list) || list.isEmpty()) {
            throw ApiError.badRequest("Поле 'items' обязательно: список услуг заказа");
        }
        List<ItemInput> items = new ArrayList<>();
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> item)) {
                throw ApiError.badRequest(
                        "Каждый элемент items должен быть объектом {serviceId, quantity}");
            }
            Integer serviceId = intOf(item.get("serviceId"));
            Integer quantity = intOf(item.get("quantity"));
            if (serviceId == null || quantity == null) {
                throw ApiError.badRequest("В позициях заказа обязательны serviceId и quantity");
            }
            items.add(new ItemInput(serviceId, quantity));
        }
        return items;
    }

    private BigDecimal totalCost(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            total = total.add(item.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    public static Map<String, Object> orderJson(Order order, boolean detail) {
        Client client = order.getClient();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", order.getId());
        json.put("clientId", client == null ? null : client.getId());
        json.put("clientName", client == null ? null : client.getFullName());
        json.put("clientPhone", client == null ? null : client.getPhone());
        json.put("createdAt", format(order.getCreatedAt()));
        json.put("status", order.getStatus());
        json.put("totalCost", order.getTotalCost());
        json.put("archived", order.isArchived());
        json.put("commentsCount", order.getComments().size());
        if (detail) {
            json.put("archivedAt", format(order.getArchivedAt()));
        }
        return json;
    }

    private List<Map<String, Object>> itemsJson(List<OrderItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderItem item : items) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", item.getId());
            json.put("serviceId", item.getService().getId());
            json.put("serviceName", item.getService().getName());
            json.put("quantity", item.getQuantity());
            json.put("price", item.getPrice());
            result.add(json);
        }
        return result;
    }

    private Map<String, Object> commentJson(Comment comment) {
        User author = comment.getAuthor();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", comment.getId());
        json.put("orderId", comment.getOrder().getId());
        json.put("authorId", author.getId());
        json.put("authorName", author.getFullName());
        json.put("authorRole", author.getRole());
        json.put("text", comment.getBody());
        json.put("createdAt", format(comment.getCreatedAt()));
        return json;
    }

    static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_FORMAT);
    }

    static Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static String str(Object value) {
        return value instanceof String s ? s : null;
    }


    /** Разбор даты из CSV: ГГГГ-ММ-ДД или с временем. */
    static LocalDateTime parseCsvDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter formatter : CSV_DATE_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw ApiError.badRequest(
                "Некорректная дата: '" + value + "' (ожидается ГГГГ-ММ-ДД[ ЧЧ:мм:сс])");
    }
}
