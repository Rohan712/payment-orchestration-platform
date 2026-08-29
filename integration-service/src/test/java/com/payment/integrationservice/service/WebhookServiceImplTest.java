package com.payment.integrationservice.service;

import com.payment.integrationservice.entity.WebhookEvent;
import com.payment.integrationservice.repository.WebhookEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceImplTest {

    @Mock
    WebhookEventRepository repo;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    WebhookServiceImpl service;

    @Test
    @DisplayName("TC-INT-006 handleStripeWebhookSuccess")
    void handleStripeWebhookSuccess() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"payment.succeeded\",\"data\":{\"object\":{}}}";

        WebhookEvent saved = WebhookEvent.builder()
                .id("abc-123")
                .provider("stripe")
                .providerEventId("evt_123")
                .eventType("payment.succeeded")
                .processingStatus("received")
                .build();

        given(repo.findByProviderEventId("evt_123")).willReturn(Optional.empty());
        given(repo.save(any(WebhookEvent.class))).willReturn(saved);

        CompletableFuture<SendResult<String, Object>> ok = CompletableFuture.completedFuture(
                new SendResult<>(null, new RecordMetadata(null, 0, 0, 0L, 0L, 0, 0))
        );
        given(kafkaTemplate.send(any(), anyString(), any())).willReturn(ok);

        Map<String, Object> result = service.handleStripeWebhook(payload, "sig", "secret");

        verify(repo, atLeastOnce()).save(any(WebhookEvent.class));
        verify(kafkaTemplate).send(any(), eq("evt_123"), any());
        assertThat(result).containsEntry("providerEventId", "evt_123");
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("TC-INT-007 handleStripeWebhookJsonError")
    void handleStripeWebhookJsonError() {
        String invalidPayload = "not-json";
        Map<String, Object> result = service.handleStripeWebhook(invalidPayload, "sig", "secret");

        verifyNoInteractions(repo);
        verify(kafkaTemplate, never()).send(any(), anyString(), any());
        assertThat(result.get("status")).isEqualTo("Failed");
        assertThat(result.get("message")).isIn("Failed to process webhook", "Failed to emit kafka event");
    }

    @Test
    @DisplayName("TC-INT-008 handleStripeWebhookExistingEvent")
    void handleStripeWebhookExistingEvent() {
        String payload = "{\"id\":\"evt_existing\",\"type\":\"payment.failed\"}";
        WebhookEvent existing = WebhookEvent.builder()
                .id("existing-id")
                .provider("stripe")
                .providerEventId("evt_existing")
                .eventType("payment.failed")
                .processingStatus("published")
                .build();

        given(repo.findByProviderEventId("evt_existing")).willReturn(Optional.of(existing));

        Map<String, Object> result = service.handleStripeWebhook(payload, "sig", "secret");

        verify(repo, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
        assertThat(result).containsEntry("providerEventId", "evt_existing");
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("TC-INT-009 handleStripeWebhookKafkaFailure")
    void handleStripeWebhookKafkaFailure() {
        String payload = "{\"id\":\"evt_fail\",\"type\":\"payment.failed\"}";

        WebhookEvent event = WebhookEvent.builder()
                .id("x1")
                .provider("stripe")
                .providerEventId("evt_fail")
                .eventType("payment.failed")
                .processingStatus("received")
                .build();

        given(repo.findByProviderEventId("evt_fail")).willReturn(Optional.empty());
        given(repo.save(any(WebhookEvent.class))).willReturn(event);

        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        given(kafkaTemplate.send(any(), anyString(), any())).willReturn(failed);

        Map<String, Object> result = service.handleStripeWebhook(payload, "sig", "secret");

        verify(kafkaTemplate).send(any(), eq("evt_fail"), any());
        assertThat(result.get("status")).isNull();
    }

    @Test
    @DisplayName("TC-INT-010 handleStripeWebhookGenericFailure")
    void handleStripeWebhookGenericFailure() {
        // Valid JSON payload so JSON parsing succeeds
        String payload = "{\"id\":\"evt_crash\",\"type\":\"payment.failed\"}";

        // Simulate: findByProviderEventId returns empty (so code goes into try block)
        given(repo.findByProviderEventId("evt_crash")).willReturn(Optional.empty());

        given(repo.save(any(WebhookEvent.class)))
                .willThrow(new RuntimeException("DB down hard"));

        // Act
        Map<String, Object> result = service.handleStripeWebhook(payload, "sig", "secret");

        // Assert
        assertThat(result)
                .containsEntry("status", "Failed")
                .containsEntry("message", "Failed to process webhook");

        // Verify no kafka send since we failed before publishEvent()
        verify(kafkaTemplate, never()).send(any(), anyString(), any());
    }

}
