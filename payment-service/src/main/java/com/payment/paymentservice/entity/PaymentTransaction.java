package com.payment.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_tx_user", columnList = "userId"),
        @Index(name = "uq_payment_tx_idempotency_key", columnList = "idempotentKey", unique = true),
        @Index(name = "uq_payment_tx_stripe_intent", columnList = "paymentIntentId", unique = true)
})
public class PaymentTransaction implements Persistable<UUID> {

    @Id
//    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String idempotentKey;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(nullable = false)
    private String status;

    private String paymentIntentId;

    @Column(columnDefinition = "TEXT")
    private String clientSecret;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    // ---- Persistable control ----
    @Transient
    @JsonIgnore
    private boolean newAggregate = true;

    @Override
    public UUID getId() { return id; }

    @Override
    @JsonIgnore
    public boolean isNew() {
        // force INSERT even when id is non-null
        return newAggregate || id == null;
    }

    @PostLoad @PostPersist
    public void markNotNew() { this.newAggregate = false; }

    /** Call this right after building the entity if you pre-set id */
    public void markNew() { this.newAggregate = true; }
}
