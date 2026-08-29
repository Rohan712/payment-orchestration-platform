package com.payment.integrationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    @Value("${kafka.topic.webhook-events}")
    private String webhookTopic;

    @Value("${kafka.topic.payment-events}")
    private String paymentTopic;

    @Bean
    public NewTopic webhookEventsTopic() { return new NewTopic(webhookTopic, 1, (short)1); }

    @Bean
    public NewTopic paymentEventsTopic() { return new NewTopic(paymentTopic, 1, (short)1); }
}