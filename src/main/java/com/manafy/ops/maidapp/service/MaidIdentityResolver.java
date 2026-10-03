package com.manafy.ops.maidapp.service;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the authenticated principal to a maid's workforce (Helper) id.
 *
 * The maid signs in as an OpsUser (dev-OTP today, Cognito later); their workforce
 * identity is the Helper whose phone matches the OpsUser mobile. This is the
 * ops-backend adaptation of the source SecurityContext.currentMaidId().
 */
@Service
public class MaidIdentityResolver {

    private final AuthenticationContext auth;
    private final HelperRepository helperRepository;

    public MaidIdentityResolver(AuthenticationContext auth, HelperRepository helperRepository) {
        this.auth = auth;
        this.helperRepository = helperRepository;
    }

    public UUID currentMaidId() {
        OpsUser user = auth.currentUser();
        String mobile = user.getMobile();
        if (mobile == null || mobile.isBlank()) {
            throw new BusinessException("MAID_401", "No maid profile linked to this account", HttpStatus.FORBIDDEN);
        }
        Helper helper = helperRepository.findByPhoneAndDeletedFalse(mobile)
                .orElseThrow(() -> new BusinessException("MAID_401",
                        "No maid profile linked to this account", HttpStatus.FORBIDDEN));
        return helper.getId();
    }

    public Helper currentMaid() {
        UUID id = currentMaidId();
        return helperRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("MAID_404", "Maid not found", HttpStatus.NOT_FOUND));
    }
}
