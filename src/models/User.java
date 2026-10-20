package  models;

public class User {
    private int id;
    private String login;
    private String password;
    private String fullName;
    private String role; // "ADMIN" или "CLIENT"

    public User(int id, String login, String password, String fullName, String role) {
        this.id = id;
        this.login = login;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public int getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
}