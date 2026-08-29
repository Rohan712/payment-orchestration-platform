package com.payment.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class IntegrationServiceException extends BaseServiceException {
    private final String responseBody;

    public IntegrationServiceException(HttpStatus status, String responseBody, String message, Throwable cause) {
        super("INTEGRATION_ERROR", message, status != null ? status : HttpStatus.BAD_GATEWAY);
        this.responseBody = responseBody;
        if (cause != null) initCause(cause);
    }

    public String getResponseBody() {
        return responseBody;
    }
}
