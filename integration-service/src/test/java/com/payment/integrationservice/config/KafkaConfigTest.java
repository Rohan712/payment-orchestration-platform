package com.payment.integrationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigTest {

    @Test
    void createsTopicsUsingInjectedNames() throws Exception {
        KafkaConfig cfg = new KafkaConfig();

        Field webhookField = KafkaConfig.class.getDeclaredField("webhookTopic");
        webhookField.setAccessible(true);
        webhookField.set(cfg, "webhook.events.test");

        Field paymentField = KafkaConfig.class.getDeclaredField("paymentTopic");
        paymentField.setAccessible(true);
        paymentField.set(cfg, "payment.events.test");

        NewTopic webhook = cfg.webhookEventsTopic();
        NewTopic payment = cfg.paymentEventsTopic();

        assertEquals("webhook.events.test", webhook.name());
        assertEquals("payment.events.test", payment.name());
        assertEquals(1, webhook.numPartitions());
        assertEquals(1, payment.numPartitions());
    }
}