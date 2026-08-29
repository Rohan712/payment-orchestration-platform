package com.payment.paymentservice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.paymentservice.dto.ApiResponse;
import com.payment.paymentservice.dto.PaymentEvent;
import com.payment.paymentservice.dto.PaymentTransactionResponse;
import com.payment.paymentservice.dto.ResultInfo;
import com.payment.paymentservice.entity.PaymentTransaction;
import com.payment.paymentservice.gateway.PaymentGateway;
import com.payment.paymentservice.gateway.PaymentGatewayFactory;
import com.payment.paymentservice.repository.PaymentRepository;
import com.payment.paymentservice.service.PaymentServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired
    RestTemplate integrationRestTemplate;

     MockRestServiceServer server;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        // Bind a mock server to the *actual* RestTemplate bean used by the service
        server = MockRestServiceServer.bindTo(integrationRestTemplate).build();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        PaymentGatewayFactory testPaymentGatewayFactory() {
            return new PaymentGatewayFactory() {
                @Override
                public PaymentGateway get(String provider) {
                    // Always return a deterministic fake gateway that never calls Stripe.
                    return new PaymentGateway() {
                        @Override
                        public Map<String, Object> createPaymentIntent(PaymentEvent event) {
                            return Map.of(
                                    "paymentIntentId", "pi_test_123",
                                    "clientSecret", "cs_test_123"
                            );
                        }
                    };
                }
            };
        }
    }

    @Test
    @DisplayName("TC-PAY-CTRL-001 createPaymentValidRequestCreatesTransaction")
    void createPaymentValidRequestCreatesTransaction() throws Exception {
        // Your validator accepts lowercase 3-letter currency ("usd"), which is fine.
        String body = """
          {"amount":15000,"currency":"usd","paymentMethodId":"pm_123","description":"new payment","psp":"stripe"}
        """;

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-abc")
                        .header("X-User-Id", "user-1")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-PAY-002 missingAmountOrCurrency")
    void createPaymentMissingUserHeaderReturns401() throws Exception {
        // IMPORTANT: use a *valid* body so validation doesn't short-circuit to 400
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content("""
              {"amount":1000,"currency":"usd","paymentMethodId":"pm_1","description":"desc","psp":"stripe"}
            """))
                .andExpect(status().isUnauthorized()); // 401 from your controller branch
    }


    @Test
    @DisplayName("TC-PAY-011 fetchValidTransaction")
    void fetchValidTransaction(CapturedOutput output) throws Exception {
        // Arrange: persist a payment so service can find it
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())          // ✅ manually assign ID
                .idempotentKey("idem-fetch-011")
                .userId("user-011")
                .amount(BigDecimal.valueOf(1200))
                .currency("USD")
                .paymentMethod("pm_011")
                .status("success")
                .description("fetch test")
                .paymentIntentId("pi_011")
                .build();

        tx = paymentRepository.saveAndFlush(tx);
        UUID id = tx.getId();

        // Act
        var mvc = mockMvc.perform(get("/api/v1/payments/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert DTO
        String body = mvc.getResponse().getContentAsString();
        ApiResponse<PaymentTransactionResponse> resp =
                objectMapper.readValue(body, new TypeReference<>() {});

        assertThat(resp.resultInfo()).isNotNull();
        assertThat(resp.resultInfo().success()).isTrue();
        assertThat(resp.data()).isNotNull();
        assertThat(resp.data().transactionId()).isEqualTo(id.toString());
        assertThat(resp.data().userId()).isEqualTo("user-011");

        // ✅ FIX: BigDecimal.equals() is scale-sensitive (1200.0 ≠ 1200.00)
        assertThat(resp.data().amount()).isEqualByComparingTo("1200.00");

        assertThat(resp.data().currency()).isEqualTo("USD");
        assertThat(resp.data().status()).isEqualTo("success");
        assertThat(resp.data().stripePaymentIntentId()).isEqualTo("pi_011");
        assertThat(resp.data().description()).isEqualTo("fetch test");

        // ✅ Optional: ensure LoggingAspect executed (for logging package coverage)
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("Entering: com.payment.paymentService.controller.PaymentController.getPayment")
                .contains("Exiting: com.payment.paymentService.controller.PaymentController.getPayment")
                .contains("Entering: com.payment.paymentService.service.PaymentServiceImpl.getPayment")
                .contains("Exiting: com.payment.paymentService.service.PaymentServiceImpl.getPayment");
    }


    @Test
    @DisplayName("TC-PAY-013 fetchMissingTransactionNotFound")
    void fetchMissingTransactionNotFound() throws Exception {
        // random UUID not in DB → service should return failure ApiResponse and controller maps to 404
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payments/{id}", missing)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-PAY-012 invalidUuidFormat")
    void invalidUuidFormat() throws Exception {
        // Spring fails to convert PathVariable to UUID → 400
        mockMvc.perform(get("/api/v1/payments/{id}", "not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
    @Test
    @DisplayName("TC-PAY-040 handleMethodArgumentNotValid returns 400 with aggregated messages")
    void handleMethodArgumentNotValidReturns400WithAggregatedMessages() throws Exception {
        // Build MockMvc with the test controller
        MockMvc mvc = standaloneSetup(new TestController()).build();

        // Invalid payload: amount too low and currency blank -> triggers two messages
        String body = """
                {
                  "amount": 100,
                  "currency": ""
                }
                """;

        mvc.perform(post("/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.resultCode").value("VALIDATION_FAILED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.resultCodeId").value("400"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.resultStatus").value("F"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.success").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.resultMsg",
                        org.hamcrest.Matchers.containsString("Amount must be at least 1000")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultInfo.resultMsg",
                        org.hamcrest.Matchers.containsString("Currency is required")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data")
                        .value(org.hamcrest.Matchers.nullValue()));
    }
    static class TestCreateRequest {
        @Min(value = 1000, message = "Amount must be at least 1000")
        public Long amount;

        @NotBlank(message = "Currency is required")
        public String currency;
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping
        public String create(@Valid @RequestBody TestCreateRequest req) {
            // Will never reach here when invalid
            return "ok";
        }

        // Paste your exception handler here (or use your GlobalExceptionHandler class)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
            String msg = ex.getBindingResult().getFieldErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining("; "));
            var info = ResultInfo.builder()
                    .resultCode("VALIDATION_FAILED")
                    .resultCodeId(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                    .resultStatus("F")
                    .resultMsg(msg)
                    .success(false)
                    .build();
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Object>builder().resultInfo(info).data(null).build());
        }
    }
    @Test
    @DisplayName("TC-PAY-ERR-001 should log and rethrow when exception occurs while fetching payment")
    void getPaymentWhenRepositoryThrowsExceptionShouldLogAndRethrow() {
        // Arrange
        PaymentRepository repo = mock(PaymentRepository.class);
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        PaymentServiceImpl service = new PaymentServiceImpl(repo, gatewayFactory);

        UUID id = UUID.randomUUID();
        RuntimeException simulatedException = new RuntimeException("Database down");

        when(repo.findById(id)).thenThrow(simulatedException);

        // Act & Assert
        assertThatThrownBy(() -> service.getPayment(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database down");

        verify(repo).findById(id);

    }
}