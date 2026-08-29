package com.payment.integrationservice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "uq_webhook_provider_event_id", columnList = "providerEventId", unique = true)
})
public class WebhookEvent {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private String id;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, unique = true)
    private String providerEventId;

    @Column(nullable = false)
    private String eventType;

    @Type(JsonType.class)
    @Column(columnDefinition = "json", nullable = false)
    private JsonNode payload;

    @CreationTimestamp
    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;

    @Column(nullable = false)
    private String processingStatus;

    private String lastError;
}
