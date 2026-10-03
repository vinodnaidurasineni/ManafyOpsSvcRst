package com.manafy.ops.maidapp.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.maidapp.dto.ApplyLeaveRequest;
import com.manafy.ops.maidapp.dto.CheckInRequest;
import com.manafy.ops.maidapp.dto.CheckOutRequest;
import com.manafy.ops.maidapp.dto.MaidJobResponse;
import com.manafy.ops.maidapp.entity.Attendance;
import com.manafy.ops.maidapp.service.MaidAppService;
import com.manafy.ops.maidapp.service.MaidIdentityResolver;
import com.manafy.ops.workforce.entity.Helper;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maid App endpoints — the maid self-service surface (ported from ManafySvcRst
 * /api/v1/maids/me). Principal is an OpsUser; the maid's workforce id is resolved
 * via MaidIdentityResolver (OpsUser.mobile → Helper.phone).
 */
@RestController
@RequestMapping("/api/v1/maids/me")
public class MaidAppController {

    private final MaidAppService maidAppService;
    private final MaidIdentityResolver maidIdentity;

    public MaidAppController(MaidAppService maidAppService, MaidIdentityResolver maidIdentity) {
        this.maidAppService = maidAppService;
        this.maidIdentity = maidIdentity;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<MaidJobResponse>> getJobs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID maidId = maidIdentity.currentMaidId();
        return ApiResponse.ok(date != null
                ? maidAppService.getJobsForDate(maidId, date)
                : maidAppService.getTodayJobs(maidId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(maidAppService.getDashboard(maidIdentity.currentMaidId()));
    }

    @GetMapping("/earnings")
    public ApiResponse<Map<String, Object>> getEarnings() {
        return ApiResponse.ok(maidAppService.getEarnings(maidIdentity.currentMaidId()));
    }

    @PostMapping("/checkin")
    public ApiResponse<Map<String, String>> checkIn(@RequestBody(required = false) CheckInRequest request) {
        Attendance a = maidAppService.checkIn(maidIdentity.currentMaidId(),
                request != null ? request : new CheckInRequest());
        return ApiResponse.ok(Map.of("attendanceId", a.getId().toString(), "status", a.getStatus()));
    }

    @PostMapping("/checkout")
    public ApiResponse<Map<String, String>> checkOut(@Valid @RequestBody CheckOutRequest request) {
        Attendance a = maidAppService.checkOut(maidIdentity.currentMaidId(), request.getAttendanceId());
        return ApiResponse.ok(Map.of("checkoutTime",
                a.getCheckoutTime() != null ? a.getCheckoutTime().toString() : ""));
    }

    @PostMapping("/leaves")
    public ApiResponse<Map<String, String>> applyLeave(@Valid @RequestBody ApplyLeaveRequest request) {
        UUID leaveId = maidAppService.applyLeave(maidIdentity.currentMaidId(), request);
        return ApiResponse.ok(Map.of("leaveId", leaveId.toString()));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        Helper maid = maidIdentity.currentMaid();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", maid.getId().toString());
        profile.put("code", maid.getCode());
        profile.put("name", maid.getName());
        profile.put("phone", maid.getPhone());
        profile.put("category", maid.getCategory());
        profile.put("status", maid.getStatus());
        profile.put("availabilityStatus", maid.getAvailabilityStatus());
        return ApiResponse.ok(profile);
    }

    /**
     * Profile-update request. In the source this queues an admin-reviewed change;
     * here we accept and acknowledge it (no-op change queue for this slice).
     */
    @PostMapping("/profile-update")
    public ApiResponse<Map<String, Object>> requestProfileUpdate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of(
                "status", "SUBMITTED",
                "message", "Profile update request received",
                "fields", body.getOrDefault("fields", Map.of())));
    }

    /** Device token registration (push). Accepted no-op — push is out of scope for the slice. */
    @PostMapping("/device-token")
    public ApiResponse<Map<String, String>> registerDeviceToken(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(Map.of("status", "REGISTERED"));
    }

    /** Notifications — empty list for now (no notification store in ops backend). */
    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> getNotifications() {
        maidIdentity.currentMaidId();
        return ApiResponse.ok(List.of());
    }
}
