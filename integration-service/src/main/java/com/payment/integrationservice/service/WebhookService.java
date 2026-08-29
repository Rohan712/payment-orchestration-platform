package com.payment.integrationservice.service;

import java.util.Map;

public interface WebhookService {
    public Map<String, Object> handleStripeWebhook(String payload, String signatureHeader, String endpointSecret);
}
