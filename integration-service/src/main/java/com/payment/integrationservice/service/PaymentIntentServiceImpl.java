package com.payment.integrationservice.service;

import com.payment.integrationservice.dto.PaymentEvent;
import com.payment.integrationservice.gateway.PaymentGateway;
import com.payment.integrationservice.gateway.PaymentGatewayFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentServiceImpl implements PaymentIntentService {

    private final PaymentGatewayFactory gatewayFactory;

    @Override
    public Map<String, Object> createPaymentIntent(Map<String, Object> payload) throws Exception {

        // --- Step 1: Extract and validate currency + amount
        final String currency = (String) payload.get("currency");
        final BigDecimal amountMajor = toBigDecimal(payload.get("amount"));

        final int exponent = currencyExponent(currency);
        final BigDecimal stripeAmountMinor = toStripeMinorUnits(amountMajor, exponent);

        log.info("Validated amount. major={}, minorUnits={}, currency={}, exponent={}",
                amountMajor, stripeAmountMinor, currency, exponent);

        // --- Step 2: Build event (store BigDecimal)
        PaymentEvent event = PaymentEvent.builder()
                .event((String) payload.getOrDefault("event", "PAYMENT_REQUESTED"))
                .transactionId((String) payload.get("transactionId"))
                .idempotencyKey((String) payload.get("idempotencyKey"))
                .userId((String) payload.get("userId"))
                .amount(stripeAmountMinor) // BigDecimal minor-unit amount (for Stripe)
                .currency(currency)
                .paymentMethodId((String) payload.get("paymentMethodId"))
                .description((String) payload.get("description"))
                .provider((String) payload.getOrDefault("provider", "stripe"))
                .build();

        // --- Step 3: Select Gateway
        PaymentGateway gateway = gatewayFactory.get(event.provider());

        log.info("Creating Payment Intent for PSP {}", event.provider());

        // --- Step 4: Call gateway and normalize response
        Map<String, Object> gatewayResp = gateway.createPaymentIntent(event);

        String paymentIntentId = (String) gatewayResp.getOrDefault(
                "paymentIntentId",
                gatewayResp.get("stripePaymentIntentId")
        );

        Map<String, Object> intentId = new HashMap<>();
        intentId.put("paymentIntentId", paymentIntentId);
        return intentId;
    }

    // --- Helper Methods ---

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + value, e);
        }
    }

    private static int currencyExponent(String currency) {
        try {
            return Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }

    /**
     * Converts a major-unit amount (e.g. ₹50000.12) into minor units (e.g. 5000012 paise)
     * as BigDecimal, maintaining decimal precision for gateway logging.
     */
    private static BigDecimal toStripeMinorUnits(BigDecimal major, int exponent) {
        if (major == null) throw new IllegalArgumentException("Amount is required");

        // Example:
        //   INR/USD (exponent=2): 50000.12 → 5000012
        //   JPY (exponent=0): 50000 → 50000
        // Rounds HALF_UP and strips any trailing zeros
        return major
                .movePointRight(exponent)
                .setScale(0, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }
}
