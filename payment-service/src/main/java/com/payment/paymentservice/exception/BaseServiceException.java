package com.payment.paymentservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all custom service exceptions.
 * Provides a consistent structure for error handling across services.
 */
@Getter
public abstract class BaseServiceException extends RuntimeException {

    private final String errorCode;
    private final String messageDetail;
    private final HttpStatus httpStatus;

    protected BaseServiceException(String errorCode, String messageDetail, HttpStatus httpStatus) {
        super(messageDetail);
        this.errorCode = errorCode;
        this.messageDetail = messageDetail;
        this.httpStatus = httpStatus;
    }
}
