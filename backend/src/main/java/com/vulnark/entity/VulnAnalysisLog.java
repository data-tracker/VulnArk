package com.vulnark.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 漏洞分析状态变更审计日志
 */
@Entity
@Table(name = "vuln_analysis_log")
public class VulnAnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vuln_id", nullable = false)
    private Long vulnId;

    @Column(name = "cve_id", length = 20)
    private String cveId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "old_status", length = 20)
    private String oldStatus;   // null = 未分析

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt = LocalDateTime.now();

    public VulnAnalysisLog() {}

    public VulnAnalysisLog(Long vulnId, String cveId, Long assetId,
                           Vulnerability.VerificationStatus oldStatus,
                           Vulnerability.VerificationStatus newStatus, Long changedBy) {
        this.vulnId = vulnId;
        this.cveId = cveId;
        this.assetId = assetId;
        this.oldStatus = oldStatus == null ? null : oldStatus.name();
        this.newStatus = newStatus == null ? "NONE" : newStatus.name();  // NONE=改回未分析
        this.changedBy = changedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVulnId() { return vulnId; }
    public void setVulnId(Long vulnId) { this.vulnId = vulnId; }
    public String getCveId() { return cveId; }
    public void setCveId(String cveId) { this.cveId = cveId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
