package com.vulnark.controller;

import com.vulnark.common.ApiResponse;
import com.vulnark.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置（仅管理员）：全局 KV 配置与 SMTP 测试
 */
@Tag(name = "系统设置", description = "系统级配置管理（仅管理员）")
@RestController
@RequestMapping("/system-configs")
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @Operation(summary = "获取全部系统配置", description = "按 category 分组返回")
    @GetMapping
    public ApiResponse<Map<String, Map<String, String>>> getAll() {
        try {
            return ApiResponse.success("获取系统配置成功", systemConfigService.getAllGrouped());
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "批量保存系统配置")
    @PutMapping
    public ApiResponse<Void> saveAll(@RequestBody Map<String, String> configs) {
        try {
            systemConfigService.saveBatch(configs);
            return ApiResponse.success("系统配置保存成功", null);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "发送测试邮件", description = "使用传入的 SMTP 配置发送测试邮件，验证配置有效性（不落库）")
    @PostMapping("/smtp/test")
    public ApiResponse<Void> sendTestMail(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> smtp = (Map<String, String>) request.get("smtp");
            String to = (String) request.get("to");
            if (to == null || to.trim().isEmpty()) {
                return ApiResponse.error("请填写测试收件邮箱");
            }
            systemConfigService.sendTestMail(smtp, to.trim());
            return ApiResponse.success("测试邮件发送成功，请查收", null);
        } catch (Exception e) {
            return ApiResponse.error("测试邮件发送失败: " + e.getMessage());
        }
    }
}
