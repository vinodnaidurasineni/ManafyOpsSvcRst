package com.manafy.ops.apartment.service;

import com.manafy.ops.apartment.domain.ChecklistItems;
import com.manafy.ops.apartment.domain.OnboardingStateMachine;
import com.manafy.ops.apartment.entity.*;
import com.manafy.ops.apartment.repository.*;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding lifecycle application service (Phase 2 §5/§6). Owns the state machine,
 * checklist, status history, and the apartment-status projection. All transitions
 * are explicit actions (no PATCH status). Every transition writes insert-only
 * status history + audit within one transaction.
 */
@Service
public class OnboardingAppService {

    private final ApartmentRepository apartmentRepo;
    private final ApartmentOnboardingRepository onboardingRepo;
    private final OnboardingChecklistItemRepository checklistRepo;
    private final OnboardingStatusHistoryRepository historyRepo;
    private final AuthorizationService authz;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public OnboardingAppService(ApartmentRepository apartmentRepo, ApartmentOnboardingRepository onboardingRepo,
                                OnboardingChecklistItemRepository checklistRepo,
                                OnboardingStatusHistoryRepository historyRepo, AuthorizationService authz,
                                ResourceScopeResolver resolver, PermissionService permissionService,
                                AuditService audit, IdempotencyService idempotency) {
        this.apartmentRepo = apartmentRepo;
        this.onboardingRepo = onboardingRepo;
        this.checklistRepo = checklistRepo;
        this.historyRepo = historyRepo;
        this.authz = authz;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    private Apartment apartment(UUID id) {
        return apartmentRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Apartment not found"));
    }

    private ResourceRef ref(Apartment a) {
        return resolver.apartment(a.getId(), a.getAreaId(), a.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private ApartmentOnboarding onboarding(UUID onboardingId) {
        return onboardingRepo.findByIdAndDeletedFalse(onboardingId)
                .orElseThrow(() -> BusinessException.notFound("Onboarding not found"));
    }

    /** Transition helper: validate, set status, write history + audit atomically. */
    private void transition(ApartmentOnboarding onb, String to, UUID actor, String reason) {
        String from = onb.getStatus();
        OnboardingStateMachine.requireTransition(from, to);
        onb.setStatus(to);

        OnboardingStatusHistory h = new OnboardingStatusHistory();
        h.setOnboardingId(onb.getId());
        h.setApartmentId(onb.getApartmentId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(actor);
        h.setReason(reason);
        historyRepo.save(h);

        audit.audit(actor, primaryRole(actor), "ONBOARDING_" + to, "APARTMENT_ONBOARDING", onb.getId(),
                from, to, reason);
        audit.activity("APARTMENT", onb.getApartmentId(), actor, "ONBOARDING_" + to,
                "Onboarding " + from + " → " + to);
    }

    // ─── start ───────────────────────────────────────────────────────

    @Transactional
    public ApartmentOnboarding start(UUID actor, UUID apartmentId, String idemKey) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_ONBOARD", ref(a));
        idempotency.register(idemKey, "POST /apartments/onboarding/start", actor);

        if (onboardingRepo.findByApartmentIdAndDeletedFalse(apartmentId).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT",
                    "Onboarding already started for this apartment", HttpStatus.CONFLICT);
        }
        ApartmentOnboarding onb = new ApartmentOnboarding();
        onb.setApartmentId(apartmentId);
        onb.setStatus(OnboardingStateMachine.DRAFT);
        onb.setStartedBy(actor);
        ApartmentOnboarding saved = onboardingRepo.save(onb);

        // Seed the checklist from the canonical definition.
        for (ChecklistItems.Def def : ChecklistItems.DEFAULTS) {
            OnboardingChecklistItem item = new OnboardingChecklistItem();
            item.setOnboardingId(saved.getId());
            item.setItemKey(def.key());
            item.setLabel(def.label());
            item.setMandatory(def.mandatory());
            item.setComplete(false);
            checklistRepo.save(item);
        }
        // Move the apartment into ONBOARDING.
        a.setStatus("ONBOARDING");
        apartmentRepo.save(a);

        historyRepo.save(newHistory(saved, null, OnboardingStateMachine.DRAFT, actor, "Onboarding started"));
        audit.audit(actor, primaryRole(actor), "ONBOARDING_STARTED", "APARTMENT_ONBOARDING", saved.getId(),
                null, "DRAFT", "Onboarding started");
        return saved;
    }

    private OnboardingStatusHistory newHistory(ApartmentOnboarding onb, String from, String to, UUID actor, String reason) {
        OnboardingStatusHistory h = new OnboardingStatusHistory();
        h.setOnboardingId(onb.getId());
        h.setApartmentId(onb.getApartmentId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(actor);
        h.setReason(reason);
        return h;
    }

    // ─── submit ────────────────────────────────────────────────────────

    @Transactional
    public ApartmentOnboarding submit(UUID actor, UUID onboardingId, String idemKey) {
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_ONBOARD", ref(a));
        idempotency.register(idemKey, "POST /apartments/onboarding/{id}/submit", actor);
        transition(onb, OnboardingStateMachine.SUBMITTED, actor, "Submitted for verification");
        onb.setSubmittedAt(LocalDateTime.now());
        a.setStatus("PENDING_VERIFICATION");
        apartmentRepo.save(a);
        return onboardingRepo.save(onb);
    }

    // ─── verify ─────────────────────────────────────────────────────────

    @Transactional
    public ApartmentOnboarding verify(UUID actor, UUID onboardingId, String idemKey) {
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_VERIFY", ref(a));
        idempotency.register(idemKey, "POST /apartments/onboarding/{id}/verify", actor);

        // Mandatory checklist (excluding VERIFICATION itself) must be complete before VERIFIED.
        List<OnboardingChecklistItem> incomplete = checklistRepo
                .findByOnboardingIdAndMandatoryTrueAndCompleteFalseAndDeletedFalse(onboardingId).stream()
                .filter(i -> !"VERIFICATION".equals(i.getItemKey()))
                .toList();
        if (!incomplete.isEmpty()) {
            throw new BusinessException("ONBOARDING_INCOMPLETE",
                    "Mandatory checklist items incomplete: " +
                            incomplete.stream().map(OnboardingChecklistItem::getItemKey).toList(),
                    HttpStatus.CONFLICT);
        }
        transition(onb, OnboardingStateMachine.VERIFIED, actor, "Verified");
        onb.setVerifiedBy(actor);
        onb.setVerifiedAt(LocalDateTime.now());
        // Auto-complete the VERIFICATION checklist item.
        checklistRepo.findByOnboardingIdAndItemKeyAndDeletedFalse(onboardingId, "VERIFICATION")
                .ifPresent(v -> { v.setComplete(true); v.setCompletedBy(actor); v.setCompletedAt(LocalDateTime.now()); checklistRepo.save(v); });
        a.setStatus("READY_FOR_ACTIVATION");
        apartmentRepo.save(a);
        return onboardingRepo.save(onb);
    }

    // ─── reject ──────────────────────────────────────────────────────────

    @Transactional
    public ApartmentOnboarding reject(UUID actor, UUID onboardingId, String reason, String idemKey) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.validation("Rejection reason is required");
        }
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_ONBOARD_REJECT", ref(a));
        idempotency.register(idemKey, "POST /apartments/onboarding/{id}/reject", actor);
        transition(onb, OnboardingStateMachine.REJECTED, actor, reason);
        onb.setRejectedBy(actor);
        onb.setRejectedAt(LocalDateTime.now());
        onb.setRejectionReason(reason);
        // Apartment returns to ONBOARDING so the onboarder can fix + resubmit.
        a.setStatus("ONBOARDING");
        apartmentRepo.save(a);
        return onboardingRepo.save(onb);
    }

    // ─── resubmit ────────────────────────────────────────────────────────

    @Transactional
    public ApartmentOnboarding resubmit(UUID actor, UUID onboardingId, String idemKey) {
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_ONBOARD", ref(a));
        idempotency.register(idemKey, "POST /apartments/onboarding/{id}/resubmit", actor);
        // Historical rejection info is RETAINED (rejection_reason/rejected_by/at stay set).
        transition(onb, OnboardingStateMachine.RESUBMITTED, actor, "Resubmitted after rejection");
        onb.setResubmittedAt(LocalDateTime.now());
        a.setStatus("PENDING_VERIFICATION");
        apartmentRepo.save(a);
        return onboardingRepo.save(onb);
    }

    // ─── checklist ───────────────────────────────────────────────────────

    @Transactional
    public OnboardingChecklistItem updateChecklistItem(UUID actor, UUID onboardingId, String itemKey, boolean complete) {
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_ONBOARD", ref(a));
        OnboardingChecklistItem item = checklistRepo
                .findByOnboardingIdAndItemKeyAndDeletedFalse(onboardingId, itemKey)
                .orElseThrow(() -> BusinessException.notFound("Checklist item not found: " + itemKey));
        item.setComplete(complete);
        if (complete) {
            item.setCompletedBy(actor);
            item.setCompletedAt(LocalDateTime.now());
        } else {
            item.setCompletedBy(null);
            item.setCompletedAt(null);
        }
        OnboardingChecklistItem saved = checklistRepo.save(item);
        audit.activity("APARTMENT", onb.getApartmentId(), actor, "CHECKLIST_UPDATED",
                itemKey + " → " + (complete ? "complete" : "incomplete"));
        return saved;
    }

    @Transactional(readOnly = true)
    public ApartmentOnboarding getForRead(UUID actor, UUID onboardingId) {
        ApartmentOnboarding onb = onboarding(onboardingId);
        Apartment a = apartment(onb.getApartmentId());
        authz.authorize(actor, "APARTMENT_VIEW", ref(a));
        return onb;
    }

    @Transactional(readOnly = true)
    public List<OnboardingChecklistItem> checklist(UUID onboardingId) {
        return checklistRepo.findByOnboardingIdAndDeletedFalse(onboardingId);
    }

    @Transactional(readOnly = true)
    public List<OnboardingStatusHistory> history(UUID onboardingId) {
        return historyRepo.findByOnboardingIdOrderByCreatedAtAsc(onboardingId);
    }
}
