package com.payment.integrationservice.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PaymentEvent(
        String event,
        String transactionId,
        String idempotencyKey,
        String userId,
        BigDecimal amount,
        String currency,
        String paymentMethodId,
        String description,
        String provider
) {}
