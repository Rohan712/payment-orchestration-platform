package com.payment.paymentservice.gateway;



import com.payment.paymentservice.dto.PaymentEvent;

import java.util.Map;

public interface PaymentGateway {
    Map<String, Object> createPaymentIntent(PaymentEvent event) throws Exception;
}
