package com.manafy.ops.common.idempotency;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency record (design DD-14, spec §67). A sensitive POST carrying an
 * {@code Idempotency-Key} header registers a unique (key) row; a replay with the
 * same key is rejected/short-circuited so a duplicate submission is never
 * processed twice.
 *
 * Foundation-light: stores the key, the endpoint, and the acting user. Later
 * phases (payments/assignments) can extend this with a stored response.
 */
@Entity
@Table(name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idem_key"))
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idem_key", nullable = false, length = 128)
    private String key;

    @Column(nullable = false, length = 120)
    private String endpoint;

    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
