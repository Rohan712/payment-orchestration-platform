package com.payment.paymentservice.service;
import com.payment.paymentservice.dto.ApiResponse;
import com.payment.paymentservice.dto.PaymentCreateRequest;
import com.payment.paymentservice.dto.PaymentTransactionResponse;
import com.payment.paymentservice.entity.PaymentTransaction;
import com.payment.paymentservice.exception.PaymentProcessingException;
import com.payment.paymentservice.gateway.PaymentGateway;
import com.payment.paymentservice.gateway.PaymentGatewayFactory;
import com.payment.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.payment.paymentservice.service.PaymentServiceImpl.PAYMENT_REQUESTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {

    private PaymentRepository repo;
    private PaymentGatewayFactory gatewayFactory;
    private PaymentGateway gateway;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(PaymentRepository.class);
        gatewayFactory = mock(PaymentGatewayFactory.class);
        gateway = mock(PaymentGateway.class);
        service = new PaymentServiceImpl(repo, gatewayFactory);
    }

    private static PaymentCreateRequest req(BigDecimal amount, String currency, String pmId, String desc) {
        // psp set to "stripe" by default; change per test if needed
        return new PaymentCreateRequest(amount, currency, pmId, desc, "stripe");
    }

    // ✅ TC-PAY-001: Idempotent replay returns existing transaction
    @Test
    @DisplayName("TC-PAY-001 idempotentExistingTransactionReturned")
    void idempotentExistingTransactionReturned() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .idempotentKey("idem-1")
                .userId("user-1")
                .amount(BigDecimal.valueOf(1500))
                .currency("USD")
                .status("pending")
                .description("existing")
                .build();

        when(repo.findByIdempotentKey("idem-1")).thenReturn(Optional.of(existing));

        ApiResponse<Map<String, Object>> resp =
                service.createPayment("idem-1", "user-1",
                        req(BigDecimal.valueOf(1500), "USD", "pm_1", "existing"),
                        "USD");

        assertNotNull(resp);
        assertTrue(resp.resultInfo().success());
        assertEquals(PAYMENT_REQUESTED, resp.resultInfo().resultCode());
        verify(repo, never()).save(any());
        verifyNoInteractions(gatewayFactory);
    }

    // ✅ TC-PAY-002: Successful new payment (gateway returns paymentIntentId)
    @Test
    @DisplayName("TC-PAY-002 createPaymentSuccess")
    void createPaymentSuccess() throws Exception {
        when(repo.findByIdempotentKey("idem-2")).thenReturn(Optional.empty());
        when(gatewayFactory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenReturn(Map.of("paymentIntentId", "pi_success_1"));

        ApiResponse<Map<String, Object>> resp =
                service.createPayment("idem-2", "user-2",
                        req(BigDecimal.valueOf(1200.55), "USD", "pm_2", "ok"),
                        "USD");

        assertNotNull(resp);
        assertTrue(resp.resultInfo().success());
        assertEquals(PAYMENT_REQUESTED, resp.resultInfo().resultCode());
        assertEquals("pending", resp.data().get("status"));

        verify(gatewayFactory).get("stripe");
        verify(gateway).createPaymentIntent(any());
        verify(repo).save(any(PaymentTransaction.class));
    }

    // ✅ TC-PAY-003: Gateway throws → wrapped in PaymentProcessingException
    @Test
    @DisplayName("TC-PAY-003 gatewayThrowsWrappedInPaymentProcessingException")
    void gatewayThrowsWrappedInPaymentProcessingException() throws Exception {
        when(repo.findByIdempotentKey("idem-err")).thenReturn(Optional.empty());
        when(gatewayFactory.get("stripe")).thenReturn(gateway);
        when(gateway.createPaymentIntent(any())).thenThrow(new RuntimeException("boom"));

        assertThrows(PaymentProcessingException.class, () ->
                service.createPayment("idem-err", "user-x",
                        req(BigDecimal.TEN, "USD", "pm_x", "err"),
                        "USD")
        );

        verify(repo, never()).save(any());
    }

    // ✅ TC-PAY-004: currencyExponent valid and invalid (private static via reflection)
    @Test
    @DisplayName("TC-PAY-004 currencyExponentValidAndInvalid")
    void currencyExponentValidAndInvalid() throws Exception {
        Method m = PaymentServiceImpl.class.getDeclaredMethod("currencyExponent", String.class);
        m.setAccessible(true);

        int usd = (int) m.invoke(null, "USD");
        int jpy = (int) m.invoke(null, "JPY");
        assertEquals(2, usd);
        assertEquals(0, jpy);

        // invalid currency should throw IllegalArgumentException wrapped by reflection
        try {
            m.invoke(null, "NOPE");
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    // ✅ TC-PAY-005: toStripeMinorUnits converts correctly and rejects null
    @Test
    @DisplayName("TC-PAY-005 toStripeMinorUnitsConvertsAndRejectsNull")
    void toStripeMinorUnitsConvertsAndRejectsNull() throws Exception {
        Method m = PaymentServiceImpl.class.getDeclaredMethod("toStripeMinorUnits", BigDecimal.class, int.class);
        m.setAccessible(true);

        BigDecimal out = (BigDecimal) m.invoke(null, new BigDecimal("19.99"), 2);
        assertEquals(new BigDecimal("1999"), out);

        // null amount should cause IllegalArgumentException (unwrap InvocationTargetException)
        try {
            m.invoke(null, null, 2);
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    // ✅ TC-PAY-006: getPayment() success
    @Test
    @DisplayName("TC-PAY-006 getPaymentSuccess")
    void getPaymentSuccess() {
        UUID id = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(id)
                .userId("user-6")
                .amount(BigDecimal.valueOf(3450))
                .currency("INR")
                .status("success")
                .paymentIntentId("pi_6")
                .description("desc-6")
                .build();

        when(repo.findById(id)).thenReturn(Optional.of(tx));

        var resp = service.getPayment(id);
        assertNotNull(resp);
        assertTrue(resp.resultInfo().success());
        assertEquals("INQUIRE_SUCCESS", resp.resultInfo().resultCode());

        PaymentTransactionResponse dto = resp.data();
        assertNotNull(dto);
        assertEquals(id.toString(), dto.transactionId());
        assertEquals("user-6", dto.userId());
        assertEquals(BigDecimal.valueOf(3450), dto.amount());
        assertEquals("INR", dto.currency());
        assertEquals("success", dto.status());
        assertEquals("pi_6", dto.stripePaymentIntentId());
        assertEquals("desc-6", dto.description());
    }

    // ✅ TC-PAY-007: getPayment() not found
    @Test
    @DisplayName("TC-PAY-007 getPaymentNotFound")
    void getPaymentNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        var resp = service.getPayment(id);
        assertNotNull(resp);
        assertFalse(resp.resultInfo().success());
        assertEquals("NOT_FOUND", resp.resultInfo().resultCode());
        assertNull(resp.data());
    }

    // ✅ TC-PAY-008: providerOverridesToPspWhenPresent
//    @Test
//    @DisplayName("TC-PAY-008 providerOverridesToPspWhenPresent")
//    void providerOverridesToPspWhenPresent() throws Exception {
//        when(repo.findByIdempotentKey("idem-psp")).thenReturn(Optional.empty());
//
//        // When PSP is provided, factory must be asked for that PSP (e.g., "razorpay")
//        when(gatewayFactory.get("razorpay")).thenReturn(gateway);
//        when(gateway.createPaymentIntent(any())).thenReturn(Map.of("paymentIntentId", "pi_psp_1"));
//
//        PaymentCreateRequest withPsp = new PaymentCreateRequest(
//                BigDecimal.valueOf(2000), "USD", "pm_7", "with psp", "razorpay"
//        );
//
//        ApiResponse<Map<String, Object>> resp =
//                service.createPayment("idem-psp", "user-psp", withPsp, "USD");
//
//        assertNotNull(resp);
//        assertTrue(resp.resultInfo().success());
//        verify(gatewayFactory).get("razorpay");
//        verify(gateway).createPaymentIntent(any());
//        verify(repo).save(any(PaymentTransaction.class));
//    }
}
