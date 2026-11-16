package models;

public class Client {
    private int id;
    private int userId;
    private String phone;
    private String email;
    private String fullName;

    public Client(int id, int userId, String phone, String email, String fullName) {
        this.id = id;
        this.userId = userId;
        this.phone = phone;
        this.email = email;
        this.fullName = fullName;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
}