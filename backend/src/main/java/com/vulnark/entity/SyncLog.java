package com.vulnark.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 数据同步日志（Dependency-Track 定期同步）
 */
@Entity
@Table(name = "sync_logs")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_time", nullable = false)
    private LocalDateTime syncTime = LocalDateTime.now();

    /** AUTO（定时）/ MANUAL（手动） */
    @Column(name = "trigger_type", nullable = false, length = 10)
    private String triggerType = "MANUAL";

    private int added;
    private int updated;
    private int resolved;
    private int failed;

    /** 明细 JSON（各映射的新增/更新/移除/失败与错误信息） */
    @Column(columnDefinition = "TEXT")
    private String detail;

    public Long getId() { return id; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime t) { this.syncTime = t; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String t) { this.triggerType = t; }
    public int getAdded() { return added; }
    public void setAdded(int v) { this.added = v; }
    public int getUpdated() { return updated; }
    public void setUpdated(int v) { this.updated = v; }
    public int getResolved() { return resolved; }
    public void setResolved(int v) { this.resolved = v; }
    public int getFailed() { return failed; }
    public void setFailed(int v) { this.failed = v; }
    public String getDetail() { return detail; }
    public void setDetail(String d) { this.detail = d; }
}
