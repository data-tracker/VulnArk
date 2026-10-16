package com.vulnark.controller;

import com.vulnark.common.ApiResponse;
import com.vulnark.entity.SyncLog;
import com.vulnark.repository.SyncLogRepository;
import com.vulnark.service.DTrackSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Dependency-Track 数据同步（仅管理员）
 */
@Tag(name = "数据同步", description = "Dependency-Track 定期同步（仅管理员）")
@RestController
@RequestMapping("/sync")
@PreAuthorize("hasRole('ADMIN')")
public class SyncController {

    @Autowired
    private DTrackSyncService syncService;

    @Autowired
    private SyncLogRepository syncLogRepo;

    @Operation(summary = "测试 DTrack 连接")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection() {
        return ApiResponse.success("测试完成", syncService.testConnection());
    }

    @Operation(summary = "触发同步（异步）")
    @PostMapping("/run")
    public ApiResponse<Void> run() {
        try {
            syncService.triggerManual();
            return ApiResponse.success("同步已启动，请稍后查看结果", null);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "同步状态（运行中标志 + 最近日志）")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> r = new HashMap<>();
        r.put("running", syncService.isRunning());
        r.put("configured", syncService.isConfigured());
        List<SyncLog> logs = syncLogRepo.findAllByOrderBySyncTimeDesc(PageRequest.of(0, 10));
        r.put("logs", logs);
        return ApiResponse.success("获取成功", r);
    }

    @Operation(summary = "仪表板同步概要", description = "上次同步结果 + Band 告警 + 按资产统计（供仪表板展示）")
    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> dashboardSummary() {
        try {
            Map<String, Object> result = new HashMap<>();
            var logs = syncLogRepo.findAllByOrderBySyncTimeDesc(PageRequest.of(0, 1));
            if (logs.isEmpty()) {
                result.put("lastSync", null);
                result.put("bandAlerts", List.of());
                result.put("perAsset", List.of());
                return ApiResponse.success("获取成功", result);
            }
            var last = logs.get(0);

            Map<String, Object> lastSync = new HashMap<>();
            lastSync.put("time", last.getSyncTime().toString());
            lastSync.put("triggerType", last.getTriggerType());
            lastSync.put("added", last.getAdded());
            lastSync.put("updated", last.getUpdated());
            lastSync.put("resolved", last.getResolved());
            lastSync.put("failed", last.getFailed());
            result.put("lastSync", lastSync);

            // 解析 detail 获取 Band 告警和按资产统计
            List<Map<String, Object>> bandAlerts = new ArrayList<>();
            List<Map<String, Object>> perAsset = new ArrayList<>();
            try {
                com.fasterxml.jackson.databind.JsonNode detail =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(last.getDetail());
                // Band 告警（只取升级到 EMERGENCY/CRITICAL 的）
                var enrich = detail.path("enrich").path("bandRecalcedList");
                for (var b : enrich) {
                    String to = b.path("to").asText("");
                    if ("EMERGENCY".equals(to) || "CRITICAL".equals(to)) {
                        Map<String, Object> alert = new HashMap<>();
                        alert.put("cve", b.path("cve").asText());
                        alert.put("asset", b.path("asset").asText("-"));
                        alert.put("from", b.path("from").asText());
                        alert.put("to", to);
                        alert.put("reason", b.path("reason").asText("-"));
                        bandAlerts.add(alert);
                    }
                }
                // 按资产统计
                var assets = detail.path("assets");
                var fields = java.util.Iterator.class.getMethod("next");
                assets.fields().forEachRemaining(e -> {
                    Map<String, Object> pa = new HashMap<>();
                    pa.put("asset", e.getKey());
                    pa.put("added", e.getValue().path("added").size());
                    pa.put("updated", e.getValue().path("updated").size());
                    pa.put("resolved", e.getValue().path("resolved").size());
                    perAsset.add(pa);
                });
            } catch (Exception ignored) {}

            result.put("bandAlerts", bandAlerts);
            result.put("perAsset", perAsset);
            return ApiResponse.success("获取成功", result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "DTrack 项目名列表（用于同名匹配）")
    @GetMapping("/dtrack-projects")
    public ApiResponse<List<String>> dtrackProjects() {
        try {
            return ApiResponse.success("获取成功", syncService.listDtrackProjects());
        } catch (Exception e) {
            return ApiResponse.error("获取 DTrack 项目失败: " + e.getMessage());
        }
    }
}
