package ru.memory.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.memory.backend.entity.User;
import ru.memory.backend.repository.ClientRepository;
import ru.memory.backend.repository.UserRepository;
import ru.memory.backend.util.PasswordUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Управление пользователями (только админ): список, смена роли,
 * сброс пароля, блокировка/разблокировка, редактирование профиля.
 */
@Service
public class UserService {

    private static final Set<String> ROLES = Set.of(
            User.ROLE_ADMIN, User.ROLE_MANAGER, User.ROLE_CLIENT);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       ClientRepository clientRepository,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    // ================== Список ==================

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String role, Boolean blocked,
                                    int page, int limit) {
        if (role != null && !role.isBlank() && !ROLES.contains(role.trim())) {
            throw ApiError.badRequest("Недопустимая роль. Допустимо: ADMIN, MANAGER, CLIENT");
        }
        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<UserRepository.UserListItem> result = userRepository.searchUsers(
                (search == null || search.isBlank()) ? null : search.trim(),
                (role == null || role.isBlank()) ? null : role.trim(),
                blocked, pageable);

        List<Map<String, Object>> items = new ArrayList<>();
        for (UserRepository.UserListItem view : result.getContent()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", view.getUser().getId());
            json.put("login", view.getUser().getLogin());
            json.put("fullName", view.getUser().getFullName());
            json.put("role", view.getUser().getRole());
            json.put("blocked", view.getUser().isBlocked());
            json.put("phone", view.getClient() == null ? null : view.getClient().getPhone());
            json.put("email", view.getClient() == null ? null : view.getClient().getEmail());
            json.put("ordersCount", view.getOrdersCount());
            json.put("lastActionAt", OrderService.format(view.getLastActionAt()));
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

    @Transactional(readOnly = true)
    public Map<String, Object> detail(int userId) {
        User user = requireUser(userId);
        Map<String, Object> json = AuthService.userJson(user);
        clientRepository.findByUserId(userId).ifPresent(client -> {
            json.put("phone", client.getPhone());
            json.put("email", client.getEmail());
            json.put("clientId", client.getId());
        });
        return json;
    }

    // ================== Смена роли ==================

    @Transactional
    public Map<String, Object> changeRole(User admin, int userId, Map<String, Object> body) {
        String role = OrderService.str(body.get("role"));
        if (role == null || !ROLES.contains(role.trim())) {
            throw ApiError.badRequest("Укажите допустимую роль: ADMIN, MANAGER, CLIENT");
        }
        role = role.trim();
        User target = requireUser(userId);
        if (target.getId().equals(admin.getId()) && !User.ROLE_ADMIN.equals(role)) {
            throw ApiError.conflict(
                    "Нельзя понизить роль своей учётной записи: вы можете потерять доступ");
        }
        if (target.getRole().equals(role)) {
            throw ApiError.conflict("У пользователя уже установлена роль " + role);
        }
        target.setRole(role);
        userRepository.save(target);
        auditService.log(admin.getId(), "USER_ROLE_CHANGED", "USER", userId,
                "Роль пользователя " + target.getLogin() + ": " + target.getRole() + " -> " + role);
        return Map.of(
                "message", "Роль пользователя " + target.getLogin() + " изменена на " + role,
                "userId", userId, "role", role);
    }

    // ================== Сброс пароля ==================

    @Transactional
    public Map<String, Object> resetPassword(User admin, int userId, Map<String, Object> body) {
        User target = requireUser(userId);
        String newPassword = OrderService.str(body.get("newPassword"));
        boolean generated = false;
        if (newPassword == null || newPassword.isBlank()) {
            newPassword = PasswordUtils.generateRandomPassword();
            generated = true;
        }
        if (newPassword.length() < 6 || newPassword.length() > 100) {
            throw ApiError.badRequest("Пароль должен быть от 6 до 100 символов");
        }
        target.setPassword(PasswordUtils.hashNewPassword(newPassword));
        userRepository.save(target);
        auditService.log(admin.getId(), "USER_PASSWORD_RESET", "USER", userId,
                "Сброс пароля пользователя " + target.getLogin());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", generated
                ? "Пароль пользователя " + target.getLogin() + " сброшен, передайте ему новый пароль"
                : "Пароль пользователя " + target.getLogin() + " изменён");
        response.put("userId", userId);
        response.put("newPassword", newPassword);
        return response;
    }

    // ================== Блокировка ==================

    @Transactional
    public Map<String, Object> changeBlocked(User admin, int userId, boolean blocked) {
        User target = requireUser(userId);
        if (target.getId().equals(admin.getId())) {
            throw ApiError.conflict(
                    "Нельзя заблокировать или разблокировать свою учётную запись");
        }
        if (target.isBlocked() == blocked) {
            throw ApiError.conflict(blocked
                    ? "Пользователь уже заблокирован"
                    : "Пользователь уже активен");
        }
        target.setBlocked(blocked);
        userRepository.save(target);
        auditService.log(admin.getId(), blocked ? "USER_BLOCKED" : "USER_UNBLOCKED", "USER", userId,
                (blocked ? "Блокировка" : "Разблокировка") + " пользователя " + target.getLogin());
        return Map.of(
                "message", "Пользователь " + target.getLogin()
                        + (blocked ? " заблокирован" : " разблокирован"),
                "userId", userId,
                "blocked", blocked);
    }

    // ================== Профиль ==================

    @Transactional
    public Map<String, Object> updateProfile(User admin, int userId, Map<String, Object> body) {
        User target = requireUser(userId);
        String fullName = OrderService.str(body.get("fullName"));
        String phone = OrderService.str(body.get("phone"));
        String email = OrderService.str(body.get("email"));
        if (fullName == null && phone == null && email == null) {
            throw ApiError.badRequest("Передайте хотя бы одно поле: fullName, phone, email");
        }
        if (fullName != null && (fullName.trim().length() < 3 || fullName.trim().length() > 150)) {
            throw ApiError.badRequest("ФИО должно быть от 3 до 150 символов");
        }
        var profile = clientRepository.findByUserId(userId).orElse(null);
        if (phone != null) {
            if (profile == null) {
                throw ApiError.badRequest(
                        "У пользователя нет профиля клиента, телефон менять нечем");
            }
            if (clientRepository.existsByPhoneAndUserIdNot(phone.trim(), userId)) {
                throw ApiError.conflict("Этот телефон уже принадлежит другому клиенту");
            }
        }
        if (fullName != null) {
            target.setFullName(fullName.trim());
            userRepository.save(target);
        }
        if (profile != null && (phone != null || email != null)) {
            if (fullName != null) {
                profile.setFullName(fullName.trim());
            }
            if (phone != null) {
                profile.setPhone(phone.trim());
            }
            if (email != null) {
                profile.setEmail(email.trim().isBlank() ? null : email.trim());
            }
            clientRepository.save(profile);
        }
        auditService.log(admin.getId(), "USER_UPDATED", "USER", userId,
                "Обновлён профиль пользователя " + target.getLogin());
        return AuthService.userJson(requireUser(userId));
    }

    // ================== Вспомогательные ==================

    private User requireUser(int userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiError.notFound("Пользователь не найден"));
    }

}
