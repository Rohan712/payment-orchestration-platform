package com.payment.paymentservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.paymentservice.dto.PaymentTransactionResponse;
import com.payment.paymentservice.entity.PaymentTransaction;
import com.payment.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    private static final Map<String,Integer> PRECEDENCE = Map.of(
            "pending", 1,
            "requires_payment_method", 2,
            "processing", 3,
            "success", 4,          // normalized from Stripe's "succeeded"
            "failed", 5,           // terminal (treat as highest if you want to prevent later changes)
            "canceled", 5
    );
    @Value("${kafka.topic.payment-status-events:payment.status}")
    private String paymentStatusTopic;

    @KafkaListener(
            topics = "${kafka.topic.webhook-events:webhook.events}",
            groupId = "${kafka.consumer.group-id:payment-service}"
    )
    @Transactional
    public void onWebhookEvent(String message) {
        log.info("Inside WebhookEventConsumer.onWebhookEvent");
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("type").asText("");
            JsonNode obj = root.path("object");

            if (obj.isMissingNode() || obj.isNull()) {
                log.warn("Invalid webhook payload: missing object node");
                return;
            }

            String paymentIntentId = obj.path("id").asText(null);
            String newStatus = obj.path("status").asText("pending");

            Optional<PaymentTransaction> opt = repo.findByPaymentIntentId(paymentIntentId);
            if (opt.isEmpty()) {
                log.warn("No transaction found for paymentIntentId={}", paymentIntentId);
                return;
            }

            PaymentTransaction tx = opt.get();
            tx.setStatus(newStatus);
            repo.save(tx);

            // Build DTO for Kafka payload
            PaymentTransactionResponse eventDto = new PaymentTransactionResponse(
                    tx.getId().toString(),
                    tx.getUserId(),
                    tx.getAmount(),
                    tx.getCurrency(),
                    tx.getStatus(),
                    tx.getPaymentIntentId(),
                    tx.getDescription(),
                    tx.getCreatedAt(),
                    tx.getUpdatedAt() != null ? tx.getUpdatedAt() :  OffsetDateTime.now()
            );

            kafka.send(paymentStatusTopic, tx.getId().toString(), objectMapper.writeValueAsString(eventDto));
            log.info("Published PAYMENT_STATUS_UPDATED for txId={} to topic={}", tx.getId(), paymentStatusTopic);

        } catch (Exception e) {
            log.error("Failed to process webhook event", e);
        }
    }

    /** For PaymentIntent: use its id; For Charge: use the linked payment_intent. */
    private String extractPaymentIntentIdFromCustomEnvelope(JsonNode obj, String stripeObject) {
        if ("payment_intent".equals(stripeObject)) {
            return obj.path("id").asText(null);
        }
        if ("charge".equals(stripeObject)) {
            String pi = obj.path("payment_intent").asText(null);
            return (pi == null || pi.isBlank()) ? null : pi;
        }
        // fallback attempts
        String pi = obj.path("payment_intent").asText(null);
        if (pi != null && !pi.isBlank()) return pi;
        return obj.path("id").asText(null);
    }

    /** Normalize Stripe → internal status (you can tweak the mapping). */
    private String normalizeStripeStatus(String stripeObject, String eventType, String status) {
        if (status != null) {
            switch (status) {
                case "succeeded": return "success";
                case "requires_payment_method": return "requires_payment_method";
                case "processing": return "processing";
                case "canceled": return "canceled";
                case "requires_action":
                case "requires_confirmation": return "pending";
                case "failed": return "failed";
                default: return status;
            }
        }
        // derive from event type when status is missing
        if (eventType != null) {
            if (eventType.startsWith("payment_intent.")) {
                if (eventType.endsWith(".succeeded")) return "success";
                if (eventType.endsWith(".payment_failed")) return "failed";
                if (eventType.endsWith(".canceled")) return "canceled";
                if (eventType.endsWith(".processing")) return "processing";
                if (eventType.endsWith(".requires_action")) return "pending";
            } else if (eventType.startsWith("charge.")) {
                if (eventType.endsWith(".succeeded")) return "success";
                if (eventType.endsWith(".failed")) return "failed";
                if (eventType.endsWith(".pending")) return "pending";
            }
        }
        return "pending";
    }

    /** Only allow forward progress; treat failed/canceled as terminal. */
    private boolean shouldUpgrade(String current, String incoming) {
        if (incoming == null) return false;
        if (current == null || current.isBlank()) return true;
        if ("failed".equals(current) || "canceled".equals(current)) return false; // terminal

        int cur = precedence(current);
        int inc = precedence(incoming);
        return inc > cur;
    }

    private int precedence(String s) {
        if (s == null) return 0;
        switch (s) {
            case "pending": return 1;
            case "requires_payment_method": return 2;
            case "processing": return 3;
            case "success": return 4;
            case "failed":
            case "canceled": return 5; // terminal
            default: return 2; // unknowns mildly above pending
        }
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
