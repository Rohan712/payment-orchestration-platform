package com.payment.notificationservice.service;

import com.payment.notificationservice.model.PaymentStatusEvent;

public interface NotificationService {
    void notifyUser(PaymentStatusEvent event);
}
