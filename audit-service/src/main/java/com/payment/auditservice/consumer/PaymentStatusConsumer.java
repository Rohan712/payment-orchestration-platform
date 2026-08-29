package com.payment.auditservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.auditservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditService auditService;

    @Value("${audit.resource-type:payment}")
    private String defaultResourceType;

    @KafkaListener(
            topics = "${kafka.topic.payment-status-events:payment.status}",
            groupId = "${kafka.consumer.group-id:audit-service}"
    )
    public void onPaymentStatus(String message) {
        try {
            log.info("Inside paymentStatusConsumer.onPaymentStatus");
            JsonNode root = mapper.readTree(message);
            String event = root.path("event").asText("UNKNOWN");
            String correlationId = root.path("correlationId").asText(null);
            String userId = root.path("userId").asText(null);
            String transactionId = root.path("transactionId").asText(null);
            String paymentIntentId = root.path("paymentIntentId").asText(null);
            String idempotencyKey = root.path("idempotencyKey").asText(null);
            String resourceId = transactionId != null ? transactionId : paymentIntentId;
            OffsetDateTime occurredAt = OffsetDateTime.now();
            if (root.hasNonNull("updatedAt")) {

                    occurredAt = OffsetDateTime.parse(root.path("updatedAt").asText());

            }
            log.info("Persisting record in DB");
           auditService.recordEvent(
                    event, correlationId, userId, defaultResourceType, resourceId, idempotencyKey,
                    null, root, null, occurredAt
            );
            log.info("Consumed payment.status event for resource {}",resourceId);
        } catch (Exception e) {
            log.error("Failed to process payment.status message", e);
        }
    }
}

