package com.payment.paymentservice.exception;

import com.payment.paymentservice.dto.ApiResponse;
import com.payment.paymentservice.dto.ResultInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static class TestServiceException extends BaseServiceException {
        public TestServiceException(String errorCode, String messageDetail, HttpStatus httpStatus) {
            super(errorCode, messageDetail, httpStatus);
        }
    }

    @Test
    @DisplayName("TC-EXC-001 handleBaseServiceException returns proper ApiResponse and logs error")
    void handleBaseServiceException_shouldReturnExpectedResponse() {
        // Arrange
        BaseServiceException ex = new TestServiceException(
                "PAYMENT_FAILED",
                "Payment processing failed",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        // Act
        ResponseEntity<ApiResponse<Object>> response = handler.handleBaseServiceException(ex);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.resultInfo()).isNotNull();

        ResultInfo info = body.resultInfo();
        assertThat(info.resultCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(info.resultMsg()).isEqualTo("Payment processing failed");
        assertThat(info.resultCodeId()).isEqualTo(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        assertThat(info.resultStatus()).isEqualTo("F");
        assertThat(info.success()).isFalse();
        assertThat(body.data()).isNull();
    }

    @Test
    @DisplayName("TC-EXC-002 handleMethodArgumentNotValid returns validation failure response")
    void handleMethodArgumentNotValid_shouldReturnValidationErrorResponse() {
        // Arrange: mock MethodArgumentNotValidException
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("paymentCreateRequest", "amount", "Amount must be positive"),
                new FieldError("paymentCreateRequest", "currency", "Currency must be 3 letters")
        ));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<ApiResponse<Object>> response = handler.handleMethodArgumentNotValid(ex);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.resultInfo()).isNotNull();

        ResultInfo info = body.resultInfo();
        assertThat(info.resultCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(info.resultMsg()).contains("Amount must be positive");
        assertThat(info.resultMsg()).contains("Currency must be 3 letters");
        assertThat(info.resultCodeId()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        assertThat(info.success()).isFalse();
    }
}
