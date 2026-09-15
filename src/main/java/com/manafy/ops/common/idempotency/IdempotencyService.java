package com.manafy.ops.common.idempotency;

import com.manafy.ops.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Idempotency guard (design DD-14, spec §67). For a sensitive POST carrying an
 * {@code Idempotency-Key} header, {@link #register} claims the key atomically; a
 * replay with the same key is rejected with 409 DUPLICATE_REQUEST.
 *
 * Foundation-light: rejects duplicates. Later phases may extend to replay a stored
 * response. A blank/absent key is a no-op (idempotency is opt-in per request).
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;

    public IdempotencyService(IdempotencyKeyRepository repo) {
        this.repo = repo;
    }

    /**
     * Claim an idempotency key for an endpoint/user. Throws 409 DUPLICATE_REQUEST
     * if the key was already used. No-op when key is null/blank.
     */
    public void register(String key, String endpoint, UUID userId) {
        if (key == null || key.isBlank()) return;
        if (repo.existsByKey(key)) {
            throw BusinessException.conflict("DUPLICATE_REQUEST", "Duplicate request (idempotency key already used)");
        }
        IdempotencyKey rec = new IdempotencyKey();
        rec.setKey(key);
        rec.setEndpoint(endpoint);
        rec.setUserId(userId);
        try {
            repo.save(rec);
        } catch (DataIntegrityViolationException dup) {
            // Concurrent request claimed the same key first.
            throw BusinessException.conflict("DUPLICATE_REQUEST", "Duplicate request (idempotency key already used)");
        }
    }
}
