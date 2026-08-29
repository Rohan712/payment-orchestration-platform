package com.payment.paymentservice.exception;

import com.payment.paymentservice.dto.ApiResponse;
import com.payment.paymentservice.dto.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseServiceException(BaseServiceException ex) {
        log.error("[ServiceError] code={} status={} msg={}", ex.getErrorCode(), ex.getHttpStatus(), ex.getMessageDetail());
        var info = ResultInfo.builder()
                .resultCode(ex.getErrorCode())
                .resultCodeId(String.valueOf(ex.getHttpStatus().value()))
                .resultStatus("F")
                .resultMsg(ex.getMessageDetail())
                .success(false)
                .build();
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.<Object>builder().resultInfo(info).data(null).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        var info = ResultInfo.builder()
                .resultCode("VALIDATION_FAILED")
                .resultCodeId(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .resultStatus("F")
                .resultMsg(msg)
                .success(false)
                .build();
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Object>builder().resultInfo(info).data(null).build());
    }


    // 🔥 single, final catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("[Unhandled] {}", ex.getMessage(), ex);
        var info = ResultInfo.builder()
                .resultCode("UNEXPECTED_ERROR")
                .resultCodeId(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .resultStatus("F")
                .resultMsg("Bad Request")
                .success(false)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Object>builder().resultInfo(info).data(null).build());
    }
}
