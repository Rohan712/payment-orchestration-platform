package com.payment.auditservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.payment.auditservice.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentStatusConsumer which parses a JSON message and delegates to AuditService.recordEvent(...).
 * Updated to avoid any PaymentRepository usage — only mocks AuditService.
 */
@ExtendWith(MockitoExtension.class)
class PaymentStatusConsumerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentStatusConsumer consumer;


    @BeforeEach
    void beforeEach() throws Exception {
        // The consumer field defaultResourceType is injected by Spring via @Value at runtime.
        // In this unit test we set it manually via reflection so the consumer uses "payment".
        Field f = PaymentStatusConsumer.class.getDeclaredField("defaultResourceType");
        f.setAccessible(true);
        f.set(consumer, "payment");
    }

    @Test
    @DisplayName("TC-AUD-001 onPaymentStatusParsesJson")
    void onPaymentStatusParsesJson(){
        String msg = "{\"event\":\"PAYMENT_STATUS\",\"correlationId\":\"corr-1\",\"userId\":\"user-9\",\"transactionId\":\"tx-123\",\"idempotencyKey\":\"idem-1\",\"foo\":\"bar\"}";

        consumer.onPaymentStatus(msg);

        // verify recordEvent called with parsed values
        ArgumentCaptor<JsonNode> jsonCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<OffsetDateTime> timeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(auditService, times(1)).recordEvent(
                eq("PAYMENT_STATUS"),
                eq("corr-1"),
                eq("user-9"),
                eq("payment"),    // we set defaultResourceType via reflection in @BeforeEach
                eq("tx-123"),
                eq("idem-1"),
                isNull(),        // request passed as null in implementation
                jsonCaptor.capture(), // root passed as response
                isNull(),
                timeCaptor.capture()
        );

        JsonNode passedRoot = jsonCaptor.getValue();
        assertThat(passedRoot.path("foo").asText()).isEqualTo("bar");
        OffsetDateTime used = timeCaptor.getValue();
        assertThat(used).isNotNull();
    }

    @Test
    @DisplayName(("TC-AUD-002 onPaymentStatusParsesUpdatedAtAndUsesIt"))
    void onPaymentStatusParsesUpdatedAtAndUsesIt(){
        String updatedAt = "2024-05-01T12:34:56+00:00";
        String msg = String.format("{\"event\":\"PAYMENT_STATUS\",\"transactionId\":\"tx-456\",\"updatedAt\":\"%s\"}", updatedAt);

        consumer.onPaymentStatus(msg);

        // allow nullable strings by using any() for those parameters; capture the OffsetDateTime
        ArgumentCaptor<OffsetDateTime> timeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditService).recordEvent(
                any(),    // event (may be "PAYMENT_STATUS")
                any(),    // correlationId (nullable)
                any(),    // userId (nullable)
                eq("payment"),
                eq("tx-456"),
                any(),    // idempotencyKey (nullable)
                any(),    // request (null in impl)
                any(JsonNode.class),
                any(),    // error
                timeCaptor.capture()
        );

        OffsetDateTime used = timeCaptor.getValue();
        assertThat(used).isNotNull();
        // ensure parsed time starts with the expected date/time
        assertThat(used.toString()).startsWith("2024-05-01T12:34:56");
    }


    @Test
    @DisplayName("TC-AUD-003 onPaymentStatusHandlesMalformedJson")
    void onPaymentStatusHandlesMalformedJson() {
        String bad = "not-a-json";

        // should not throw
        consumer.onPaymentStatus(bad);

        // and auditService should not be called
        verifyNoInteractions(auditService);
    }
}