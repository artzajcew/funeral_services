package ru.memory.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.memory.backend.entity.Order;
import ru.memory.backend.entity.User;
import ru.memory.backend.repository.ClientRepository;
import ru.memory.backend.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV-импорт/экспорт заказов (только админ).
 * Экспорт: UTF-8 c BOM, разделитель «;».
 * Импорт: обязательные колонки client_id и total_cost, разделитель «;» или «,».
 */
@Service
public class CsvService {

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;

    public CsvService(OrderRepository orderRepository,
                      ClientRepository clientRepository,
                      AuditService auditService) {
        this.orderRepository = orderRepository;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    // ================== Экспорт ==================

    /** Экспорт текущей выборки (параметры как у списка). По умолчанию всё (archived=all). */
    @Transactional(readOnly = true)
    public String exportCsv(User admin, OrderQuery query) {
        Boolean archived = switch (query.archived() == null || query.archived().isBlank()
                ? "all" : query.archived()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF'); // BOM для Excel
        csv.append("id;client_id;client_name;created_at;status;total_cost;archived;comments_count\n");
        long exported = 0;

        // Экспорт может быть больше одной страницы — выгружаем порциями.
        int page = 1;
        while (true) {
            Pageable pageable = PageRequest.of(page - 1, 100,
                    Sort.by(Sort.Direction.ASC, "id"));
            Page<Order> result = orderRepository.searchOrders(
                    admin.isStaff() ? null : admin.getId(),
                    query.clientId(), query.status(), archived,
                    query.dateFrom() == null ? null : query.dateFrom().atStartOfDay(),
                    query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay(),
                    query.minCost(), query.maxCost(),
                    query.search() == null || query.search().isBlank() ? null : query.search().trim(),
                    null, pageable);
            for (Order order : result.getContent()) {
                csv.append(escapeCsv(String.valueOf(order.getId()))).append(';')
                        .append(escapeCsv(String.valueOf(order.getClient().getId()))).append(';')
                        .append(escapeCsv(nullSafe(order.getClient().getFullName()))).append(';')
                        .append(escapeCsv(OrderService.format(order.getCreatedAt()))).append(';')
                        .append(escapeCsv(order.getStatus())).append(';')
                        .append(escapeCsv(String.format("%.2f", order.getTotalCost()))).append(';')
                        .append(order.isArchived() ? "да" : "нет").append(';')
                        .append(order.getComments().size()).append('\n');
                exported++;
            }
            if (page >= result.getTotalPages() || result.getContent().isEmpty()) {
                break;
            }
            page++;
        }
        auditService.log(admin.getId(), "EXPORT_ORDERS", "ORDER", null,
                "Экспорт CSV: выгружено " + exported + " заказов");
        return csv.toString();
    }

    // ================== Импорт ==================

    @Transactional
    public Map<String, Object> importCsv(User admin, String text) {
        if (text == null || text.isBlank()) {
            throw ApiError.badRequest("Ожидается CSV-текст в теле запроса");
        }
        text = text.replace('\uFEFF', ' ');
        String[] lines = text.split("\r\n|\n|\r");
        String headerLine = firstNonEmpty(lines);
        if (headerLine == null) {
            throw ApiError.badRequest("CSV-файл пуст");
        }

        // Заголовок и определение разделителя.
        char delimiter = headerLine.indexOf(';') >= 0 ? ';' : ',';
        List<String> header = parseCsvLine(headerLine, delimiter);
        Map<String, Integer> col = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase(), i);
        }
        for (String required : new String[]{"client_id", "total_cost"}) {
            if (!col.containsKey(required)) {
                throw ApiError.badRequest("В заголовке CSV нет обязательной колонки '"
                        + required + "'. Ожидаемые колонки: client_id, status, total_cost, created_at");
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
                importRow(col, parseCsvLine(line, delimiter));
                imported++;
            } catch (RuntimeException e) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("line", lineNo);
                error.put("error", e.getMessage());
                errors.add(error);
            }
        }
        auditService.log(admin.getId(), "IMPORT_ORDERS", "ORDER", null,
                "Импорт CSV: импортировано " + imported + ", ошибок " + errors.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("totalLines", lineNo);
        result.put("errorsCount", errors.size());
        result.put("errors", errors);
        return result;
    }

    private void importRow(Map<String, Integer> col, List<String> cells) {
        Integer clientId = intOf(cell(col, cells, "client_id"));
        if (clientId == null) {
            throw ApiError.badRequest("Не указан client_id");
        }
        ru.memory.backend.entity.Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiError.badRequest(
                        "Клиент с ID " + clientId + " не существует"));
        String status = cell(col, cells, "status");
        if (status == null || status.isBlank()) {
            status = "NEW";
        }
        status = status.trim().toUpperCase();
        if (!OrderService.STATUSES.contains(status)) {
            throw ApiError.badRequest("Недопустимый статус '" + status + "'");
        }
        BigDecimal total = decimalOf(cell(col, cells, "total_cost"));
        if (total == null || total.signum() < 0) {
            throw ApiError.badRequest("Некорректная сумма total_cost");
        }
        LocalDateTime createdAt = OrderService.parseCsvDateTime(cell(col, cells, "created_at"));

        Order order = new Order(client, status, total);
        if (createdAt != null) {
            order.setCreatedAt(createdAt);
        }
        orderRepository.save(order);
    }

    // ================== Разбор CSV ==================

    private String cell(Map<String, Integer> col, List<String> cells, String name) {
        Integer index = col.get(name);
        if (index == null || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
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
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonEmpty(String[] lines) {
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    static Integer intOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Ожидалось целое число, получено: '" + value + "'");
        }
    }

    static BigDecimal decimalOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Ожидалось число, получено: '" + value + "'");
        }
    }

}
