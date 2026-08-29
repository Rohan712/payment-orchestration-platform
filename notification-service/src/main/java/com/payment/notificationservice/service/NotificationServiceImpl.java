package com.payment.notificationservice.service;

import com.payment.notificationservice.client.UserClient;
import com.payment.notificationservice.model.PaymentStatusEvent;
import com.payment.notificationservice.notification.NotificationChannel;
import com.payment.notificationservice.notification.NotificationChannelFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final UserClient userClient;
    private final NotificationChannelFactory channelFactory;

    @Override
    public void notifyUser(PaymentStatusEvent event) {
        if (event == null || event.getUserId() == null) {
            log.warn("Skipping notification, missing userId in event: {}", event);
            return;
        }

        try {
            // call user service to get user's details
            var user = userClient.getUserById(event.getUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("User not found or has no email: userId={}", event.getUserId());
                return;
            }

            // Select channel
            NotificationChannel email = channelFactory.get("EMAIL");
            log.info("getting subject and amount");
            String subject = "Your payment status: " + event.getStatus();
            String amountStr = (event.getAmount() != null)
                    ? NumberFormat.getInstance(Locale.ENGLISH).format(event.getAmount() / 100.0) + " " + event.getCurrency()
                    : "N/A";

            String txRef = (event.getTransactionId() != null)
                    ? event.getTransactionId()
                    : event.getPaymentIntentId();
            log.info("getting recipient");
            String recipientName = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : user.getEmail();
            log.info("sending email");
            String body = String.format(
                    "Hi %s," +
                            "Your payment (txn: %s) is now '%s'." +
                            "Amount: %s" +
                            "Thank you!",
                    recipientName, txRef, event.getStatus(), amountStr
            );
            log.info("sending email body {}", user.getEmail());
            log.info("Event Details {}, {}, {}",event.getTransactionId(),event.getPaymentIntentId(),event.getStatus());
            email.send(
                    user.getEmail(),
                    subject,
                    body,
                    Map.of(
                            "transactionId", event.getTransactionId(),
                            "status", event.getStatus()
                    )
            );

            log.info("Notification sent to {} for txn {}", user.getEmail(), txRef);

        } catch (Exception e) {
            log.error("Failed to send notification for userId={}, event={}", event.getUserId(), event, e);
        }
    }
}

