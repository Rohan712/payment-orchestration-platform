package com.payment.integrationservice.gateway;

import com.payment.integrationservice.dto.PaymentEvent;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StripePaymentGatewayTest {

    @Test
    @DisplayName("TC-INT-005 createPaymentIntentBuildsParamsAndReturnsMap")
    void createPaymentIntentBuildsParamsAndReturnsMap() throws Exception {
        StripePaymentGateway gw = new StripePaymentGateway("sk_test_123");

        // Mock static PaymentIntent.create
        try (MockedStatic<PaymentIntent> mocked = Mockito.mockStatic(PaymentIntent.class)) {
            PaymentIntent fake = new PaymentIntent();
            fake.setId("pi_123");
            fake.setClientSecret("secret_abc");
            fake.setStatus("succeeded");

            mocked.when(() -> PaymentIntent.create(Mockito.anyMap(), Mockito.any()))
                    .thenReturn(fake);

            PaymentEvent ev = PaymentEvent.builder()
                    .event("PAYMENT_REQUESTED")
                    .transactionId("tx_1")
                    .idempotencyKey("idem_1")
                    .userId("user_1")
                    .amount(BigDecimal.valueOf(5000))
                    .currency("usd")
                    .paymentMethodId("pm_123")
                    .description("desc")
                    .provider("stripe")
                    .build();

            Map<String, Object> res = gw.createPaymentIntent(ev);
            assertEquals("pi_123", res.get("stripePaymentIntentId"));
            assertEquals("secret_abc", res.get("clientSecret"));
            assertEquals("succeeded", res.get("status"));
        }
    }
}