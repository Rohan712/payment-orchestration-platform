package com.payment.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.notificationservice.model.PaymentStatusEvent;
import com.payment.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${kafka.topic.payment-status-events:payment.status}",
            groupId = "${kafka.consumer.group-id:notification-service}"
    )
    public void onPaymentStatus(String message) {
        try {
            PaymentStatusEvent event = mapper.readValue(message, PaymentStatusEvent.class);
            log.info("Consumed payment.status: txnId={}, userId={}, status={}", event.getTransactionId(), event.getUserId(), event.getStatus());
            notificationService.notifyUser(event);
        } catch (Exception e) {
            log.error("Failed to process payment.status message", e);
        }
    }
}