package models;

import java.sql.Timestamp;

/** Запись журнала аудита. */
public class AuditEntry {
    private int id;
    private Integer userId;
    private String userLogin;
    private String action;
    private String entityType;
    private Integer entityId;
    private String details;
    private Timestamp createdAt;

    public AuditEntry(int id, Integer userId, String userLogin, String action,
                      String entityType, Integer entityId, String details, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.userLogin = userLogin;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public Integer getUserId() { return userId; }
    public String getUserLogin() { return userLogin; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Integer getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Timestamp getCreatedAt() { return createdAt; }
}
