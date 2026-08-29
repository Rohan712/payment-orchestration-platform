package com.payment.notificationservice.notification;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class NotificationChannelFactory {
    private final List<NotificationChannel> channels;
    public NotificationChannelFactory(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public NotificationChannel get(String type) {
        return channels.stream()
                .filter(c -> c.type().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No channel for type " + type));
    }
}

