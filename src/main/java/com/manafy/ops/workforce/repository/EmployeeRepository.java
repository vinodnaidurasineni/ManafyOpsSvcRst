package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByIdAndDeletedFalse(UUID id);
    boolean existsByEmployeeCode(String employeeCode);
    Page<Employee> findByDeletedFalse(Pageable pageable);
    List<Employee> findByEmployeeTypeAndDeletedFalse(String employeeType);
}
