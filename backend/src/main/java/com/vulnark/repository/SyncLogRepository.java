package com.vulnark.repository;

import com.vulnark.entity.SyncLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findAllByOrderBySyncTimeDesc(Pageable pageable);

    Optional<SyncLog> findFirstByTriggerTypeOrderBySyncTimeDesc(String triggerType);
}
