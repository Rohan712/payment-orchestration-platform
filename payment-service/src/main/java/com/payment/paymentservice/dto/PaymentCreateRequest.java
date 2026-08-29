package com.payment.paymentservice.dto;

import jakarta.validation.constraints.*;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

public record PaymentCreateRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 1000, message = "Amount must be at least 1000")
        @Max(value = 1000000, message = "Amount cannot exceed 10,00,000")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        @Pattern(regexp = "^[A-Za-z]+$", message = "Currency must contain only alphabets")
        String currency,

        @NotBlank(message = "Payment Method is required")
        @Size(min = 3, max = 32, message = "Payment Method must be between 8 and 128 characters")
        String paymentMethodId,

        @NotBlank(message = "Description is required")
        String description,

        @Size(min = 3, max = 32, message = "Payment Service Provider must be between 8 and 128 characters")
        @Nullable
        String psp
) {}
