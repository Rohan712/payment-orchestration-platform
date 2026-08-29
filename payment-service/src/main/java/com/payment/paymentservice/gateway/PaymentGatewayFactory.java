package com.payment.paymentservice.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayFactory {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    public PaymentGateway get(String provider) {
        if (provider == null || provider.equalsIgnoreCase("stripe")) {
            return new StripePaymentGateway(stripeSecretKey);
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }
}
