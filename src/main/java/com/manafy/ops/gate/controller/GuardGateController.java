package com.manafy.ops.gate.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.gate.entity.DeliveryEntry;
import com.manafy.ops.gate.entity.StaffPass;
import com.manafy.ops.gate.entity.VisitorEntry;
import com.manafy.ops.gate.repository.DeliveryEntryRepository;
import com.manafy.ops.gate.repository.StaffPassRepository;
import com.manafy.ops.gate.repository.VisitorEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guard gate endpoints (ManafyOps compatibility layer) — ported from ManafySvcRst
 * guard APIs. Visitors (walk-in/checkin/checkin-otp/checkout/today/pending),
 * deliveries (create/waiting), staff verify, activity log. Apartment scope comes
 * from the X-Apartment-Id header (matches the app's guardGateApi).
 */
@RestController
@RequestMapping("/api/v1/gate/guard")
public class GuardGateController {

    private final VisitorEntryRepository visitorRepo;
    private final DeliveryEntryRepository deliveryRepo;
    private final StaffPassRepository staffRepo;

    public GuardGateController(VisitorEntryRepository visitorRepo,
                               DeliveryEntryRepository deliveryRepo,
                               StaffPassRepository staffRepo) {
        this.visitorRepo = visitorRepo;
        this.deliveryRepo = deliveryRepo;
        this.staffRepo = staffRepo;
    }

    // ─── Visitors ────────────────────────────────────────────────────

    @PostMapping("/visitors/walkin")
    public ApiResponse<Map<String, Object>> createWalkIn(@RequestHeader("X-Apartment-Id") UUID apartmentId,
                                                         @RequestBody Map<String, Object> body) {
        VisitorEntry v = new VisitorEntry();
        v.setApartmentId(apartmentId);
        v.setFlatId(uuid(body.get("flatId")));
        v.setVisitorName(str(body.get("visitorName")));
        v.setVisitorMobile(str(body.get("visitorMobile")));
        v.setPurpose(str(body.get("purpose")));
        v.setVisitorVehicleNumber(str(body.get("visitorVehicleNumber")));
        v.setVisitorPhotoUrl(str(body.get("visitorPhotoUrl")));
        v.setNotes(str(body.get("notes")));
        v.setStatus("PENDING"); // walk-in awaits resident approval
        v.setCreatedBy("GUARD");
        return ApiResponse.ok(toVisitor(visitorRepo.save(v)));
    }

    @PostMapping("/visitors/{visitorId}/checkin")
    public ApiResponse<Map<String, Object>> checkIn(@PathVariable UUID visitorId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        VisitorEntry v = getVisitor(visitorId);
        v.setStatus("CHECKED_IN");
        v.setCheckinAt(LocalDateTime.now());
        return ApiResponse.ok(toVisitor(visitorRepo.save(v)));
    }

    @PostMapping("/visitors/checkin-otp")
    public ApiResponse<Map<String, Object>> checkInByOtp(@RequestHeader("X-Apartment-Id") UUID apartmentId,
                                                         @RequestBody Map<String, Object> body) {
        String otp = str(body.get("otp"));
        VisitorEntry v = visitorRepo.findByApartmentIdAndOtpCodeAndDeletedFalse(apartmentId, otp)
                .orElseThrow(() -> new BusinessException("GATE_404", "No visitor for this OTP", HttpStatus.NOT_FOUND));
        v.setStatus("CHECKED_IN");
        v.setCheckinAt(LocalDateTime.now());
        return ApiResponse.ok(toVisitor(visitorRepo.save(v)));
    }

    @PostMapping("/visitors/{visitorId}/checkout")
    public ApiResponse<Map<String, Object>> checkOut(@PathVariable UUID visitorId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        VisitorEntry v = getVisitor(visitorId);
        v.setStatus("CHECKED_OUT");
        v.setCheckoutAt(LocalDateTime.now());
        return ApiResponse.ok(toVisitor(visitorRepo.save(v)));
    }

    @GetMapping("/visitors/today")
    public ApiResponse<List<Map<String, Object>>> todaysVisitors(@RequestHeader("X-Apartment-Id") UUID apartmentId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return ApiResponse.ok(visitorRepo
                .findByApartmentIdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(apartmentId, startOfDay)
                .stream().map(this::toVisitor).toList());
    }

    @GetMapping("/visitors/pending")
    public ApiResponse<List<Map<String, Object>>> pendingVisitors(@RequestHeader("X-Apartment-Id") UUID apartmentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String s : List.of("PENDING", "APPROVED")) {
            visitorRepo.findByApartmentIdAndStatusAndDeletedFalse(apartmentId, s)
                    .forEach(v -> out.add(toVisitor(v)));
        }
        return ApiResponse.ok(out);
    }

    // ─── Deliveries ──────────────────────────────────────────────────

    @PostMapping("/deliveries")
    public ApiResponse<Map<String, Object>> createDelivery(@RequestHeader("X-Apartment-Id") UUID apartmentId,
                                                           @RequestBody Map<String, Object> body) {
        DeliveryEntry d = new DeliveryEntry();
        d.setApartmentId(apartmentId);
        d.setFlatId(uuid(body.get("flatId")));
        d.setDeliveryCompany(str(body.get("deliveryCompany")));
        d.setDeliveryPersonName(str(body.get("deliveryPersonName")));
        d.setDeliveryPersonMobile(str(body.get("deliveryPersonMobile")));
        d.setPackageDescription(str(body.get("packageDescription")));
        d.setPhotoUrl(str(body.get("photoUrl")));
        d.setStatus("WAITING");
        d.setCreatedBy("GUARD");
        return ApiResponse.ok(toDelivery(deliveryRepo.save(d)));
    }

    @GetMapping("/deliveries/waiting")
    public ApiResponse<List<Map<String, Object>>> waitingDeliveries(@RequestHeader("X-Apartment-Id") UUID apartmentId) {
        return ApiResponse.ok(deliveryRepo.findByApartmentIdAndStatusAndDeletedFalse(apartmentId, "WAITING")
                .stream().map(this::toDelivery).toList());
    }

    // ─── Staff verify ────────────────────────────────────────────────

    @GetMapping("/staff/verify")
    public ApiResponse<List<Map<String, Object>>> verifyStaff(@RequestHeader("X-Apartment-Id") UUID apartmentId,
                                                              @RequestParam String mobile) {
        return ApiResponse.ok(staffRepo.findByApartmentIdAndStaffMobileAndDeletedFalse(apartmentId, mobile)
                .stream().map(this::toStaff).toList());
    }

    // ─── Activity log ────────────────────────────────────────────────

    @GetMapping("/activity")
    public ApiResponse<List<Map<String, Object>>> activity(@RequestHeader("X-Apartment-Id") UUID apartmentId,
                                                           @RequestParam(required = false, defaultValue = "12") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Map<String, Object>> out = new ArrayList<>();
        visitorRepo.findByApartmentIdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(apartmentId, since)
                .forEach(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "VISITOR");
                    m.put("name", v.getVisitorName());
                    m.put("status", v.getStatus());
                    m.put("at", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
                    out.add(m);
                });
        return ApiResponse.ok(out);
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private VisitorEntry getVisitor(UUID id) {
        return visitorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("GATE_404", "Visitor not found", HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> toVisitor(VisitorEntry v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId().toString());
        m.put("visitorId", v.getId().toString());
        m.put("visitorName", v.getVisitorName());
        m.put("visitorMobile", v.getVisitorMobile());
        m.put("purpose", v.getPurpose());
        m.put("visitorVehicleNumber", v.getVisitorVehicleNumber());
        m.put("flatId", v.getFlatId() != null ? v.getFlatId().toString() : null);
        m.put("status", v.getStatus());
        return m;
    }

    private Map<String, Object> toDelivery(DeliveryEntry d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId().toString());
        m.put("deliveryCompany", d.getDeliveryCompany());
        m.put("deliveryPersonName", d.getDeliveryPersonName());
        m.put("packageDescription", d.getPackageDescription());
        m.put("flatId", d.getFlatId() != null ? d.getFlatId().toString() : null);
        m.put("status", d.getStatus());
        return m;
    }

    private Map<String, Object> toStaff(StaffPass s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("staffName", s.getStaffName());
        m.put("staffMobile", s.getStaffMobile());
        m.put("staffType", s.getStaffType());
        m.put("flatNumber", s.getFlatNumber());
        m.put("status", s.getStatus());
        return m;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
    private UUID uuid(Object o) {
        try { return o == null ? null : UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }
}
