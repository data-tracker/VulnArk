package com.vulnark.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 操作审计日志（只增不改）
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 60)
    private String userName;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "object_type", nullable = false, length = 20)
    private String objectType;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "object_label", length = 120)
    private String objectLabel;

    @Column(length = 40)
    private String field;

    @Column(name = "old_value", length = 200)
    private String oldValue;

    @Column(name = "new_value", length = 200)
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getUserName() { return userName; }
    public void setUserName(String v) { this.userName = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String v) { this.objectType = v; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long v) { this.objectId = v; }
    public String getObjectLabel() { return objectLabel; }
    public void setObjectLabel(String v) { this.objectLabel = v; }
    public String getField() { return field; }
    public void setField(String v) { this.field = v; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String v) { this.oldValue = v; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String v) { this.newValue = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { this.ip = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
