package models;

/**
 * Пользователь системы. Роли:
 *   ADMIN   — полный доступ (архив, массовые операции, CSV,
 *             аналитика, управление пользователями, аудит);
 *   MANAGER — сотрудник бюро (работа со всеми заказами);
 *   CLIENT  — клиент (только свои заказы).
 */
public class User {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_CLIENT = "CLIENT";

    private int id;
    private String login;
    private String password;
    private String fullName;
    private String role;
    private boolean blocked;

    /** Совместимость с прежним кодом (консольное меню). */
    public User(int id, String login, String password, String fullName) {
        this(id, login, password, fullName, ROLE_CLIENT, false);
    }

    public User(int id, String login, String password, String fullName,
                String role, boolean blocked) {
        this.id = id;
        this.login = login;
        this.password = password;
        this.fullName = fullName;
        this.role = role == null ? ROLE_CLIENT : role;
        this.blocked = blocked;
    }

    public int getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isBlocked() { return blocked; }

    public boolean isAdmin() { return ROLE_ADMIN.equals(role); }
    public boolean isManager() { return ROLE_MANAGER.equals(role); }
    public boolean isClient() { return ROLE_CLIENT.equals(role); }

    public boolean isStaff() { return isAdmin() || isManager(); }
}
