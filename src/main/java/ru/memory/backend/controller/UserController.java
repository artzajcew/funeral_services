package ru.memory.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.memory.backend.security.UserPrincipal;
import ru.memory.backend.service.ApiError;
import ru.memory.backend.service.UserService;

import java.util.Map;

/**
 * Управление пользователями (только админ): список, карточка,
 * смена роли, сброс пароля, блокировка, профиль.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String blocked,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        Boolean blockedFilter = null;
        if (blocked != null && !blocked.isBlank()) {
            if ("true".equalsIgnoreCase(blocked)) {
                blockedFilter = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(blocked)) {
                blockedFilter = Boolean.FALSE;
            } else {
                throw ApiError.badRequest("Параметр 'blocked' должен быть true или false");
            }
        }
        if (page < 1) {
            throw ApiError.badRequest("Параметр 'page' должен быть не меньше 1");
        }
        if (limit < 1 || limit > 100) {
            throw ApiError.badRequest("Параметр 'limit' должен быть от 1 до 100");
        }
        return ResponseEntity.ok(userService.list(search, role, blockedFilter, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.detail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(userService.updateProfile(principal.getUser(), id, body));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> changeRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(userService.changeRole(principal.getUser(), id, body));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(userService.resetPassword(principal.getUser(), id,
                body == null ? Map.of() : body));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<Map<String, Object>> block(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(userService.changeBlocked(principal.getUser(), id, true));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<Map<String, Object>> unblock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(userService.changeBlocked(principal.getUser(), id, false));
    }
}
