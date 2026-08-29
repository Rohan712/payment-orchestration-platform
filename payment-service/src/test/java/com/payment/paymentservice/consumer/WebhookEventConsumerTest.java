package com.payment.paymentservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.paymentservice.entity.PaymentTransaction;
import com.payment.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookEventConsumerTest {

    private static void setField(Object target, String field, Object value) throws Exception {
        Field f = WebhookEventConsumer.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
    private WebhookEventConsumer newConsumer() {
        var mapper = new ObjectMapper();
        var repo = mock(PaymentRepository.class);
        @SuppressWarnings("unchecked")
        var kafka = (KafkaTemplate<String, Object>) mock(KafkaTemplate.class);
        return new WebhookEventConsumer(mapper, repo, kafka);
    }

    private Object invokePrivate(WebhookEventConsumer c, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = WebhookEventConsumer.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(c, args);
    }
    @Test
    @DisplayName("TC-PAY-020 onWebhookEvent_updatesByPaymentIntentId_andPublishes")
    void onWebhookEventUpdatesByPaymentIntentId() throws Exception {
        // Arrange: mocks
        PaymentRepository repo = mock(PaymentRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        WebhookEventConsumer consumer = new WebhookEventConsumer(mapper, repo, kafka);
        setField(consumer,
                "paymentStatusTopic", "payment.status");

        // existing tx found by paymentIntentId
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .idempotentKey("idem-xyz")
                .userId("user-xyz")
                .amount(BigDecimal.valueOf(1500))
                .currency("USD")
                .paymentMethod("pm_1")
                .status("pending")
                .description("old")
                .paymentIntentId("pi_123")
                .build();
        tx.setCreatedAt(OffsetDateTime.now());
        when(repo.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(tx));

        // Stripe-like payload expected by your consumer
        String payload = """
        {
          "type": "payment_intent.succeeded",
          "object": {
            "id": "pi_123",
            "status": "succeeded"
          }
        }
        """;

        // Act
        consumer.onWebhookEvent(payload);

        // Assert: lookup path used
        verify(repo, atLeastOnce()).findByPaymentIntentId("pi_123");

        // Assert: entity saved with new status
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(txCaptor.capture());
        PaymentTransaction updated = txCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(txId);
        // consumer uses obj.status directly -> "succeeded"
        assertThat(updated.getStatus()).isEqualTo("succeeded");

        // Assert: Kafka publish succeeded
        verify(kafka).send(eq("payment.status"), eq(txId.toString()), any());
    }
    @Test
    @DisplayName("TC-PAY-021 onWebhookEvent_noMatch_doesNothing")
    void onWebhookEventNoMatchDoesNothing() throws Exception {
        PaymentRepository repo = mock(PaymentRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        WebhookEventConsumer consumer = new WebhookEventConsumer(mapper, repo, kafka);
        setField(consumer, "paymentStatusTopic", "payment.status");

        when(repo.findByPaymentIntentId("pi_missing")).thenReturn(Optional.empty());

        String payload = """
        {
          "type": "payment_intent.succeeded",
          "object": { "id": "pi_missing", "status": "succeeded" }
        }
        """;

        consumer.onWebhookEvent(payload);

        verify(repo, atLeastOnce()).findByPaymentIntentId("pi_missing");
        verify(repo, never()).save(any());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("TC-PAY-022 onWebhookEvent_invalidPayload_skips")
    void onWebhookEventInvalidPayloadSkips() throws Exception {
        PaymentRepository repo = mock(PaymentRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        WebhookEventConsumer consumer = new WebhookEventConsumer(mapper, repo, kafka);
        setField(consumer, "paymentStatusTopic", "payment.status");

        // missing "object" node -> early return
        String payload = """
        { "type": "payment_intent.succeeded" }
        """;

        consumer.onWebhookEvent(payload);

        verifyNoInteractions(repo);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("TC-PAY-030 normalizeStripeStatusMapsExplicitStatuses")
    void normalizeStripeStatusMapsExplicitStatuses() throws Exception {
        var c = newConsumer();
        String stripeObject = "payment_intent";

        String s1 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.succeeded", "succeeded");
        String s2 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.requires_action", "requires_action");
        String s3 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.processing", "processing");
        String s4 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.canceled", "canceled");
        String s5 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.payment_failed", "failed");
        String s6 = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                stripeObject, "payment_intent.unknown", "weird_custom");

        assertThat(s1).isEqualTo("success");
        assertThat(s2).isEqualTo("pending");
        assertThat(s3).isEqualTo("processing");
        assertThat(s4).isEqualTo("canceled");
        assertThat(s5).isEqualTo("failed");
        // default: return given status if not in mapping
        assertThat(s6).isEqualTo("weird_custom");
    }

    @Test
    @DisplayName("TC-PAY-031 normalizeStripeStatus derives from eventType when status is null")
    void normalizeStripeStatus_derivesFromEventType() throws Exception {
        var c = newConsumer();

        String a = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "payment_intent", "payment_intent.succeeded", null);
        String b = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "payment_intent", "payment_intent.payment_failed", null);
        String d = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "payment_intent", "payment_intent.processing", null);
        String e = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "charge", "charge.succeeded", null);
        String f = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "charge", "charge.failed", null);
        String g = (String) invokePrivate(c, "normalizeStripeStatus",
                new Class[]{String.class, String.class, String.class},
                "unknown", "unknown.event", null);

        assertThat(a).isEqualTo("success");
        assertThat(b).isEqualTo("failed");
        assertThat(d).isEqualTo("processing");
        assertThat(e).isEqualTo("success");
        assertThat(f).isEqualTo("failed");
        assertThat(g).isEqualTo("pending"); // default when nothing matches
    }

    // ---------- shouldUpgrade / precedence ----------

    @Test
    @DisplayName("TC-PAY-032 shouldUpgradeRespectsPrecedenceAndTerminal")
    void shouldUpgradeRespectsPrecedenceAndTerminal() throws Exception {
        var c = newConsumer();

        // helper invoker
        var should = (java.util.function.BiFunction<String,String,Boolean>) (cur, inc) -> {
            try {
                return (Boolean) invokePrivate(c, "shouldUpgrade",
                        new Class[]{String.class, String.class}, cur, inc);
            } catch (Exception ex) { throw new RuntimeException(ex); }
        };

        // null current -> allow
        assertThat(should.apply(null, "pending")).isTrue();
        // forward progress
        assertThat(should.apply("pending", "processing")).isTrue();
        assertThat(should.apply("processing", "success")).isTrue();
        // no downgrade
        assertThat(should.apply("processing", "pending")).isFalse();
        // equal -> no change
        assertThat(should.apply("processing", "processing")).isFalse();
        // terminals block upgrades
        assertThat(should.apply("failed", "success")).isFalse();
        assertThat(should.apply("canceled", "processing")).isFalse();
        // unknowns treated mildly above pending (default precedence 2)
        assertThat(should.apply("pending", "something_new")).isTrue();
    }

    @Test
    @DisplayName("TC-PAY-033 precedenceValues")
    void precedenceValues() throws Exception {
        var c = newConsumer();

        int pNull = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, (Object) null);
        int pPending = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "pending");
        int pReqPm = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "requires_payment_method");
        int pProcessing = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "processing");
        int pSuccess = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "success");
        int pFailed = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "failed");
        int pCanceled = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "canceled");
        int pUnknown = (Integer) invokePrivate(c, "precedence", new Class[]{String.class}, "mystery");

        assertThat(pNull).isEqualTo(0);
        assertThat(pPending).isEqualTo(1);
        assertThat(pReqPm).isEqualTo(2);
        assertThat(pProcessing).isEqualTo(3);
        assertThat(pSuccess).isEqualTo(4);
        assertThat(pFailed).isEqualTo(5);
        assertThat(pCanceled).isEqualTo(5);
        assertThat(pUnknown).isEqualTo(2); // default branch
    }

    // ---------- firstNonBlank ----------

    @Test
    @DisplayName("TC-PAY-034 firstNonBlankBehaves")
    void firstNonBlankBehaves() throws Exception {
        var c = newConsumer();

        String a = (String) invokePrivate(c, "firstNonBlank", new Class[]{String[].class},
                new Object[]{ new String[]{ " ", "", null, "A", "B" } });
        String b = (String) invokePrivate(c, "firstNonBlank", new Class[]{String[].class},
                new Object[]{ new String[]{ null, "X" } });
        String c1 = (String) invokePrivate(c, "firstNonBlank", new Class[]{String[].class},
                new Object[]{ new String[]{ "  ", "" } });
        String d = (String) invokePrivate(c, "firstNonBlank", new Class[]{String[].class},
                new Object[]{ new String[]{ } });
        String e = (String) invokePrivate(c, "firstNonBlank", new Class[]{String[].class},
                new Object[]{ null });

        assertThat(a).isEqualTo("A");
        assertThat(b).isEqualTo("X");
        assertThat(c1).isNull();
        assertThat(d).isNull();
        assertThat(e).isNull();
    }


    @Test
    @DisplayName("TC-PAY-040 extractPI: payment_intent -> returns object.id")
    void extractPI_paymentIntent_usesId() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();
        var node = mapper.readTree("{\"id\":\"pi_12345\"}");

        String result = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node, "payment_intent"
        );

        assertThat(result).isEqualTo("pi_12345");
    }

    @Test
    @DisplayName("TC-PAY-041 extractPI: charge -> returns payment_intent field when present")
    void extractPI_charge_usesPaymentIntentField() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();
        var node = mapper.readTree("{\"id\":\"ch_999\",\"payment_intent\":\"pi_999\"}");

        String result = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node, "charge"
        );

        assertThat(result).isEqualTo("pi_999");
    }

    @Test
    @DisplayName("TC-PAY-042 extractPI: charge -> blank payment_intent returns null")
    void extractPI_charge_blankReturnsNull() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();
        var node = mapper.readTree("{\"id\":\"ch_1\",\"payment_intent\":\"  \"}");

        String result = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node, "charge"
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TC-PAY-043 extractPI: other object -> nonblank payment_intent preferred")
    void extractPI_other_prefersPaymentIntent() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();
        var node = mapper.readTree("{\"id\":\"evt_1\",\"payment_intent\":\"pi_abc\"}");

        String result = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node, "event"
        );

        assertThat(result).isEqualTo("pi_abc");
    }

    @Test
    @DisplayName("TC-PAY-044 extractPI: other object -> fallback to id when payment_intent blank/missing")
    void extractPI_other_fallbackToId() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();

        // blank payment_intent -> fallback to id
        var node1 = mapper.readTree("{\"id\":\"evt_2\",\"payment_intent\":\"\"}");
        String r1 = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node1, "random"
        );
        assertThat(r1).isEqualTo("evt_2");

        // missing payment_intent -> fallback to id
        var node2 = mapper.readTree("{\"id\":\"evt_3\"}");
        String r2 = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node2, "random"
        );
        assertThat(r2).isEqualTo("evt_3");
    }

    @Test
    @DisplayName("TC-PAY-045 extractPI: other object -> returns null when neither payment_intent nor id present")
    void extractPI_other_returnsNullWhenNothingPresent() throws Exception {
        var c = newConsumer();
        var mapper = new ObjectMapper();
        var node = mapper.readTree("{\"object\":\"something\"}");

        String result = (String) invokePrivate(
                c,
                "extractPaymentIntentIdFromCustomEnvelope",
                new Class[]{com.fasterxml.jackson.databind.JsonNode.class, String.class},
                node, "something"
        );

        assertThat(result).isNull();
    }
}
