package ru.memory.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.memory.backend.entity.AuditEntry;
import ru.memory.backend.repository.AuditRepository;
import ru.memory.backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Административные разделы: аналитика по заказам и журнал аудита.
 * Оба доступны только администратору (см. SecurityConfig).
 */
@Service
public class AdminService {

    private final ru.memory.backend.repository.AnalyticsRepository analyticsRepository;
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;

    public AdminService(ru.memory.backend.repository.AnalyticsRepository analyticsRepository,
                        AuditRepository auditRepository,
                        UserRepository userRepository) {
        this.analyticsRepository = analyticsRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    // ================== Аналитика ==================

    @Transactional(readOnly = true)
    public Map<String, Object> analytics(LocalDate from, LocalDate to, Integer topLimit) {
        if (from == null) {
            from = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            throw ApiError.badRequest("Дата начала не может быть позже даты окончания");
        }
        int limit = topLimit == null ? 10 : Math.max(1, topLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from.toString(), "to", to.toString()));
        result.put("overview", analyticsRepository.overview(from, to));
        result.put("byStatus", analyticsRepository.byStatus(from, to));
        result.put("byMonth", analyticsRepository.byMonth(from, to));
        result.put("topServices", analyticsRepository.topServices(from, to, limit));
        result.put("users", analyticsRepository.usersByRole());
        return result;
    }

    // ================== Журнал аудита ==================

    @Transactional(readOnly = true)
    public Map<String, Object> audit(Integer userId, String action, String entityType,
                                     LocalDate dateFrom, LocalDate dateTo,
                                     int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<AuditEntry> result = auditRepository.search(
                userId, (action == null || action.isBlank()) ? null : action.trim(),
                (entityType == null || entityType.isBlank()) ? null : entityType.trim(),
                dateFrom == null ? null : dateFrom.atStartOfDay(),
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(),
                pageable);

        List<Map<String, Object>> items = new ArrayList<>();
        for (AuditEntry entry : result.getContent()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", entry.getId());
            json.put("userId", entry.getUser() == null ? null : entry.getUser().getId());
            json.put("userLogin", entry.getUser() == null ? null : entry.getUser().getLogin());
            json.put("action", entry.getAction());
            json.put("entityType", entry.getEntityType());
            json.put("entityId", entry.getEntityId());
            json.put("details", entry.getDetails());
            json.put("createdAt", OrderService.format(entry.getCreatedAt()));
            items.add(json);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", items);
        response.put("page", page);
        response.put("limit", limit);
        response.put("total", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return response;
    }

    /** Список типов действий для фильтров интерфейса админа. */
    @Transactional(readOnly = true)
    public List<String> auditActions() {
        return auditRepository.distinctActions();
    }
}
