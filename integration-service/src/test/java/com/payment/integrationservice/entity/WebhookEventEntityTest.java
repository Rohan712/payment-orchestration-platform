package com.payment.integrationservice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventEntityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-INT-021 prePersistGeneratesUuidWhenNull")
    void prePersistGeneratesUuidWhenNull() throws Exception {
        JsonNode payload = MAPPER.readTree("{\"pi\":\"pi_123\",\"amount\":1000}");

        WebhookEvent e = WebhookEvent.builder()
                .id(null) // ensure null
                .provider("stripe")
                .providerEventId("evt_123")
                .eventType("payment_intent.succeeded")
                .payload(payload)
                .processingStatus("received")
                .build();

        // Before prePersist
        assertThat(e.getId()).isNull();

        // Trigger @PrePersist logic manually
        e.prePersist();

        assertThat(e.getId()).isNotNull();
        // validate UUID format
        UUID.fromString(e.getId());
    }

    @Test
    @DisplayName("TC-INT-022 prePersistDoesNotOverrideExistingId")
    void prePersistDoesNotOverrideExistingId() throws Exception {
        JsonNode payload = MAPPER.readTree("{\"pi\":\"pi_456\"}");
        String existingId = "fixed-id-001";

        WebhookEvent e = WebhookEvent.builder()
                .id(existingId)
                .provider("stripe")
                .providerEventId("evt_456")
                .eventType("payment_intent.created")
                .payload(payload)
                .processingStatus("received")
                .build();

        e.prePersist();

        assertThat(e.getId()).isEqualTo(existingId);
    }

    @Test
    @DisplayName("TC-INT-023 builderAndAccessorsRoundTrip")
    void builderAndAccessorsRoundTrip() throws Exception {
        JsonNode payload = MAPPER.readTree("{\"object\":{\"id\":\"pi_789\"}}");
        OffsetDateTime now = OffsetDateTime.now();

        WebhookEvent e = WebhookEvent.builder()
                .id("abc-123")
                .provider("stripe")
                .providerEventId("evt_789")
                .eventType("payment_intent.failed")
                .payload(payload)
                .receivedAt(null)         // before persistence this is null; set explicitly to assert later
                .processedAt(now)
                .processingStatus("published")
                .lastError("none")
                .build();

        // Getters
        assertThat(e.getId()).isEqualTo("abc-123");
        assertThat(e.getProvider()).isEqualTo("stripe");
        assertThat(e.getProviderEventId()).isEqualTo("evt_789");
        assertThat(e.getEventType()).isEqualTo("payment_intent.failed");
        assertThat(e.getPayload()).isEqualTo(payload);
        assertThat(e.getReceivedAt()).isNull(); // @CreationTimestamp happens on real persist
        assertThat(e.getProcessedAt()).isEqualTo(now);
        assertThat(e.getProcessingStatus()).isEqualTo("published");
        assertThat(e.getLastError()).isEqualTo("none");

        // Setters
        e.setProcessingStatus("archived");
        e.setLastError("timeout");
        assertThat(e.getProcessingStatus()).isEqualTo("archived");
        assertThat(e.getLastError()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("TC-INT-024 timestampsAreNullBeforePersistence")
    void timestampsAreNullBeforePersistence() throws Exception {
        JsonNode payload = MAPPER.readTree("{\"dummy\":true}");

        WebhookEvent e = new WebhookEvent();
        e.setProvider("stripe");
        e.setProviderEventId("evt_before_persist");
        e.setEventType("payment_intent.processing");
        e.setPayload(payload);
        e.setProcessingStatus("received");

        // No persistence context here, so @CreationTimestamp won't run
        assertThat(e.getReceivedAt()).isNull();
        assertThat(e.getProcessedAt()).isNull();
    }
}
