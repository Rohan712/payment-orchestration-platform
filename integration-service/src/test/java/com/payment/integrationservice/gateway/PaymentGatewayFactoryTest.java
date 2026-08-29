package com.payment.integrationservice.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayFactoryTest {

    @Test
    @DisplayName("TC-INT-003 returnsStripeGatewayWhenProviderNull")
    void returnsStripeGatewayWhenProviderNull() {
        PaymentGatewayFactory factory = new PaymentGatewayFactory();
        // Inject secret key (simulating @Value)
        try {
            var f = PaymentGatewayFactory.class.getDeclaredField("stripeSecretKey");
            f.setAccessible(true);
            f.set(factory, "sk_test_123");
        } catch (Exception e) {
            fail(e);
        }
        PaymentGateway gw = factory.get(null);
        assertNotNull(gw);
        assertTrue(gw instanceof StripePaymentGateway);
    }

    @Test
    @DisplayName("TC-INT-004 returnsStripeGatewayWhenProviderStripeCaseInsensitive")
    void returnsStripeGatewayWhenProviderStripeCaseInsensitive() {
        PaymentGatewayFactory factory = new PaymentGatewayFactory();
        try {
            var f = PaymentGatewayFactory.class.getDeclaredField("stripeSecretKey");
            f.setAccessible(true);
            f.set(factory, "sk_test_123");
        } catch (Exception e) {
            fail(e);
        }
        PaymentGateway gw = factory.get("StRiPe");
        assertNotNull(gw);
        assertTrue(gw instanceof StripePaymentGateway);
    }

    @Test
    @DisplayName("TC-INT-005 throwsOnUnsupportedProvider")
    void throwsOnUnsupportedProvider() {
        PaymentGatewayFactory factory = new PaymentGatewayFactory();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.get("paypal"));
        assertTrue(ex.getMessage().contains("Unsupported provider"));
    }
}