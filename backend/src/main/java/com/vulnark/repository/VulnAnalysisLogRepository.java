package com.vulnark.repository;

import com.vulnark.entity.VulnAnalysisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface VulnAnalysisLogRepository extends JpaRepository<VulnAnalysisLog, Long> {

    List<VulnAnalysisLog> findByChangedAtAfter(LocalDateTime since);

    // 统计某时段新完成分析（从 NULL → 任意状态）的记录
    @org.springframework.data.jpa.repository.Query("SELECT l FROM VulnAnalysisLog l WHERE l.changedAt >= :since AND l.oldStatus IS NULL")
    List<VulnAnalysisLog> findNewlyAnalyzedSince(@org.springframework.lang.NonNull LocalDateTime since);
}
