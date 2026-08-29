package com.payment.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.payment-events:payment.events}")
    private String paymentTopic;

    @Bean
    public NewTopic paymentEventsTopic() {
        return new NewTopic(paymentTopic, 1, (short) 1);
    }
}
