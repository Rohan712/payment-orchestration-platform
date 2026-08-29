package com.payment.integrationservice.gateway;

import com.payment.integrationservice.dto.PaymentEvent;
import java.util.Map;

public interface PaymentGateway {
    Map<String, Object> createPaymentIntent(PaymentEvent event) throws Exception;
}
