package com.payment.integrationservice.controller;

import com.payment.integrationservice.dto.PaymentEvent;
import com.payment.integrationservice.gateway.PaymentGateway;
import com.payment.integrationservice.gateway.PaymentGatewayFactory;
import com.payment.integrationservice.service.PaymentIntentService;
import com.payment.integrationservice.service.PaymentIntentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IntegrationPaymentsController.class)
@AutoConfigureMockMvc(addFilters = false) // disable security filters for this slice test
@Import({IntegrationPaymentsControllerTest.TestBeansConfig.class,
        IntegrationPaymentsControllerTest.TestExceptionAdvice.class})
@ActiveProfiles("test")
class IntegrationPaymentsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestBeansConfig {
        @Bean
        @Primary
        public PaymentGatewayFactory paymentGatewayFactory() {
            return new PaymentGatewayFactory() {
                @Override
                public PaymentGateway get(String provider) {
                    return new PaymentGateway() {
                        @Override
                        public Map<String, Object> createPaymentIntent(PaymentEvent event) {
                            HashMap<String, Object> m = new HashMap<>();
                            // Return keys that your controller/service actually surface
                            m.put("paymentIntentId", "pi_test_123");
                            m.put("clientSecret", "cs_test_123");
                            // keep optional, in case service ignores it
                            m.put("status", "requires_payment_method");
                            return m;
                        }
                    };
                }
            };
        }

        @Bean
        @Primary
        public PaymentIntentService paymentIntentService(PaymentGatewayFactory factory) {
            // Real service wired to our in-memory gateway
            return new PaymentIntentServiceImpl(factory);
        }
    }

    @RestControllerAdvice
    static class TestExceptionAdvice {
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
            HashMap<String, Object> body = new HashMap<>();
            body.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    @Test
    @DisplayName("TC-INT-001 createPaymentOk")
    void createPaymentOk() throws Exception {
        String body = "{ \"amount\": 99.99, \"currency\": \"usd\", \"provider\": \"stripe\", \"idempotencyKey\": \"idem-001\" }";

        mockMvc.perform(post("/v1/integrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_test_123"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    @DisplayName("TC-INT-002 createPaymentBadRequestWhenMissingFields")
    void createPaymentBadRequestWhenMissingFields() throws Exception {
        // Triggers IllegalArgumentException("Amount is required") in PaymentIntentServiceImpl
        String invalidBody = "{ \"provider\": \"stripe\" }";

        mockMvc.perform(post("/v1/integrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Amount is required"));
    }
}
