package com.manafy.ops.gate.repository;

import com.manafy.ops.gate.entity.StaffPass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffPassRepository extends JpaRepository<StaffPass, UUID> {
    List<StaffPass> findByApartmentIdAndStaffMobileAndDeletedFalse(UUID apartmentId, String staffMobile);
}
