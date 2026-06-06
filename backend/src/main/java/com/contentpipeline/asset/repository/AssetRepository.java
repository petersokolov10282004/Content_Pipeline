package com.contentpipeline.asset.repository;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.domain.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Asset> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, AssetStatus status);
}
