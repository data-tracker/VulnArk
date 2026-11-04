package com.vulnark.service;

import com.vulnark.entity.AuditLog;
import com.vulnark.entity.User;
import com.vulnark.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志：写入辅助（只增不改）与查询
 */
@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /** 便捷写入：漏洞对象变更（带批次 ID） */
    public void logVulnChange(User operator, String action, Long vulnId, String cveId,
                               String field, String oldValue, String newValue, String batchId) {
        AuditLog log = base(operator, action, "VULNERABILITY", vulnId, cveId);
        log.setBatchId(batchId);
        log.setField(field);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditLogRepository.save(log);
    }

    /** 兼容旧调用（无批次） */
    public void logVulnChange(User operator, String action, Long vulnId, String cveId,
                               String field, String oldValue, String newValue) {
        logVulnChange(operator, action, vulnId, cveId, field, oldValue, newValue, null);
    }

    /** 批量操作汇总（明细清单放 detail JSON） */
    public void logBatch(User operator, String action, String batchAction,
                         List<AuditLog> itemLogs, String summaryLabel) {
        AuditLog log = base(operator, batchAction, "BATCH", null, summaryLabel);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < itemLogs.size(); i++) {
            AuditLog it = itemLogs.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"cve\":\"").append(it.getObjectLabel()).append("\",\"old\":\"")
              .append(it.getOldValue() == null ? "" : it.getOldValue()).append("\",\"new\":\"")
              .append(it.getNewValue() == null ? "" : it.getNewValue()).append("\"}");
        }
        sb.append("]");
        log.setDetail(sb.toString());
        auditLogRepository.save(log);
        // 散条（每条漏洞的时间线各得一条）
        auditLogRepository.saveAll(itemLogs);
    }

    /** 资产操作 */
    public void logAsset(User operator, String action, Long assetId, String assetName,
                          String field, String oldValue, String newValue) {
        AuditLog log = base(operator, action, "ASSET", assetId, assetName);
        log.setField(field);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditLogRepository.save(log);
    }

    private AuditLog base(User operator, String action, String objType, Long objId, String label) {
        AuditLog log = new AuditLog();
        if (operator != null) {
            log.setUserId(operator.getId());
            log.setUserName(operator.getFullName() != null ? operator.getFullName() : operator.getUsername());
        } else {
            log.setUserId(0L);
            log.setUserName("SYSTEM");
        }
        log.setAction(action);
        log.setObjectType(objType);
        log.setObjectId(objId);
        log.setObjectLabel(label);
        return log;
    }

    public List<AuditLog> getVulnTimeline(Long vulnId) {
        return auditLogRepository.findByObjectTypeAndObjectIdOrderByCreatedAtDesc("VULNERABILITY", vulnId);
    }

    public Page<AuditLog> query(int page, int size, Long userId, String action) {
        PageRequest p = PageRequest.of(page, size);
        if (userId != null) return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, p);
        if (action != null && !action.isEmpty()) return auditLogRepository.findByActionOrderByCreatedAtDesc(action, p);
        return auditLogRepository.findByOrderByCreatedAtDesc(p);
    }
}
