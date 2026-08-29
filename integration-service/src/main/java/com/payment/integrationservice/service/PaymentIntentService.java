package com.payment.integrationservice.service;

import java.util.Map;

public interface PaymentIntentService {
    /**
     * Creates a provider payment intent and returns the minimal response.
     * @param payload request map from payment-service
     * @return map with key "paymentIntentId"
     */
    Map<String, Object> createPaymentIntent(Map<String, Object> payload) throws Exception;
}