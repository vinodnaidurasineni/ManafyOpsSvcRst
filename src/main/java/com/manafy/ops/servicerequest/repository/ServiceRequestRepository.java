package com.manafy.ops.servicerequest.repository;

import com.manafy.ops.servicerequest.entity.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRequestRepository
        extends JpaRepository<ServiceRequest, UUID>, JpaSpecificationExecutor<ServiceRequest> {

    Optional<ServiceRequest> findByIdAndDeletedFalse(UUID id);

    boolean existsByReferenceNo(String referenceNo);

    Page<ServiceRequest> findByDeletedFalse(Pageable pageable);
}
