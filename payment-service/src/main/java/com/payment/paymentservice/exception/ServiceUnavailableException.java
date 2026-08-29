package com.payment.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseServiceException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super("SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
        if (cause != null) initCause(cause);
    }
}