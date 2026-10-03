package com.manafy.ops.common.security.dev;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.manafy.ops.common.authz.entity.RolePermission;
import com.manafy.ops.common.authz.entity.UserRole;
import com.manafy.ops.common.authz.entity.UserScope;
import com.manafy.ops.common.authz.repository.PermissionRepository;
import com.manafy.ops.common.authz.repository.RolePermissionRepository;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.authz.repository.UserRoleRepository;
import com.manafy.ops.common.authz.repository.UserScopeRepository;
import com.manafy.ops.complaint.entity.OpsComplaint;
import com.manafy.ops.complaint.repository.OpsComplaintRepository;
import com.manafy.ops.compat.entity.HelperTag;
import com.manafy.ops.compat.repository.HelperTagRepository;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;

/**
 * DEV-ONLY seeder. On startup (when {@code manafy.dev-auth.enabled=true}), it
 * provisions a handful of ops users with distinct mobile numbers, distinct roles,
 * and GLOBAL scope so the mobile app can be driven end-to-end without Cognito.
 *
 * <p>Each user's Cognito subject is deterministic ({@code dev-sub-<mobile>}) so the
 * dev login can mint a token whose {@code sub} resolves to the seeded user. It also
 * grants the OPERATIONS assignment permissions to the roles these users hold, since
 * the foundation catalog does not yet map ASSIGNMENT_* to FIELD_OFFICER etc.
 *
 * <p>Fully idempotent — safe to run on every boot. Inert in production.
 */
@Component
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    /**
     * Permissions the dev users need to exercise the full Ops mobile flow end-to-end:
     * the manual-request queue (ASSIGNMENT_*), the complaints module (COMPLAINT_*),
     * the workforce/maid directory + onboarding (HELPER_*, EMPLOYEE_*), and the
     * dashboard customer-count/summary (REPORT_VIEW). Granted to each dev role
     * (except SUPER_ADMIN, which already holds everything via the catalog seed).
     */
    private static final List<String> OPERATIONS_PERMISSIONS = List.of(
            "ASSIGNMENT_VIEW", "ASSIGNMENT_CREATE", "ASSIGNMENT_REASSIGN", "ASSIGNMENT_CANCEL",
            "COMPLAINT_VIEW", "COMPLAINT_UPDATE",
            "HELPER_VIEW", "HELPER_CREATE", "HELPER_UPDATE",
            "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_UPDATE",
            "REPORT_VIEW", "REGION_VIEW", "AREA_VIEW");

    /** The dev accounts. Mobile numbers are distinct from the Community app's. */
    public record DevAccount(String mobile, String name, String roleCode) {}

    private static final List<DevAccount> ACCOUNTS = List.of(
            // Original ops dev personas (kept working — no "no dev account" error).
            new DevAccount("7000000001", "Dev Field Officer", "FIELD_OFFICER"),
            new DevAccount("7000000002", "Dev Ops Coordinator", "OPERATIONS_COORDINATOR"),
            new DevAccount("7000000003", "Dev Area Manager", "AREA_OPERATIONS_MANAGER"),
            new DevAccount("7000000004", "Dev Super Admin", "SUPER_ADMIN"),
            // Old ManafyOps management hierarchy (ported from ManafySvcRst
            // deploy/seed-dev-hierarchy.sql). Mapped to ops roles so they
            // authenticate here and land on the Ops admin app. Management tiers
            // get SUPER_ADMIN (full visibility); field/area roles map to the
            // closest ops operational role.
            new DevAccount("9999900001", "Vinod Kumar", "SUPER_ADMIN"),            // CEO
            new DevAccount("9999900002", "Rahul Sharma", "SUPER_ADMIN"),           // COO
            new DevAccount("9999900003", "Priya Nair", "SUPER_ADMIN"),             // HR
            new DevAccount("9999900004", "Kiran Reddy", "AREA_OPERATIONS_MANAGER"),// Area Manager
            new DevAccount("9999900005", "Arun Patel", "OPERATIONS_COORDINATOR"),  // Training Team
            new DevAccount("9999900006", "Sneha Iyer", "OPERATIONS_COORDINATOR")); // Onboarding Assistant

    public static String subForMobile(String mobile) {
        return "dev-sub-" + mobile;
    }

    private final DevAuthProperties props;
    private final OpsUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserScopeRepository userScopeRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final ManualAssignmentRequestRepository manualRepo;
    private final HelperRepository helperRepo;
    private final OpsComplaintRepository complaintRepo;
    private final HelperTagRepository helperTagRepo;

    public DevUserSeeder(DevAuthProperties props, OpsUserRepository userRepo, RoleRepository roleRepo,
                         UserRoleRepository userRoleRepo, UserScopeRepository userScopeRepo,
                         PermissionRepository permissionRepo, RolePermissionRepository rolePermissionRepo,
                         ManualAssignmentRequestRepository manualRepo, HelperRepository helperRepo,
                         OpsComplaintRepository complaintRepo, HelperTagRepository helperTagRepo) {
        this.props = props;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.userScopeRepo = userScopeRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.manualRepo = manualRepo;
        this.helperRepo = helperRepo;
        this.complaintRepo = complaintRepo;
        this.helperTagRepo = helperTagRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) return;

        // Ensure the roles these users hold can actually use the manual-request queue.
        for (DevAccount acct : ACCOUNTS) {
            grantOperationsPermissionsToRole(acct.roleCode());
        }

        for (DevAccount acct : ACCOUNTS) {
            OpsUser user = ensureUser(acct);
            ensureRole(user.getId(), acct.roleCode());
            ensureGlobalScope(user.getId());
        }

        seedHelpers();
        seedSampleRequests();
        seedWorkloadRequests();
        seedComplaints();
        seedHelperTags();

        log.warn("DEV-AUTH ENABLED: seeded {} local login accounts (mobiles 7000000001..04, OTP={}), "
                + "helper workforce, sample requests, and sample complaints. This must NEVER be enabled in production.",
                ACCOUNTS.size(), props.getOtp());
    }

    // ─── Dummy helper workforce ──────────────────────────────────────

    /** A dummy helper definition. Phones are clearly fake dev numbers (99000xxxxx). */
    private record DevHelper(String code, String name, String category, String phone,
                             String status, String availability) {}

    /**
     * Seed a realistic dummy workforce across service categories. These are REAL
     * helper rows (created via the normal repository), NOT special-cased in
     * assignment logic — the assignment algorithm treats them like any helper.
     * Idempotent via the unique helper code.
     */
    private static final List<DevHelper> HELPERS = List.of(
            // MAID — several active/available with different names for ordering demos.
            new DevHelper("H-MAID-01", "Anita Sharma", "MAID", "9900010001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-MAID-02", "Priya Das", "MAID", "9900010002", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-MAID-03", "Kavita Singh", "MAID", "9900010003", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-MAID-04", "Sunita Reddy", "MAID", "9900010004", "ACTIVE", "UNAVAILABLE"),
            // COOK
            new DevHelper("H-COOK-01", "Ramesh Gupta", "COOK", "9900020001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-COOK-02", "Deepa Nair", "COOK", "9900020002", "ACTIVE", "AVAILABLE"),
            // PLUMBER
            new DevHelper("H-PLUMB-01", "Ravi Kumar", "PLUMBER", "9900030001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-PLUMB-02", "Suresh Kumar", "PLUMBER", "9900030002", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-PLUMB-03", "Mahesh Kumar", "PLUMBER", "9900030003", "ACTIVE", "AVAILABLE"),
            // ELECTRICIAN
            new DevHelper("H-ELEC-01", "Vijay Rao", "ELECTRICIAN", "9900040001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-ELEC-02", "Arjun Mehta", "ELECTRICIAN", "9900040002", "ACTIVE", "AVAILABLE"),
            // CLEANER
            new DevHelper("H-CLEAN-01", "Lakshmi Devi", "CLEANER", "9900050001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-CLEAN-02", "Geeta Bai", "CLEANER", "9900050002", "ACTIVE", "AVAILABLE"),
            // CARPENTER
            new DevHelper("H-CARP-01", "Manoj Yadav", "CARPENTER", "9900060001", "ACTIVE", "AVAILABLE"),
            new DevHelper("H-CARP-02", "Prakash Jha", "CARPENTER", "9900060002", "ACTIVE", "AVAILABLE"));

    private void seedHelpers() {
        for (DevHelper h : HELPERS) {
            if (helperRepo.existsByCode(h.code())) continue;
            Helper e = new Helper();
            e.setCode(h.code());
            e.setName(h.name());
            e.setCategory(h.category());
            e.setPhone(h.phone());
            e.setRelationship("MANAFY");
            e.setStatus(h.status());
            e.setAvailabilityStatus(h.availability());
            helperRepo.save(e);
        }
    }

    /**
     * Give a few MAID helpers different assignment counts FOR TODAY so the
     * lowest-daily-workload ordering is visibly demonstrable. These are real
     * COMPLETED manual-request rows dated today pointing at seeded helpers by id.
     * Idempotent via fixed DEV-WL-* source ids.
     *   Anita (H-MAID-01) → 1 today, Priya (H-MAID-02) → 3, Kavita (H-MAID-03) → 5
     * so a new MAID request selects Anita first (lowest workload, available).
     */
    private void seedWorkloadRequests() {
        seedWorkloadFor("H-MAID-01", "MAID", "Anita Sharma", "9900010001", 1);
        seedWorkloadFor("H-MAID-02", "MAID", "Priya Das", "9900010002", 3);
        seedWorkloadFor("H-MAID-03", "MAID", "Kavita Singh", "9900010003", 5);
    }

    private void seedWorkloadFor(String helperCode, String category, String helperName,
                                 String helperPhone, int countToday) {
        Helper helper = helperRepo.findByCategoryAndDeletedFalse(category).stream()
                .filter(h -> helperCode.equals(h.getCode())).findFirst().orElse(null);
        if (helper == null) return;
        String helperId = helper.getId().toString();
        for (int i = 1; i <= countToday; i++) {
            String sourceId = "WL-" + helperCode + "-" + i;
            if (manualRepo.findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
                    "DEV", "RECURRING_HELPER_REQUEST", sourceId).isPresent()) {
                continue;
            }
            ManualAssignmentRequest r = new ManualAssignmentRequest();
            r.setReferenceNo("MR-" + sourceId);
            r.setSourceSystem("DEV");
            r.setSourceType("RECURRING_HELPER_REQUEST");
            r.setSourceId(sourceId);
            r.setServiceType(category);
            r.setServiceTitle("Recurring " + category.charAt(0) + category.substring(1).toLowerCase());
            r.setFrequency("DAILY");
            r.setStartDate(LocalDate.now());     // TODAY → counts toward today's workload
            r.setTimeSlot("MORNING");
            r.setServiceAddress("Prior assignment (seed)");
            r.setResidentName("Prior Resident " + i);
            r.setContactNumber("9845000000");
            r.setPaymentStatus("NONE");
            r.setPriority("MEDIUM");
            r.setStatus("COMPLETED");            // done, but still counts as today's work
            r.setAssigneeType("OPS_WORKFORCE");
            r.setAssigneeRef(helperId);
            r.setAssigneeName(helperName);
            r.setAssigneePhone(helperPhone);
            manualRepo.save(r);
        }
    }

    /**
     * Seed a few sample manual requests so the queue isn't empty when testing the
     * app standalone (before any real Community handoff). Idempotent: each row has a
     * fixed DEV source id, so re-runs are skipped by the unique source constraint.
     * area_id/region_id are null → visible to the GLOBAL-scoped dev accounts.
     */
    private void seedSampleRequests() {
        sample("DEV-1", "QUEUED", "MAID", "Recurring Maid", "DAILY", "MORNING",
                "Anita Rao", "9845012345", "A-402, Prestige Lakeside, Whitefield", new BigDecimal("3500"), null, null);
        sample("DEV-2", "QUEUED", "COOK", "Recurring Cook", "DAILY", "EVENING",
                "Rahul Verma", "9845067890", "B-1203, Sobha Dream Acres, Panathur", new BigDecimal("6000"), null, null);
        sample("DEV-3", "ASSIGNED", "DRIVER", "Recurring Driver", "MONTHLY", "FULL_DAY",
                "Meera Iyer", "9845099887", "C-77, Brigade Gateway, Rajajinagar", new BigDecimal("18000"),
                "Suresh Kumar", "9845555111");
        sample("DEV-4", "IN_PROGRESS", "NANNY", "Recurring Nanny", "WEEKLY", "AFTERNOON",
                "Farah Khan", "9845033221", "D-9, Purva Highland, Kanakapura Rd", new BigDecimal("9000"),
                "Lakshmi Devi", "9845555222");
    }

    private void sample(String sourceId, String status, String serviceType, String title,
                        String frequency, String timeSlot, String residentName, String contact,
                        String address, BigDecimal amount, String assigneeName, String assigneePhone) {
        if (manualRepo.findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
                "DEV", "RECURRING_HELPER_REQUEST", sourceId).isPresent()) {
            return;
        }
        ManualAssignmentRequest r = new ManualAssignmentRequest();
        r.setReferenceNo("MR-" + sourceId);
        r.setSourceSystem("DEV");
        r.setSourceType("RECURRING_HELPER_REQUEST");
        r.setSourceId(sourceId);
        r.setServiceType(serviceType);
        r.setServiceTitle(title);
        r.setFrequency(frequency);
        r.setStartDate(LocalDate.now().plusDays(2));
        r.setTimeSlot(timeSlot);
        r.setServiceAddress(address);
        r.setResidentName(residentName);
        r.setContactNumber(contact);
        r.setAmount(amount);
        r.setPaymentStatus(amount != null ? "DUMMY_PAID" : "NONE");
        r.setPriority("MEDIUM");
        r.setStatus(status);
        if (assigneeName != null) {
            r.setAssigneeType("EXTERNAL_PERSON");
            r.setAssigneeName(assigneeName);
            r.setAssigneePhone(assigneePhone);
        }
        manualRepo.save(r);
    }

    // ─── Sample complaints (so the Complaints module isn't empty in dev) ──

    /**
     * Seed a few operational complaints spanning types/statuses so the Ops mobile
     * Complaints screen can be tested standalone. area_id/region_id are null →
     * visible to the GLOBAL-scoped dev accounts. Idempotent via fixed source ids.
     */
    private void seedComplaints() {
        complaint("DEV-C1", "MAID_NO_SHOW", "OPEN", "HIGH",
                "Maid did not turn up for the scheduled morning slot.",
                "Anita Rao", "9845012345", "Anita Sharma", "9900010001", "BK-1001");
        complaint("DEV-C2", "WORK_NOT_DONE", "IN_PROGRESS", "MEDIUM",
                "Kitchen was not cleaned properly during the last visit.",
                "Rahul Verma", "9845067890", "Priya Das", "9900010002", "BK-1002");
        complaint("DEV-C3", "MAID_LATE", "OPEN", "LOW",
                "Helper consistently arrives 30+ minutes late.",
                "Meera Iyer", "9845099887", null, null, "BK-1003");
        complaint("DEV-C4", "DAMAGE", "RESOLVED", "HIGH",
                "A ceramic dish was broken; resolved with replacement.",
                "Farah Khan", "9845033221", "Kavita Singh", "9900010003", "BK-1004");
        complaint("DEV-C5", "BILLING", "CLOSED", "MEDIUM",
                "Disputed an extra charge on the monthly invoice; refunded.",
                "Sanjay Gupta", "9845044556", null, null, "BK-1005");
    }

    private void complaint(String sourceId, String type, String status, String priority, String description,
                           String customerName, String customerMobile,
                           String assigneeName, String assigneeMobile, String bookingRef) {
        if (complaintRepo.findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
                "DEV", "SOCIETY_COMPLAINT", sourceId).isPresent()) {
            return;
        }
        OpsComplaint c = new OpsComplaint();
        c.setReferenceNo("CMP-" + sourceId);
        c.setSourceSystem("DEV");
        c.setSourceType("SOCIETY_COMPLAINT");
        c.setSourceId(sourceId);
        c.setComplaintType(type);
        c.setStatus(status);
        c.setPriority(priority);
        c.setDescription(description);
        c.setCustomerName(customerName);
        c.setCustomerMobile(customerMobile);
        c.setAssigneeName(assigneeName);
        c.setAssigneeMobile(assigneeMobile);
        c.setBookingReference(bookingRef);
        if ("RESOLVED".equals(status)) c.setResolvedAt(LocalDateTime.now().minusDays(1));
        if ("CLOSED".equals(status)) {
            c.setResolvedAt(LocalDateTime.now().minusDays(2));
            c.setClosedAt(LocalDateTime.now().minusDays(1));
        }
        complaintRepo.save(c);
    }

    // ─── Sample helper-tag leads (so the admin Helper Tags screen isn't empty) ──

    /**
     * Seed a few helper-tag leads spanning statuses so the ported ManafyOps admin
     * "Helper Tags" screen can be tested standalone. Idempotent: only seeds when the
     * table is empty (fixed helper mobiles double as the natural dedupe signal).
     */
    private void seedHelperTags() {
        if (helperTagRepo.count() > 0) return;
        helperTag("Lakshmi Bai", "9812300001", "MAID", "A-402", "Prestige Lakeside", "Anita Rao", "9845012345", "PENDING");
        helperTag("Ramu Cook", "9812300002", "COOK", "B-1203", "Sobha Dream Acres", "Rahul Verma", "9845067890", "CONTACTED");
        helperTag("Shanti Devi", "9812300003", "MAID", "C-77", "Brigade Gateway", "Meera Iyer", "9845099887", "PENDING");
        helperTag("Gopal Driver", "9812300004", "DRIVER", "D-9", "Purva Highland", "Farah Khan", "9845033221", "ONBOARDED");
        helperTag("Old Helper", "9812300005", "MAID", "E-5", "Mantri Espana", "Sanjay Gupta", "9845044556", "DISMISSED");
    }

    private void helperTag(String helperName, String helperMobile, String helperType, String flat,
                           String apartmentName, String customerName, String customerMobile, String status) {
        HelperTag t = new HelperTag();
        t.setHelperName(helperName);
        t.setHelperMobile(helperMobile);
        t.setHelperType(helperType);
        t.setFlatNumber(flat);
        t.setApartmentName(apartmentName);
        t.setCustomerName(customerName);
        t.setCustomerMobile(customerMobile);
        t.setStatus(status);
        helperTagRepo.save(t);
    }

    private OpsUser ensureUser(DevAccount acct) {
        String sub = subForMobile(acct.mobile());
        return userRepo.findByCognitoSubAndDeletedFalse(sub).orElseGet(() -> {
            OpsUser u = new OpsUser();
            u.setCognitoSub(sub);
            u.setMobile(acct.mobile());
            u.setDisplayName(acct.name());
            u.setStatus("ACTIVE");
            u.setSuperAdmin("SUPER_ADMIN".equals(acct.roleCode()));
            return userRepo.save(u);
        });
    }

    private void ensureRole(UUID userId, String roleCode) {
        var role = roleRepo.findByCodeAndDeletedFalse(roleCode).orElse(null);
        if (role == null) {
            log.warn("DEV-AUTH: role {} not found in catalog; skipping role grant", roleCode);
            return;
        }
        if (userRoleRepo.findByUserIdAndRoleIdAndDeletedFalse(userId, role.getId()).isPresent()) return;
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        ur.setAssignedAt(LocalDateTime.now());
        ur.setStatus("ACTIVE");
        userRoleRepo.save(ur);
    }

    private void ensureGlobalScope(UUID userId) {
        boolean hasGlobal = userScopeRepo.findByUserIdAndDeletedFalse(userId).stream()
                .anyMatch(s -> "GLOBAL".equals(s.getScopeType()));
        if (hasGlobal) return;
        UserScope s = new UserScope();
        s.setUserId(userId);
        s.setScopeType("GLOBAL");
        userScopeRepo.save(s);
    }

    private void grantOperationsPermissionsToRole(String roleCode) {
        var role = roleRepo.findByCodeAndDeletedFalse(roleCode).orElse(null);
        if (role == null) return;
        // SUPER_ADMIN already has every permission via the catalog seed.
        if ("SUPER_ADMIN".equals(roleCode)) return;
        for (String permCode : OPERATIONS_PERMISSIONS) {
            var perm = permissionRepo.findByCodeAndDeletedFalse(permCode).orElse(null);
            if (perm == null) continue;
            if (rolePermissionRepo.existsByRoleIdAndPermissionIdAndDeletedFalse(role.getId(), perm.getId())) continue;
            RolePermission rp = new RolePermission();
            rp.setRoleId(role.getId());
            rp.setPermissionId(perm.getId());
            rolePermissionRepo.save(rp);
        }
    }
}
