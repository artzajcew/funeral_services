package ru.memory.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.memory.backend.entity.Client;
import ru.memory.backend.entity.User;
import ru.memory.backend.repository.ClientRepository;
import ru.memory.backend.repository.UserRepository;
import ru.memory.backend.security.JwtService;
import ru.memory.backend.util.PasswordUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Регистрация, вход, текущий пользователь.
 *
 * Токен выдаётся на 24 часа; роль и блокировка проверяются по
 * актуальным данным из БД при каждом запросе (в JwtAuthFilter).
 */
@Service
public class AuthService {

    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       ClientRepository clientRepository,
                       AuditService auditService,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
        this.jwtService = jwtService;
    }

    // ================== Регистрация ==================

    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String login = str(body, "login");
        String password = str(body, "password");
        String fullName = str(body, "fullName");
        String phone = str(body, "phone");
        String email = str(body, "email");

        if (login == null || password == null || fullName == null || phone == null) {
            throw ApiError.badRequest("Обязательные поля: login, password, fullName, phone");
        }
        login = login.trim();
        fullName = fullName.trim();
        phone = phone.trim();
        if (email != null) {
            email = email.trim();
        }

        if (!LOGIN_PATTERN.matcher(login).matches()) {
            throw ApiError.badRequest(
                    "Логин: 3-50 символов, только латиница, цифры и знак подчёркивания");
        }
        if (password.length() < 6 || password.length() > 100) {
            throw ApiError.badRequest("Пароль должен быть от 6 до 100 символов");
        }
        if (fullName.length() < 3 || fullName.length() > 150) {
            throw ApiError.badRequest("ФИО должно быть от 3 до 150 символов");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw ApiError.badRequest("Телефон указан некорректно");
        }
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw ApiError.badRequest("E-mail указан некорректно");
        }
        if (userRepository.findByLogin(login).isPresent()) {
            throw ApiError.conflict("Логин уже занят");
        }
        if (clientRepository.existsByPhone(phone)) {
            throw ApiError.conflict("Телефон уже зарегистрирован");
        }
        if (email != null && !email.isBlank() && clientRepository.existsByEmail(email)) {
            throw ApiError.conflict("E-mail уже зарегистрирован");
        }

        // Пользователь и профиль клиента создаются в одной транзакции.
        User user = new User(login, PasswordUtils.hashNewPassword(password),
                fullName, User.ROLE_CLIENT);
        user = userRepository.save(user);
        clientRepository.save(new Client(user, phone,
                (email == null || email.isBlank()) ? null : email, fullName));

        auditService.log(user.getId(), "REGISTER", "USER", user.getId(),
                "Зарегистрирован пользователь " + login);
        return tokenResponse(user, "Регистрация выполнена");
    }

    // ================== Вход ==================

    @Transactional
    public Map<String, Object> login(Map<String, Object> body) {
        String login = str(body, "login");
        String password = str(body, "password");
        if (login == null || password == null) {
            throw ApiError.badRequest("Укажите login и password");
        }
        User user = userRepository.findByLogin(login.trim()).orElse(null);
        if (user == null || !PasswordUtils.verify(password, user.getPassword())) {
            Integer failedId = user == null ? null : user.getId();
            auditService.logNow(failedId, "LOGIN_FAILED", "USER", failedId,
                    "Неудачная попытка входа: " + login);
            throw ApiError.unauthorized("Неверный логин или пароль");
        }
        if (user.isBlocked()) {
            auditService.log(user.getId(), "LOGIN_BLOCKED", "USER", user.getId(),
                    "Попытка входа в заблокированную учётную запись " + login);
            throw ApiError.forbidden("Учётная запись заблокирована. Обратитесь к администратору.");
        }
        if (PasswordUtils.needsUpgrade(user.getPassword())) {
            // Перезапись legacy SHA-256 хеша на PBKDF2 после успешного входа.
            user.setPassword(PasswordUtils.hashNewPassword(password));
            userRepository.save(user);
        }
        auditService.log(user.getId(), "LOGIN", null, null, "Вход в систему: " + login);
        return tokenResponse(user, "Вход выполнен");
    }

    // ================== Текущий пользователь ==================

    @Transactional(readOnly = true)
    public Map<String, Object> me(User user) {
        Map<String, Object> json = userJson(user);
        clientRepository.findByUserId(user.getId()).ifPresent(client ->
                json.put("client", clientJson(client)));
        return json;
    }

    private Map<String, Object> tokenResponse(User user, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("token", jwtService.createToken(user.getId(), user.getLogin(), user.getRole()));
        response.put("tokenType", "Bearer");
        response.put("expiresIn", JwtService.TOKEN_TTL_SECONDS);
        response.put("user", userJson(user));
        return response;
    }

    // ================== JSON-представления ==================

    public static Map<String, Object> userJson(User user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("login", user.getLogin());
        json.put("fullName", user.getFullName());
        json.put("role", user.getRole());
        json.put("blocked", user.isBlocked());
        return json;
    }

    public static Map<String, Object> clientJson(Client client) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", client.getId());
        json.put("userId", client.getUser().getId());
        json.put("phone", client.getPhone());
        json.put("email", client.getEmail());
        json.put("fullName", client.getFullName());
        return json;
    }

    // ================== Вспомогательные ==================


    static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof String s ? s : null;
    }
}
