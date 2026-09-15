package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.WorkforceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkforceStatusHistoryRepository extends JpaRepository<WorkforceStatusHistory, UUID> {
    List<WorkforceStatusHistory> findByWorkforceKindAndWorkforceIdOrderByCreatedAtAsc(String kind, UUID workforceId);
}
