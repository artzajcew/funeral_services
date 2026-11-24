package ru.memory.backend.entity;

import jakarta.persistence.*;

/**
 * Пользователь системы. Роли:
 *   ADMIN   — полный доступ (архив, массовые операции, CSV,
 *             аналитика, управление пользователями, аудит);
 *   MANAGER — сотрудник бюро (работа со всеми заказами);
 *   CLIENT  — клиент (только свои заказы).
 */
@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_CLIENT = "CLIENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String login;

    /** Хеш пароля: PBKDF2 (новые) или legacy SHA-256 (из data.sql). */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String role = ROLE_CLIENT;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private Client client;

    public User() {
    }

    public User(String login, String password, String fullName, String role) {
        this.login = login;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isStaff() {
        return ROLE_ADMIN.equals(role) || ROLE_MANAGER.equals(role);
    }
}
