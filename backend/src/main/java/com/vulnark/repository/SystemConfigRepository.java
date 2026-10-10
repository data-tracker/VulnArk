package com.vulnark.repository;

import com.vulnark.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    List<SystemConfig> findAllByOrderByCategoryAscConfigKeyAsc();

    List<SystemConfig> findByCategoryOrderByConfigKeyAsc(String category);

    Optional<SystemConfig> findByConfigKey(String configKey);
}
