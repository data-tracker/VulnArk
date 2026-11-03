package com.vulnark.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产组件清单（BOM）：同步时从 DTrack 静默积累，界面二期
 */
@Entity
@Table(name = "asset_components")
public class AssetComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "component_name", nullable = false, length = 120)
    private String componentName;

    @Column(name = "component_version", length = 60)
    private String componentVersion;

    @Column(name = "component_uuid", length = 64)
    private String componentUuid;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "last_seen")
    private LocalDate lastSeen;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long v) { this.assetId = v; }
    public String getComponentName() { return componentName; }
    public void setComponentName(String v) { this.componentName = v; }
    public String getComponentVersion() { return componentVersion; }
    public void setComponentVersion(String v) { this.componentVersion = v; }
    public String getComponentUuid() { return componentUuid; }
    public void setComponentUuid(String v) { this.componentUuid = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public LocalDate getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDate v) { this.lastSeen = v; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
}
