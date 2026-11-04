package com.vulnark.controller;

import com.vulnark.common.ApiResponse;
import com.vulnark.entity.AuditLog;
import com.vulnark.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit")
@Tag(name = "审计日志", description = "操作审计查询（只读）")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Operation(summary = "漏洞操作历史（时间线）")
    @GetMapping("/vuln/{vulnId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AuditLog>> vulnTimeline(@PathVariable Long vulnId) {
        return ApiResponse.success("获取成功", auditService.getVulnTimeline(vulnId));
    }

    @Operation(summary = "全局审计流（ADMIN）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action) {
        var result = auditService.query(page, size, userId, action);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("content", result.getContent());
        out.put("totalElements", result.getTotalElements());
        return ApiResponse.success("获取成功", out);
    }
}
