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
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import com.manafy.ops.maidapp.entity.Booking;
import com.manafy.ops.maidapp.entity.BookingAssignment;
import com.manafy.ops.maidapp.entity.BookingSchedule;
import com.manafy.ops.maidapp.entity.Complaint;
import com.manafy.ops.maidapp.entity.HelperTag;
import com.manafy.ops.maidapp.repository.BookingAssignmentRepository;
import com.manafy.ops.maidapp.repository.BookingRepository;
import com.manafy.ops.maidapp.repository.BookingScheduleRepository;
import com.manafy.ops.maidapp.repository.ComplaintRepository;
import com.manafy.ops.maidapp.repository.HelperTagRepository;

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

    /** Assignment/queue permissions the dev users need for the manual-request flow. */
    private static final List<String> OPERATIONS_PERMISSIONS = List.of(
            "ASSIGNMENT_VIEW", "ASSIGNMENT_CREATE", "ASSIGNMENT_REASSIGN", "ASSIGNMENT_CANCEL",
            "REGION_VIEW", "AREA_VIEW");

    /** The dev accounts. Mobile numbers are distinct from the Community app's. */
    public record DevAccount(String mobile, String name, String roleCode) {}

    private static final List<DevAccount> ACCOUNTS = List.of(
            new DevAccount("7000000001", "Dev Field Officer", "FIELD_OFFICER"),
            new DevAccount("7000000002", "Dev Ops Coordinator", "OPERATIONS_COORDINATOR"),
            new DevAccount("7000000003", "Dev Area Manager", "AREA_OPERATIONS_MANAGER"),
            new DevAccount("7000000004", "Dev Super Admin", "SUPER_ADMIN"));

    /**
     * Maid-app dev login accounts. Their mobile == a seeded MAID Helper.phone so
     * MaidAppController can resolve the maid's workforce id. They get NO ops role
     * (a maid is not an ops staff persona) — just an authenticated OpsUser.
     */
    private static final List<DevAccount> MAID_ACCOUNTS = List.of(
            new DevAccount("9900010001", "Anita Sharma", null),
            new DevAccount("9900010002", "Priya Das", null));

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
    private final BookingRepository bookingRepo;
    private final BookingScheduleRepository scheduleRepo;
    private final BookingAssignmentRepository assignmentRepo;
    private final ComplaintRepository complaintRepo;
    private final HelperTagRepository helperTagRepo;

    public DevUserSeeder(DevAuthProperties props, OpsUserRepository userRepo, RoleRepository roleRepo,
                         UserRoleRepository userRoleRepo, UserScopeRepository userScopeRepo,
                         PermissionRepository permissionRepo, RolePermissionRepository rolePermissionRepo,
                         ManualAssignmentRequestRepository manualRepo, HelperRepository helperRepo,
                         BookingRepository bookingRepo, BookingScheduleRepository scheduleRepo,
                         BookingAssignmentRepository assignmentRepo, ComplaintRepository complaintRepo,
                         HelperTagRepository helperTagRepo) {
        this.props = props;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.userScopeRepo = userScopeRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.manualRepo = manualRepo;
        this.helperRepo = helperRepo;
        this.bookingRepo = bookingRepo;
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
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

        // Maid-app login accounts (no ops role). Created after helpers so the
        // OpsUser.mobile → Helper.phone link resolves in MaidAppController.
        for (DevAccount maid : MAID_ACCOUNTS) {
            ensureUser(maid);
        }

        // Sample maid-app jobs so /maids/me/jobs + /dashboard return data.
        seedMaidJobs();

        // Sample complaints so the admin complaints screen has data.
        seedComplaints();

        // Sample helper tags so the admin helper-tags screen has data.
        seedHelperTags();

        log.warn("DEV-AUTH ENABLED: seeded {} local login accounts (mobiles 7000000001..04, OTP={}), "
                + "helper workforce, and sample requests. This must NEVER be enabled in production.",
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

    /**
     * Seed a sample maid-app booking + today's schedule + assignment for Anita
     * (H-MAID-01) so the maid dashboard/jobs are populated. Idempotent via a
     * fixed booking_number. References the runtime-generated Helper id.
     */
    private void seedMaidJobs() {
        Helper anita = helperRepo.findByCategoryAndDeletedFalse("MAID").stream()
                .filter(h -> "H-MAID-01".equals(h.getCode())).findFirst().orElse(null);
        if (anita == null) return;

        String bookingNumber = "MB-DEV-0001";
        if (bookingRepo.findAll().stream().anyMatch(b -> bookingNumber.equals(b.getBookingNumber()))) {
            return; // already seeded
        }

        LocalDate today = LocalDate.now();
        Booking booking = new Booking();
        booking.setBookingNumber(bookingNumber);
        booking.setCustomerId(UUID.randomUUID()); // dev customer (Community owns real customers)
        booking.setStartDate(today);
        booking.setEndDate(today.plusMonths(1).minusDays(1));
        booking.setDurationMonths(1);
        booking.setBookingStatus("ASSIGNED");
        booking.setTotalAmount(new BigDecimal("3500"));
        booking.setBhkType("2BHK");
        booking.setSelectedTime("06:00 AM - 06:30 AM");
        booking.setCreatedBy("DEV-SEED");
        booking = bookingRepo.save(booking);

        BookingSchedule schedule = new BookingSchedule();
        schedule.setBookingId(booking.getId());
        schedule.setScheduledDate(today);
        schedule.setStatus("ASSIGNED");
        schedule.setServiceName("Recurring Maid");
        schedule.setApartmentName("Prestige Lakeside");
        schedule.setTower("A");
        schedule.setFlatNumber("A-402");
        schedule.setSlotTime("06:00 AM - 06:30 AM");
        schedule.setCreatedBy("DEV-SEED");
        schedule = scheduleRepo.save(schedule);

        BookingAssignment assignment = new BookingAssignment();
        assignment.setBookingScheduleId(schedule.getId());
        assignment.setMaidId(anita.getId());
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAssignedBy("DEV-SEED");
        assignment.setAssignmentStatus("ASSIGNED");
        assignment.setCreatedBy("DEV-SEED");
        assignmentRepo.save(assignment);
    }

    /** Seed a couple of sample complaints (idempotent by description marker). */
    private void seedComplaints() {
        if (!complaintRepo.findByDeletedFalseOrderByCreatedAtDesc().isEmpty()) return;
        complaint("MAID_NO_SHOW", "OPEN", "Maid did not arrive for the morning slot.",
                "Anita Rao", "9845012345", "Anita Sharma", "9900010001");
        complaint("WORK_NOT_DONE", "IN_PROGRESS", "Dishes were left unwashed.",
                "Rahul Verma", "9845067890", "Priya Das", "9900010002");
    }

    private void complaint(String type, String status, String desc,
                           String customerName, String customerMobile, String maidName, String maidMobile) {
        Complaint c = new Complaint();
        c.setComplaintType(type);
        c.setComplaintStatus(status);
        c.setDescription(desc);
        c.setCustomerName(customerName);
        c.setCustomerMobile(customerMobile);
        c.setMaidName(maidName);
        c.setMaidMobile(maidMobile);
        c.setCreatedBy("DEV-SEED");
        complaintRepo.save(c);
    }

    /** Seed a couple of sample helper tags (idempotent — only when table empty). */
    private void seedHelperTags() {
        if (!helperTagRepo.findByDeletedFalseOrderByCreatedAtDesc().isEmpty()) return;
        helperTag("Sunita Devi", "9812345670", "Anita Rao", "9845012345", "Prestige Lakeside", "A-402", "PENDING");
        helperTag("Ramesh Kumar", "9812345671", "Rahul Verma", "9845067890", "Sobha Dream Acres", "B-1203", "CONTACTED");
    }

    private void helperTag(String helperName, String helperMobile, String customerName,
                           String customerMobile, String apartmentName, String flatNumber, String status) {
        HelperTag t = new HelperTag();
        t.setHelperName(helperName);
        t.setHelperMobile(helperMobile);
        t.setCustomerName(customerName);
        t.setCustomerMobile(customerMobile);
        t.setApartmentName(apartmentName);
        t.setFlatNumber(flatNumber);
        t.setStatus(status);
        t.setCreatedBy("DEV-SEED");
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
