package api;

import dao.AuditDAO;
import dao.AnalyticsDAO;
import models.User;
import utils.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Административные разделы: аналитика и журнал аудита.
 * Оба доступны только администратору.
 */
public class AdminHandler {
    private final AnalyticsDAO analyticsDAO = new AnalyticsDAO();
    private final AuditDAO auditDAO;

    AdminHandler(OrderHandler.AuditDAOHolder audit) {
        this.auditDAO = audit.dao();
    }

    // ================== Аналитика ==================

    public void analytics(ApiContext ctx, User admin) {
        LocalDate from = ctx.queryDate("from");
        LocalDate to = ctx.queryDate("to");
        if (from == null) {
            from = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            throw ApiError.badRequest("Дата начала не может быть позже даты окончания");
        }
        int topLimit = ctx.queryInt("topLimit") == null ? 10 : Math.max(1, ctx.queryInt("topLimit"));

        Map<String, Object> result = Json.obj(
                "period", Json.obj(
                        "from", from.toString(),
                        "to", to.toString()),
                "overview", analyticsDAO.overview(from, to),
                "byStatus", analyticsDAO.byStatus(from, to),
                "byMonth", analyticsDAO.byMonth(from, to),
                "topServices", analyticsDAO.topServices(from, to, topLimit),
                "users", analyticsDAO.usersByRole());
        ctx.sendJson(200, result);
    }

    // ================== Журнал аудита ==================

    public void audit(ApiContext ctx, User admin) {
        Integer userId = ctx.queryInt("userId");
        String action = ctx.query("action");
        String entityType = ctx.query("entityType");
        LocalDate dateFrom = ctx.queryDate("dateFrom");
        LocalDate dateTo = ctx.queryDate("dateTo");
        dao.Page<models.AuditEntry> page = auditDAO.search(
                userId, action, entityType, dateFrom, dateTo, ctx.page(), ctx.limit());

        List<Map<String, Object>> items = new ArrayList<>();
        for (models.AuditEntry entry : page.getItems()) {
            items.add(Json.obj(
                    "id", entry.getId(),
                    "userId", entry.getUserId(),
                    "userLogin", entry.getUserLogin(),
                    "action", entry.getAction(),
                    "entityType", entry.getEntityType(),
                    "entityId", entry.getEntityId(),
                    "details", entry.getDetails(),
                    "createdAt", formatTimestamp(entry.getCreatedAt())));
        }
        ctx.sendJson(200, Json.obj(
                "data", items,
                "page", page.getPage(),
                "limit", page.getLimit(),
                "total", page.getTotal(),
                "totalPages", page.getTotalPages()));
    }

    /** Список типов действий для фильтров интерфейса. */
    public void auditActions(ApiContext ctx, User admin) {
        ctx.sendJson(200, Json.obj("data", auditDAO.distinctActions()));
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null
                : timestamp.toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
