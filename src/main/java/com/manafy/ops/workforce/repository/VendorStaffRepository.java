package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.VendorStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorStaffRepository extends JpaRepository<VendorStaff, UUID> {
    Optional<VendorStaff> findByIdAndDeletedFalse(UUID id);
    List<VendorStaff> findByVendorIdAndDeletedFalse(UUID vendorId);
}
