package com.payment.notificationservice.notification;



import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.util.List;


import static org.junit.jupiter.api.Assertions.*;


class NotificationChannelFactoryTest {


    static class DummyChannel implements NotificationChannel {
        public void send(String to, String subject, String body, java.util.Map<String, Object> meta) { /* */ }
        public String type() { return "DUMMY"; }
    }


    // Not numbered test (utility class coverage). Still keeps >90% by simple tests.
    @Test
    @DisplayName("TC-NOT-Factory getAndMissingChannel")
    void getAndMissingChannel() {
        NotificationChannel dummy = new DummyChannel();
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(dummy));


// happy path
        assertSame(dummy, factory.get("DUMMY"));
        assertSame(dummy, factory.get("dummy")); // case-insensitive


// invalid channel
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.get("EMAIL"));
        assertTrue(ex.getMessage().contains("No channel for type"));
    }
}