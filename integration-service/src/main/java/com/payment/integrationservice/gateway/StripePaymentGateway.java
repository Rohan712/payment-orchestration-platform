package com.payment.integrationservice.gateway;

import com.payment.integrationservice.dto.PaymentEvent;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final String apiKey;

    public StripePaymentGateway(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Map<String, Object> createPaymentIntent(PaymentEvent event) throws Exception {
        log.info("Inside StripePaymentGateway.createPaymentIntent");
        Stripe.apiKey = apiKey;
        Map<String, Object> params = new HashMap<>();
        params.put("amount", event.amount());
        params.put("currency", event.currency());
        params.put("payment_method", event.paymentMethodId());
        params.put("payment_method_types", List.of("card"));
        params.put("confirm", true);
        params.put("description", event.description());

// metadata for correlation
        Map<String, String> metadata = new HashMap<>();
        metadata.put("transactionId", event.transactionId());
        metadata.put("idempotencyKey", event.idempotencyKey());
        metadata.put("userId", event.userId());
        params.put("metadata", metadata);



        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(event.idempotencyKey())
                .build();

        PaymentIntent intent = PaymentIntent.create(params, options);

        Map<String, Object> res = new HashMap<>();
        res.put("stripePaymentIntentId", intent.getId());
        res.put("clientSecret", intent.getClientSecret());
        res.put("status", intent.getStatus());
        return res;
    }
}