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
