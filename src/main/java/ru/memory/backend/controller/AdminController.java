package ru.memory.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.memory.backend.service.AdminService;
import ru.memory.backend.service.ApiError;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Аналитика и журнал аудита (только админ). */
@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/api/analytics")
    public ResponseEntity<Map<String, Object>> analytics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer topLimit) {
        return ResponseEntity.ok(adminService.analytics(
                parseDate("from", from), parseDate("to", to), topLimit));
    }

    @GetMapping("/api/audit")
    public ResponseEntity<Map<String, Object>> audit(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        if (page < 1) {
            throw ApiError.badRequest("Параметр 'page' должен быть не меньше 1");
        }
        if (limit < 1 || limit > 100) {
            throw ApiError.badRequest("Параметр 'limit' должен быть от 1 до 100");
        }
        return ResponseEntity.ok(adminService.audit(userId, action, entityType,
                parseDate("dateFrom", dateFrom), parseDate("dateTo", dateTo), page, limit));
    }

    /** Список типов действий (для фильтров UI). */
    @GetMapping("/api/audit/actions")
    public ResponseEntity<Map<String, Object>> auditActions() {
        return ResponseEntity.ok(Map.of("data", adminService.auditActions()));
    }

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
