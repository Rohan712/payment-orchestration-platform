package com.payment.notificationservice.notification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GmailEmailChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(GmailEmailChannel.class);

    private final JavaMailSender mailSender;

    @Value("${notification.email.from}")
    private String from;

    @Override
    public void send(String to, String subject, String body, Map<String, Object> meta) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Sent email to {}", to);
    }

    @Override
    public String type() { return "EMAIL"; }
}
