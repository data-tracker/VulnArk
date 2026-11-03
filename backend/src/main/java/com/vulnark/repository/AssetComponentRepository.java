package com.vulnark.repository;

import com.vulnark.entity.AssetComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AssetComponentRepository extends JpaRepository<AssetComponent, Long> {
    List<AssetComponent> findByAssetId(Long assetId);
    Optional<AssetComponent> findByAssetIdAndComponentNameAndComponentVersion(
            Long assetId, String componentName, String componentVersion);
    List<AssetComponent> findByComponentName(String componentName);
}
