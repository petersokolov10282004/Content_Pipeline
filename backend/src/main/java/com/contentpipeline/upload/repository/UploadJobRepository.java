package com.contentpipeline.upload.repository;

import com.contentpipeline.upload.domain.UploadJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {}
