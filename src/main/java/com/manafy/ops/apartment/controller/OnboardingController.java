package com.manafy.ops.apartment.controller;

import com.manafy.ops.apartment.dto.ApartmentMapper;
import com.manafy.ops.apartment.dto.OnboardingDtos.*;
import com.manafy.ops.apartment.entity.ApartmentOnboarding;
import com.manafy.ops.apartment.service.OnboardingAppService;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Apartment onboarding lifecycle (Phase 2 §24). Explicit action endpoints only —
 * no PATCH status. Thin controller delegating to OnboardingAppService.
 */
@RestController
@RequestMapping("/api/v1/apartments/onboarding")
public class OnboardingController {

    private final OnboardingAppService service;
    private final AuthenticationContext auth;

    public OnboardingController(OnboardingAppService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private OnboardingResponse withChecklist(ApartmentOnboarding onb) {
        return ApartmentMapper.toOnboarding(onb, service.checklist(onb.getId()));
    }

    @PostMapping("/start")
    public ApiResponse<OnboardingResponse> start(@Valid @RequestBody OnboardingStartRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(withChecklist(service.start(auth.currentUserId(), req.apartmentId(), idemKey)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OnboardingResponse> get(@PathVariable UUID id) {
        ApartmentOnboarding onb = service.getForRead(auth.currentUserId(), id);
        return ApiResponse.ok(withChecklist(onb));
    }

    @GetMapping("/{id}/checklist")
    public ApiResponse<OnboardingChecklistResponse> checklist(@PathVariable UUID id) {
        ApartmentOnboarding onb = service.getForRead(auth.currentUserId(), id);
        List<ChecklistItemResponse> items = service.checklist(id).stream()
                .map(ApartmentMapper::toChecklistItem).toList();
        return ApiResponse.ok(new OnboardingChecklistResponse(onb.getId(), onb.getApartmentId(), onb.getStatus(), items));
    }

    @PostMapping("/{id}/checklist")
    public ApiResponse<ChecklistItemResponse> updateChecklist(@PathVariable UUID id,
                                                              @Valid @RequestBody ChecklistUpdateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toChecklistItem(
                service.updateChecklistItem(auth.currentUserId(), id, req.itemKey(), req.complete())));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> history(@PathVariable UUID id) {
        service.getForRead(auth.currentUserId(), id); // authorize via parent apartment
        return ApiResponse.ok(service.history(id).stream().map(ApartmentMapper::toHistory).toList());
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<OnboardingResponse> submit(@PathVariable UUID id,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(withChecklist(service.submit(auth.currentUserId(), id, idemKey)));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<OnboardingResponse> verify(@PathVariable UUID id,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(withChecklist(service.verify(auth.currentUserId(), id, idemKey)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<OnboardingResponse> reject(@PathVariable UUID id, @Valid @RequestBody OnboardingRejectRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(withChecklist(service.reject(auth.currentUserId(), id, req.reason(), idemKey)));
    }

    @PostMapping("/{id}/resubmit")
    public ApiResponse<OnboardingResponse> resubmit(@PathVariable UUID id,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(withChecklist(service.resubmit(auth.currentUserId(), id, idemKey)));
    }
}
