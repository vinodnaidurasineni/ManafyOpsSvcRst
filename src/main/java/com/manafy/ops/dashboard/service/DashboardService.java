package com.manafy.ops.dashboard.service;

import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.complaint.repository.OpsComplaintRepository;
import com.manafy.ops.dashboard.dto.DashboardDtos.DashboardSummary;
import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.workforce.repository.EmployeeRepository;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ops dashboard aggregate (customer count + operational counts). Read-only.
 *
 * Gated by REPORT_VIEW (permission-based, like every other Ops read). The customer
 * count is fetched from Community (system of record) and degrades gracefully; all
 * other counts are Ops-owned so they always resolve.
 */
@Service
public class DashboardService {

    private final AuthorizationService authz;
    private final HelperRepository helperRepo;
    private final EmployeeRepository employeeRepo;
    private final OpsComplaintRepository complaintRepo;
    private final ManualAssignmentRequestRepository manualRepo;
    private final CommunityCustomerClient communityCustomerClient;

    public DashboardService(AuthorizationService authz, HelperRepository helperRepo,
                            EmployeeRepository employeeRepo, OpsComplaintRepository complaintRepo,
                            ManualAssignmentRequestRepository manualRepo,
                            CommunityCustomerClient communityCustomerClient) {
        this.authz = authz;
        this.helperRepo = helperRepo;
        this.employeeRepo = employeeRepo;
        this.complaintRepo = complaintRepo;
        this.manualRepo = manualRepo;
        this.communityCustomerClient = communityCustomerClient;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID actor) {
        authz.requirePermission(actor, "REPORT_VIEW");

        Long totalCustomers = communityCustomerClient.fetchCustomerCount();
        long activeHelpers = helperRepo.countByStatusAndDeletedFalse("ACTIVE");
        long totalHelpers = helperRepo.countByDeletedFalse();
        long activeEmployees = employeeRepo.countByStatusAndDeletedFalse("ACTIVE");
        long openComplaints = complaintRepo.countByStatusAndDeletedFalse("OPEN");
        long openRequests = manualRepo.countOpen();

        return new DashboardSummary(
                totalCustomers, totalCustomers != null,
                activeHelpers, totalHelpers, activeEmployees, openComplaints, openRequests);
    }
}
