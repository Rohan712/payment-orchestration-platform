package com.payment.notificationservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentStatusEvent {
    private String event;
    private String source;
    private String transactionId;
    private String paymentIntentId;
    private String userId;
    private Long amount;
    private String currency;
    private String status;
    private String updatedAt;
    private String correlationId;
    private String idempotencyKey;
}
