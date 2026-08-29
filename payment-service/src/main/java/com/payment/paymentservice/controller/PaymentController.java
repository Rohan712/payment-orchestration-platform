package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.*;
import com.payment.paymentservice.service.PaymentServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentServiceImpl service;

    @Value("${flipt.namespace}")
    private String namespace;

     // Create a new payment via Stripe PaymentIntent API.

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPayment(
            @RequestHeader("Idempotency-Key") String idempotentKey,
            @RequestHeader(value = "X-User-Id", required = false) String userIdFromHeader,
            @Valid @RequestBody PaymentCreateRequest req) {

        // Require identity propagated by the gateway
        if (userIdFromHeader == null || userIdFromHeader.isBlank()) {
            log.warn("Missing X-User-Id header on createPayment request. idempotentKey={}", idempotentKey);
            var info = ResultInfo.builder()
                    .resultCode("INVALID_TOKEN")
                    .resultCodeId("4012")
                    .resultStatus("F")
                    .resultMsg("Invalid or expired token")
                    .success(false)
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .resultInfo(info)
                            .data(null)
                            .build());
        }

        // If client also sent userId in body, ignore it — and warn if it mismatches

        // Log incoming request (do not log sensitive data)
        log.info("Received payment creation request. userId={}, idempotentKey={}, amount={}, currency={}",
                userIdFromHeader, idempotentKey, req.amount(), req.currency());

        try {

            // Evaluate feature flag with fallback=false (if evaluation fails, use disabled)
            String finalCurrency = req.currency();

            // Delegate to service layer — ensure service uses this userId parameter (not req.userId)
            var response = service.createPayment(idempotentKey, userIdFromHeader, req, finalCurrency);

            // Determine response code
            HttpStatus status = "Payment intent created".equals(response.resultInfo().resultMsg())
                    ? HttpStatus.CREATED : HttpStatus.OK;

            log.info("Payment creation successful. userId={}, idempotentKey={}, resultCode={}",
                    userIdFromHeader, idempotentKey, response.resultInfo().resultCode());

            return ResponseEntity.status(status).body(response);

        } catch (Exception e) {
            log.error("Unexpected error while creating payment. userId={}, idempotentKey={}, message={}",
                    userIdFromHeader, idempotentKey, e.getMessage(), e);
            throw e;
        }
    }



    // Retrieve payment details by transaction ID.

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> getPayment(
            @PathVariable UUID id) {

        log.info("Received request to fetch payment details. transactionId={}", id);

        try {
            var response = service.getPayment(id);

            if (response.resultInfo() != null && !"S".equalsIgnoreCase(response.resultInfo().resultStatus())) {
                log.warn("Payment not found or failed to fetch. transactionId={}, resultCode={}",
                        id, response.resultInfo().resultCode());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            assert response.resultInfo() != null;
            log.info("Payment details fetched successfully. transactionId={}, status={}",
                    id, response.resultInfo().resultCode());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching payment. transactionId={}, message={}", id, e.getMessage(), e);
            throw e;
        }
    }
}