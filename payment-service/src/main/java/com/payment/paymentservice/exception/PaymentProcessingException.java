package com.payment.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class PaymentProcessingException extends BaseServiceException {

    public PaymentProcessingException(String message, Throwable cause) {
        super("PAYMENT_PROCESSING_FAILED", message, HttpStatus.INTERNAL_SERVER_ERROR);
        if (cause != null) initCause(cause);
    }
}
