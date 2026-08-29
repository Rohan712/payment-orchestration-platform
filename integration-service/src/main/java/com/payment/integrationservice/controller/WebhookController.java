package com.payment.integrationservice.controller;

import com.payment.integrationservice.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService service;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    // To get the webhook event
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> stripeWebhook(HttpServletRequest request) throws IOException {
       log.info("Inside webhookController.stripeWebhook");
        String payload = getBody(request);
        String signature = request.getHeader("Stripe-Signature");
        log.info("------Payload: {}", payload);
        var resp = service.handleStripeWebhook(payload, signature, stripeWebhookSecret);
        return ResponseEntity.ok(resp);
    }

    private String getBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

