package com.payment.paymentservice.service;

import com.payment.paymentservice.dto.ApiResponse;
import com.payment.paymentservice.dto.PaymentCreateRequest;
import com.payment.paymentservice.dto.PaymentTransactionResponse;
import com.stripe.exception.StripeException;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    ApiResponse<Map<String, Object>> createPayment(String idempotentKey, String userId, PaymentCreateRequest req, String currency) throws StripeException;
    ApiResponse<PaymentTransactionResponse> getPayment(UUID id);
}
