package com.payment.integrationservice.controller;

import com.payment.integrationservice.gateway.PaymentGatewayFactory;
import com.payment.integrationservice.service.PaymentIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/integrations")
@RequiredArgsConstructor
public class IntegrationPaymentsController {

    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentIntentService paymentIntentService;

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody Map<String, Object> payload) throws Exception {
        log.info("Integration REST createPayment called");

        Map<String, Object> resp = paymentIntentService.createPaymentIntent(payload);
        return ResponseEntity.ok(resp);
    }
}
