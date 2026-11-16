package api;

import dao.ClientDAO;
import dao.UserDAO;
import models.User;
import utils.Json;
import utils.PasswordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Управление пользователями (только админ):
 * список, смена роли, сброс пароля, блокировка/разблокировка,
 * редактирование профиля.
 */
public class UserHandler {
    private static final Set<String> ROLES = Set.of(
            User.ROLE_ADMIN, User.ROLE_MANAGER, User.ROLE_CLIENT);

    private final UserDAO userDAO = new UserDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final AuthService auth;
    private final OrderHandler.AuditDAOHolder audit;

    UserHandler(AuthService auth, OrderHandler.AuditDAOHolder audit) {
        this.auth = auth;
        this.audit = audit;
    }

    // ================== Список ==================

    public void list(ApiContext ctx, User admin) {
        String search = ctx.query("search");
        String role = ctx.query("role");
        if (role != null && !role.isBlank() && !ROLES.contains(role.trim())) {
            throw ApiError.badRequest("Недопустимая роль. Допустимо: ADMIN, MANAGER, CLIENT");
        }
        Boolean blocked = null;
        String blockedParam = ctx.query("blocked");
        if (blockedParam != null && !blockedParam.isBlank()) {
            if ("true".equalsIgnoreCase(blockedParam)) {
                blocked = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(blockedParam)) {
                blocked = Boolean.FALSE;
            } else {
                throw ApiError.badRequest("Параметр 'blocked' должен быть true или false");
            }
        }
        dao.Page<UserDAO.UserView> page =
                userDAO.listUsers(search, role, blocked, ctx.page(), ctx.limit());
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserDAO.UserView view : page.getItems()) {
            items.add(Json.obj(
                    "id", view.id,
                    "login", view.login,
                    "fullName", view.fullName,
                    "role", view.role,
                    "blocked", view.blocked,
                    "phone", view.phone,
                    "email", view.email,
                    "ordersCount", view.ordersCount,
                    "lastActionAt", formatTimestamp(view.lastActionAt)));
        }
        ctx.sendJson(200, Json.obj(
                "data", items,
                "page", page.getPage(),
                "limit", page.getLimit(),
                "total", page.getTotal(),
                "totalPages", page.getTotalPages()));
    }

    public void detail(ApiContext ctx, User admin, int userId) {
        User user = requireUser(userId);
        Map<String, Object> json = AuthService.userJson(user);
        var profile = clientDAO.getClientByUserId(userId);
        if (profile != null) {
            json.put("phone", profile.getPhone());
            json.put("email", profile.getEmail());
            json.put("clientId", profile.getId());
        }
        ctx.sendJson(200, json);
    }

    // ================== Смена роли ==================

    public void changeRole(ApiContext ctx, User admin, int userId) {
        Map<String, Object> body = ctx.readJsonBody();
        String role = Json.getString(body, "role");
        if (role == null || !ROLES.contains(role.trim())) {
            throw ApiError.badRequest("Укажите допустимую роль: ADMIN, MANAGER, CLIENT");
        }
        role = role.trim();
        User target = requireUser(userId);
        if (target.getId() == admin.getId() && !User.ROLE_ADMIN.equals(role)) {
            throw ApiError.conflict("Нельзя понизить роль своей учётной записи: вы можете потерять доступ");
        }
        if (target.getRole().equals(role)) {
            throw ApiError.conflict("У пользователя уже установлена роль " + role);
        }
        userDAO.updateRole(userId, role);
        audit.log(admin.getId(), "USER_ROLE_CHANGED", "USER", userId,
                "Роль пользователя " + target.getLogin() + ": " + target.getRole() + " -> " + role);
        ctx.sendJson(200, Json.obj(
                "message", "Роль пользователя " + target.getLogin() + " изменена на " + role,
                "userId", userId, "role", role));
    }

    // ================== Сброс пароля ==================

    public void resetPassword(ApiContext ctx, User admin, int userId) {
        User target = requireUser(userId);
        Map<String, Object> body = ctx.readJsonBody();
        String newPassword = Json.getString(body, "newPassword");
        boolean generated = false;
        if (newPassword == null || newPassword.isBlank()) {
            newPassword = PasswordUtils.generateRandomPassword();
            generated = true;
        }
        if (newPassword.length() < 6 || newPassword.length() > 100) {
            throw ApiError.badRequest("Пароль должен быть от 6 до 100 символов");
        }
        userDAO.updatePasswordHash(userId, PasswordUtils.hashNewPassword(newPassword));
        audit.log(admin.getId(), "USER_PASSWORD_RESET", "USER", userId,
                "Сброс пароля пользователя " + target.getLogin());
        Map<String, Object> response = Json.obj(
                "message", generated
                        ? "Пароль пользователя " + target.getLogin() + " сброшен, передайте ему новый пароль"
                        : "Пароль пользователя " + target.getLogin() + " изменён",
                "userId", userId);
        response.put("newPassword", newPassword);
        ctx.sendJson(200, response);
    }

    // ================== Блокировка / разблокировка ==================

    public void block(ApiContext ctx, User admin, int userId) {
        changeBlocked(ctx, admin, userId, true);
    }

    public void unblock(ApiContext ctx, User admin, int userId) {
        changeBlocked(ctx, admin, userId, false);
    }

    private void changeBlocked(ApiContext ctx, User admin, int userId, boolean blocked) {
        User target = requireUser(userId);
        if (target.getId() == admin.getId()) {
            throw ApiError.conflict("Нельзя заблокировать или разблокировать свою учётную запись");
        }
        if (target.isBlocked() == blocked) {
            throw ApiError.conflict(blocked
                    ? "Пользователь уже заблокирован"
                    : "Пользователь уже активен");
        }
        userDAO.setBlocked(userId, blocked);
        audit.log(admin.getId(),
                blocked ? "USER_BLOCKED" : "USER_UNBLOCKED", "USER", userId,
                (blocked ? "Блокировка" : "Разблокировка") + " пользователя " + target.getLogin());
        ctx.sendJson(200, Json.obj(
                "message", (blocked ? "Пользователь " : "Пользователь ") + target.getLogin()
                        + (blocked ? " заблокирован" : " разблокирован"),
                "userId", userId,
                "blocked", blocked));
    }

    // ================== Профиль ==================

    public void updateProfile(ApiContext ctx, User admin, int userId) {
        User target = requireUser(userId);
        Map<String, Object> body = ctx.readJsonBody();
        String fullName = Json.getString(body, "fullName");
        String phone = Json.getString(body, "phone");
        String email = Json.getString(body, "email");
        if (fullName == null && phone == null && email == null) {
            throw ApiError.badRequest("Передайте хотя бы одно поле: fullName, phone, email");
        }
        if (fullName != null && (fullName.trim().length() < 3 || fullName.trim().length() > 150)) {
            throw ApiError.badRequest("ФИО должно быть от 3 до 150 символов");
        }
        var profile = clientDAO.getClientByUserId(userId);
        if (phone != null) {
            if (profile == null) {
                throw ApiError.badRequest("У пользователя нет профиля клиента, телефон менять нечем");
            }
            if (clientDAO.phoneExistsOther(userId, phone.trim())) {
                throw ApiError.conflict("Этот телефон уже принадлежит другому клиенту");
            }
        }
        if (fullName != null) {
            userDAO.updateFullName(userId, fullName.trim());
        }
        if (profile != null && (phone != null || email != null)) {
            clientDAO.updateProfile(userId,
                    fullName != null ? fullName.trim() : profile.getFullName(),
                    phone != null ? phone.trim() : profile.getPhone(),
                    email != null ? email.trim() : profile.getEmail());
        }
        audit.log(admin.getId(), "USER_UPDATED", "USER", userId,
                "Обновлён профиль пользователя " + target.getLogin());
        ctx.sendJson(200, AuthService.userJson(userDAO.findById(userId)));
    }

    private User requireUser(int userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw ApiError.notFound("Пользователь не найден");
        }
        return user;
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null
                : timestamp.toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
