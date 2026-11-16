package api;

import dao.AuditDAO;
import dao.ClientDAO;
import dao.DaoException;
import dao.UserDAO;
import db.DatabaseConnector;
import models.Client;
import models.User;
import utils.Json;
import utils.JwtUtil;
import utils.JwtUtil.Claims;
import utils.PasswordUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Регистрация, вход, проверка JWT и прав доступа.
 *
 * Токен проверяется по подписи и сроку, после чего пользователь
 * перечитывается из БД: заблокированные учётные записи и смена роли
 * действуют сразу, не дожидаясь истечения токена.
 */
public class AuthService {
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDAO userDAO = new UserDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final AuditDAO auditDAO = new AuditDAO();

    // ================== Регистрация ==================

    public Map<String, Object> register(Map<String, Object> body) {
        String login = Json.getString(body, "login");
        String password = Json.getString(body, "password");
        String fullName = Json.getString(body, "fullName");
        String phone = Json.getString(body, "phone");
        String email = Json.getString(body, "email");

        if (login == null || password == null || fullName == null || phone == null) {
            throw ApiError.badRequest("Обязательные поля: login, password, fullName, phone");
        }
        login = login.trim();
        fullName = fullName.trim();
        phone = phone.trim();
        if (email != null) email = email.trim();

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
        if (userDAO.findByLogin(login) != null) {
            throw ApiError.conflict("Логин уже занят");
        }
        if (clientDAO.phoneExists(phone)) {
            throw ApiError.conflict("Телефон уже зарегистрирован");
        }
        if (email != null && !email.isBlank() && clientDAO.emailExists(email)) {
            throw ApiError.conflict("E-mail уже зарегистрирован");
        }

        // Пользователь и профиль клиента создаются в одной транзакции.
        int userId;
        try (Connection conn = DatabaseConnector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String hash = PasswordUtils.hashNewPassword(password);
                try (var stmt = conn.prepareStatement(
                        "INSERT INTO users (login, password, full_name, role) "
                                + "VALUES (?, ?, ?, 'CLIENT') RETURNING id")) {
                    stmt.setString(1, login);
                    stmt.setString(2, hash);
                    stmt.setString(3, fullName);
                    try (var rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new DaoException("Не удалось создать пользователя");
                        userId = rs.getInt(1);
                    }
                }
                try (var stmt = conn.prepareStatement(
                        "INSERT INTO clients (user_id, phone, email, full_name) VALUES (?, ?, ?, ?)")) {
                    stmt.setInt(1, userId);
                    stmt.setString(2, phone);
                    stmt.setString(3, (email == null || email.isBlank()) ? null : email);
                    stmt.setString(4, fullName);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("clients_phone_key")) {
                throw ApiError.conflict("Телефон уже зарегистрирован");
            }
            throw new DaoException("Ошибка при регистрации", e);
        }

        auditDAO.log(userId, "REGISTER", "USER", userId, "Зарегистрирован пользователь " + login);
        User user = userDAO.findById(userId);
        return tokenResponse(user, "Регистрация выполнена");
    }

    // ================== Вход ==================

    public Map<String, Object> login(Map<String, Object> body) {
        String login = Json.getString(body, "login");
        String password = Json.getString(body, "password");
        if (login == null || password == null) {
            throw ApiError.badRequest("Укажите login и password");
        }
        User user = userDAO.verifyLogin(login.trim(), password);
        if (user == null) {
            User existing = userDAO.findByLogin(login.trim());
            int id = existing == null ? null : existing.getId();
            auditDAO.log(id, "LOGIN_FAILED", "USER", id, "Неудачная попытка входа: " + login);
            throw ApiError.unauthorized("Неверный логин или пароль");
        }
        if (user.isBlocked()) {
            auditDAO.log(user.getId(), "LOGIN_BLOCKED", "USER", user.getId(),
                    "Попытка входа в заблокированную учётную запись " + login);
            throw ApiError.forbidden("Учётная запись заблокирована. Обратитесь к администратору.");
        }
        auditDAO.log(user.getId(), "LOGIN", null, null, "Вход в систему: " + login);
        return tokenResponse(user, "Вход выполнен");
    }

    private Map<String, Object> tokenResponse(User user, String message) {
        String token = JwtUtil.createToken(user.getId(), user.getLogin(), user.getRole());
        return Json.obj(
                "message", message,
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", JwtUtil.TOKEN_TTL_SECONDS,
                "user", userJson(user));
    }

    // ================== Текущий пользователь ==================

    /**
     * Проверяет JWT и возвращает актуального пользователя из БД.
     * Бросает 401/403 при проблемах. Роль и блокировка проверяются
     * по данным из БД, а не из токена.
     */
    public User authenticate(ApiContext ctx) {
        String header = ctx.exchange().getRequestHeaders().getFirst("Authorization");
        if (header == null || header.isBlank()) {
            throw ApiError.unauthorized("Требуется заголовок Authorization: Bearer <token>");
        }
        String token = header.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        Claims claims = JwtUtil.verifyToken(token);
        if (claims == null) {
            throw ApiError.unauthorized("Токен недействителен или истёк. Войдите снова.");
        }
        User user = userDAO.findById(claims.userId);
        if (user == null) {
            throw ApiError.unauthorized("Пользователь не найден");
        }
        if (user.isBlocked()) {
            throw ApiError.forbidden("Учётная запись заблокирована. Обратитесь к администратору.");
        }
        return user;
    }

    // ================== Проверки прав ==================

    public void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw ApiError.forbidden("Действие доступно только администратору");
        }
    }

    public void requireStaff(User user) {
        if (!user.isStaff()) {
            throw ApiError.forbidden("Действие доступно сотруднику бюро (менеджер или администратор)");
        }
    }

    // ================== JSON пользователя ==================

    public static Map<String, Object> userJson(User user) {
        return Json.obj(
                "id", user.getId(),
                "login", user.getLogin(),
                "fullName", user.getFullName(),
                "role", user.getRole(),
                "blocked", user.isBlocked());
    }

    public static Map<String, Object> clientJson(Client client) {
        return Json.obj(
                "id", client.getId(),
                "userId", client.getUserId(),
                "phone", client.getPhone(),
                "email", client.getEmail(),
                "fullName", client.getFullName());
    }
}
