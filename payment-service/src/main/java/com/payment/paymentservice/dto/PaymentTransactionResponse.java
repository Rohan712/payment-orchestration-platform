package com.payment.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentTransactionResponse(
        String transactionId,
        String userId,
        BigDecimal amount,
        String currency,
        String status,
        String stripePaymentIntentId,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime updatedAt
) {}