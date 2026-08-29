package com.payment.notificationservice.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class GmailEmailChannelTest {


    static class GmailEmailChannelExposed extends GmailEmailChannel {
        // Expose constructor to inject mocks (uses Lombok @RequiredArgsConstructor in prod)
        public GmailEmailChannelExposed(JavaMailSender sender) {
            super(sender);
        }

        public void setFrom(String from) {
            // Directly set the injected field
            try {
                java.lang.reflect.Field field = GmailEmailChannel.class.getDeclaredField("from");
                field.setAccessible(true);
                field.set(this, from);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Test
    @DisplayName("TC-NOT-Provider gmailEmailSend")
    void gmailEmailSend() {
        JavaMailSender sender = mock(JavaMailSender.class);
        GmailEmailChannelExposed channel = new GmailEmailChannelExposed(sender);
        channel.setFrom("noreply@example.com");

        channel.send("to@example.com", "Subject", "Body", java.util.Map.of());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();

        assertEquals("noreply@example.com", msg.getFrom());
        assertArrayEquals(new String[]{"to@example.com"}, msg.getTo());
        assertEquals("Subject", msg.getSubject());
        assertEquals("Body", msg.getText());
    }
}