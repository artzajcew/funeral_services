package ru.memory.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.memory.backend.entity.User;
import ru.memory.backend.security.UserPrincipal;
import ru.memory.backend.service.ApiError;
import ru.memory.backend.service.CsvService;
import ru.memory.backend.service.OrderQuery;
import ru.memory.backend.service.OrderService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Заказы: CRUD, поиск/фильтры/сортировка/пагинация, архив (админ),
 * комментарии, массовые операции (админ), CSV (админ).
 *
 * Права: /api/orders/** — любая роль; bulk/export/import — только
 * ADMIN (см. SecurityConfig).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CsvService csvService;

    public OrderController(OrderService orderService, CsvService csvService) {
        this.orderService = orderService;
        this.csvService = csvService;
    }

    // ================== Список и поиск ==================

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) BigDecimal minCost,
            @RequestParam(required = false) BigDecimal maxCost,
            @RequestParam(required = false) String archived,
            @RequestParam(required = false, defaultValue = "created_at") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDir,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        if (status != null && !status.isBlank()) {
            if (!OrderService.STATUSES.contains(status.trim())) {
                throw ApiError.badRequest(
                        "Недопустимый статус. Допустимо: NEW, IN_PROGRESS, COMPLETED, CANCELLED");
            }
            status = status.trim();
        } else {
            status = null;
        }
        LocalDate from = parseDate("dateFrom", dateFrom);
        LocalDate to = parseDate("dateTo", dateTo);
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiError.badRequest("dateFrom не может быть позже dateTo");
        }
        if (minCost != null && maxCost != null && minCost.compareTo(maxCost) > 0) {
            throw ApiError.badRequest("minCost не может быть больше maxCost");
        }
        if (!"ASC".equalsIgnoreCase(sortDir) && !"DESC".equalsIgnoreCase(sortDir)) {
            throw ApiError.badRequest("sortDir должен быть ASC или DESC");
        }
        if (page < 1) {
            throw ApiError.badRequest("Параметр 'page' должен быть не меньше 1");
        }
        if (limit < 1 || limit > 100) {
            throw ApiError.badRequest("Параметр 'limit' должен быть от 1 до 100");
        }

        OrderQuery query = new OrderQuery(search, status, clientId, from, to,
                minCost, maxCost, archived, sortBy, sortDir, page, limit);
        return ResponseEntity.ok(orderService.list(principal.getUser(), query));
    }

    // ================== Детализация ==================

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(orderService.detail(principal.getUser(), id));
    }

    // ================== Создание ==================

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201)
                .body(orderService.create(principal.getUser(), body));
    }

    // ================== Обновление ==================

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(orderService.update(principal.getUser(), id, body));
    }

    // ================== Удаление ==================

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(orderService.delete(principal.getUser(), id));
    }

    // ================== Архив (только админ) ==================

    @PostMapping("/{id}/archive")
    public ResponseEntity<Map<String, Object>> archive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(orderService.archive(principal.getUser(), id));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(orderService.restore(principal.getUser(), id));
    }

    // ================== Комментарии ==================

    @GetMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> listComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(orderService.listComments(principal.getUser(), id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201)
                .body(orderService.addComment(principal.getUser(), id, body));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @PathVariable Integer commentId) {
        return ResponseEntity.ok(orderService.deleteComment(principal.getUser(), id, commentId));
    }

    // ================== Массовые операции (только админ) ==================

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulk(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(orderService.bulk(principal.getUser(), body));
    }

    // ================== CSV (только админ) ==================

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) BigDecimal minCost,
            @RequestParam(required = false) BigDecimal maxCost,
            @RequestParam(required = false) String archived,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "ASC") String sortDir) {
        User user = principal.getUser();
        OrderQuery query = new OrderQuery(search,
                (status == null || status.isBlank()) ? null : status.trim(),
                clientId, parseDate("dateFrom", dateFrom), parseDate("dateTo", dateTo),
                minCost, maxCost, archived, sortBy, sortDir, 1, 100);
        String csv = csvService.exportCsv(user, query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"orders_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(csv);
    }

    @PostMapping(value = "/import", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody String csvText) {
        return ResponseEntity.ok(csvService.importCsv(principal.getUser(), csvText));
    }

    // ================== Вспомогательные ==================

    private LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest(
                    "Параметр '" + name + "' должен быть датой в формате YYYY-MM-DD");
        }
    }
}
