package com.payment.paymentservice.service;

import com.payment.paymentservice.dto.*;
import com.payment.paymentservice.entity.PaymentTransaction;
import com.payment.paymentservice.exception.PaymentProcessingException;
import com.payment.paymentservice.gateway.PaymentGateway;
import com.payment.paymentservice.gateway.PaymentGatewayFactory;
import com.payment.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    // ---------- Constants ----------
    public static final String TRANSACTION_ID = "transactionId";
    public static final String CURRENCY = "currency";
    public static final String AMOUNT = "amount";
    private static final String STATUS_PENDING = "pending";
    public static final String PAYMENT_REQUESTED = "PAYMENT_REQUESTED";
    public static final String USER_ID = "userId";
    public static final String DESCRIPTION = "description";
    public static final String STRIPE = "stripe";
    public static final String PAYMENT_INTENT_ID = "paymentIntentId";

    private final PaymentRepository repo;
    private final PaymentGatewayFactory gatewayFactory;

    @Transactional
    @Override
    public ApiResponse<Map<String, Object>> createPayment(
            final String idempotentKey,
            final String userId,
            final PaymentCreateRequest req,
            final String currency
    ) {
        log.info("Creating payment idempotentKey={}, userId={}, amount={}, currency={}",
                idempotentKey, userId, req.amount(), currency);

        // 1) Idempotency check
        final Optional<PaymentTransaction> existing = repo.findByIdempotentKey(idempotentKey);
        if (existing.isPresent()) {
            log.warn("Idempotent replay detected. Returning existing transaction. idempotentKey={}", idempotentKey);
            return buildRequestedResponse(existing.get());
        }

        // 2) Prepare new transaction (not yet persisted)
        final UUID txId = UUID.randomUUID();
        log.info("Prepared transaction (not yet persisted). transactionId={}", txId);

        final PaymentTransaction tx = newPendingTransaction(idempotentKey, userId, req, currency, txId);

        // Convert major units (e.g., 19.99) to minor units (e.g., 1999) for gateways like Stripe
        final BigDecimal amountMajor = req.amount();
        final int exponent = currencyExponent(currency);
        final BigDecimal amountMinor = toStripeMinorUnits(amountMajor, exponent);

        // 3) Call provider through gateway
        final String provider = STRIPE; // default; adjust if request supports provider override
        final PaymentEvent event = PaymentEvent.builder()
                .event(PAYMENT_REQUESTED)
                .transactionId(txId.toString())
                .idempotencyKey(idempotentKey)
                .userId(userId)
                .amount(amountMinor)              // minor-unit amount for Stripe
                .currency(currency)
                .paymentMethodId(req.paymentMethodId())
                .description(req.description())
                .provider(provider)
                .build();

        try {
            PaymentGateway gateway = gatewayFactory.get(provider);
            Map<String, Object> gatewayResp = gateway.createPaymentIntent(event);

            String paymentIntentId = (String) gatewayResp.getOrDefault(
                    PAYMENT_INTENT_ID,
                    gatewayResp.get("stripePaymentIntentId")
            );
            tx.setPaymentIntentId(paymentIntentId);

        } catch (Exception e) {
            throw new PaymentProcessingException("Unexpected error requesting payment creation", e);
        }

        // 4) Persist once (after integration call)
        repo.save(tx);
        log.info("Transaction persisted exactly once. transactionId={}", tx.getId());

        // 5) Return response
        return buildRequestedResponse(tx);
    }

    // ---------- Helper methods ----------

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
        return major
                .movePointRight(exponent)
                .setScale(0, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private PaymentTransaction newPendingTransaction(
            final String idempotentKey,
            final String userId,
            final PaymentCreateRequest req,
            final String currency,
            final UUID txId
    ) {
        final PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .idempotentKey(idempotentKey)
                .userId(userId)
                .amount(req.amount())
                .currency(currency)
                .paymentMethod(req.paymentMethodId())
                .status(STATUS_PENDING)
                .description(req.description())
                .build();
        tx.markNew();
        return tx;
    }

    private ApiResponse<Map<String, Object>> buildRequestedResponse(PaymentTransaction tx) {
        String transactionId = (tx.getId() != null) ? tx.getId().toString() : null;
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put(TRANSACTION_ID, transactionId);
        data.put(AMOUNT, tx.getAmount());         // can be null
        data.put(CURRENCY, tx.getCurrency());     // can be null
        data.put("status", tx.getStatus());       // can be null

        var info = ResultInfo.builder()
                .resultCode(PAYMENT_REQUESTED)
                .resultCodeId("3000")
                .resultStatus("S")
                .resultMsg("Payment creation requested; processing asynchronously")
                .success(true)
                .build();

        return ApiResponse.<Map<String, Object>>builder()
                .resultInfo(info)
                .data(data)
                .build();
    }
    @Override
    public ApiResponse<PaymentTransactionResponse> getPayment(UUID id) {
        log.info("Fetching payment details. transactionId={}", id);

        PaymentTransaction tx = repo.findById(id).orElse(null);
        if (tx == null) {
            log.warn("Payment not found. transactionId={}", id);

            var info = ResultInfo.builder()
                    .resultCode("NOT_FOUND")
                    .resultCodeId("404")
                    .resultStatus("F")
                    .resultMsg("Payment not found")
                    .success(false)
                    .build();

            return ApiResponse.<PaymentTransactionResponse>builder()
                    .resultInfo(info)
                    .data(null)
                    .build();
        }

        PaymentTransactionResponse dto = new PaymentTransactionResponse(
                tx.getId().toString(),
                tx.getUserId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getPaymentIntentId(),
                tx.getDescription(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );

        var info = ResultInfo.builder()
                .resultCode("INQUIRE_SUCCESS")
                .resultCodeId("0018")
                .resultStatus("S")
                .resultMsg("Payment details fetched successfully")
                .success(true)
                .build();

        return ApiResponse.<PaymentTransactionResponse>builder()
                .resultInfo(info)
                .data(dto)
                .build();
    }
}
