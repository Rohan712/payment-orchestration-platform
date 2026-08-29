package com.payment.notificationservice.consumer;

import com.payment.notificationservice.model.PaymentStatusEvent;
import com.payment.notificationservice.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class PaymentStatusConsumerTest {


    @Test
    @DisplayName("TC-NOT-Consumer onPaymentStatusParsesAndDelegates")
    void onPaymentStatusParsesAndDelegates() {
        NotificationService svc = mock(NotificationService.class);
        PaymentStatusConsumer consumer = new PaymentStatusConsumer(svc);


        String json = "{" +
                "\"userId\":\"user-777\"," +
                "\"transactionId\":\"txn-777\"," +
                "\"amount\":1999," +
                "\"currency\":\"INR\"," +
                "\"status\":\"SUCCESS\"" +
                "}";


        assertDoesNotThrow(() -> consumer.onPaymentStatus(json));
        ArgumentCaptor<PaymentStatusEvent> cap = ArgumentCaptor.forClass(PaymentStatusEvent.class);
        verify(svc).notifyUser(cap.capture());
        PaymentStatusEvent e = cap.getValue();
        assertEquals("user-777", e.getUserId());
        assertEquals("txn-777", e.getTransactionId());
        assertEquals(1999L, e.getAmount());
        assertEquals("INR", e.getCurrency());
        assertEquals("SUCCESS", e.getStatus());
    }


    @Test
    @DisplayName("TC-NOT-Consumer invalidJsonIsCaught")
    void invalidJsonIsCaught() {
        NotificationService svc = mock(NotificationService.class);
        PaymentStatusConsumer consumer = new PaymentStatusConsumer(svc);
        assertDoesNotThrow(() -> consumer.onPaymentStatus("not-json"));
        verifyNoInteractions(svc);
    }
}
