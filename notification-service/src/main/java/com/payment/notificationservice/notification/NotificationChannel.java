package com.payment.notificationservice.notification;

import java.util.Map;

public interface NotificationChannel {
    void send(String to, String subject, String body, Map<String, Object> meta) throws Exception;
    String type();
}
