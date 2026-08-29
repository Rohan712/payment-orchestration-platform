package com.payment.integrationservice.service;

import com.payment.integrationservice.dto.PaymentEvent;
import com.payment.integrationservice.gateway.PaymentGateway;
import com.payment.integrationservice.gateway.PaymentGatewayFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentIntentServiceImpl (no @MockBean, just Mockito).
 */
class PaymentIntentServiceImplTest {

    @Test
    @DisplayName("TC-INT-011 createPaymentIntentOkMapsEventAndReturnsId")
    void createPaymentIntentOkMapsEventAndReturnsId() throws Exception {
        // Arrange
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(factory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenReturn(mapOf("paymentIntentId", "pi_123"));

        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", 12.34);            // major units
        payload.put("currency", "usd");          // exponent 2 expected
        payload.put("provider", "stripe");
        payload.put("idempotencyKey", "idem-123");

        // Act
        Map<String, Object> out = service.createPaymentIntent(payload);

        // Assert response mapping
        assertThat(out).containsEntry("paymentIntentId", "pi_123");

        // Assert event mapping (minor units & fields) via captor
        ArgumentCaptor<PaymentEvent> ev = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(gateway).createPaymentIntent(ev.capture());
        PaymentEvent event = ev.getValue();

        assertThat(event.provider()).isEqualTo("stripe");
        assertThat(event.currency()).isEqualTo("usd"); // service uses given casing
        // 12.34 USD with exponent=2 -> 1234 minor units
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("1234"));
        assertThat(event.idempotencyKey()).isEqualTo("idem-123");

        verify(factory).get("stripe");
    }

    @Test
    @DisplayName("TC-INT-012 createPaymentIntentOkMapsStripePaymentIntentIdFallback")
    void createPaymentIntentOkMapsStripePaymentIntentIdFallback() throws Exception {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(factory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenReturn(mapOf("stripePaymentIntentId", "pi_fallback"));

        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", "10");    // string ok
        payload.put("currency", "usd");
        payload.put("provider", "stripe");

        Map<String, Object> out = service.createPaymentIntent(payload);

        assertThat(out).containsEntry("paymentIntentId", "pi_fallback");
    }

    @Test
    @DisplayName("TC-INT-013 createPaymentIntentMissingAmountThrows")
    void createPaymentIntentMissingAmountThrows() {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("currency", "usd");
        payload.put("provider", "stripe");

        assertThatThrownBy(() -> service.createPaymentIntent(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount is required");
        verify(factory, never()).get(any());
    }

    @Test
    @DisplayName("TC-INT-014 createPaymentIntentInvalidAmountThrows")
    void createPaymentIntentInvalidAmountThrows() {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", "abc"); // invalid
        payload.put("currency", "usd");

        assertThatThrownBy(() -> service.createPaymentIntent(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid amount format");
        verify(factory, never()).get(any());
    }

    @Test
    @DisplayName("TC-INT-015 createPaymentIntentUnsupportedCurrencyThrows")
    void createPaymentIntentUnsupportedCurrencyThrows() {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", 1);
        payload.put("currency", "zzz"); // unsupported

        assertThatThrownBy(() -> service.createPaymentIntent(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency");
        verify(factory, never()).get(any());
    }

    @Test
    @DisplayName("TC-INT-016 createPaymentIntentZeroExponentCurrencyUsesNoScaling")
    void createPaymentIntentZeroExponentCurrencyUsesNoScaling() throws Exception {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(factory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenReturn(mapOf("paymentIntentId", "pi_jpy"));

        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", 100); // major units
        payload.put("currency", "jpy"); // exponent=0
        payload.put("provider", "stripe");

        Map<String, Object> out = service.createPaymentIntent(payload);
        assertThat(out).containsEntry("paymentIntentId", "pi_jpy");

        ArgumentCaptor<PaymentEvent> ev = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(gateway).createPaymentIntent(ev.capture());
        assertThat(ev.getValue().amount()).isEqualByComparingTo(new BigDecimal("100")); // no scaling
    }

    @Test
    @DisplayName("TC-INT-017 createPaymentIntentDefaultsProviderToStripe")
    void createPaymentIntentDefaultsProviderToStripe() throws Exception {
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(factory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenReturn(mapOf("paymentIntentId", "pi_def"));

        PaymentIntentServiceImpl service = new PaymentIntentServiceImpl(factory);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", 5);
        payload.put("currency", "usd");
        // no provider key -> default "stripe" in service

        Map<String, Object> out = service.createPaymentIntent(payload);
        assertThat(out).containsEntry("paymentIntentId", "pi_def");

        // verify factory used default provider
        verify(factory).get("stripe");
    }

    // --- helpers ---

    private static Map<String, Object> mapOf(String k, Object v) {
        HashMap<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }
}
