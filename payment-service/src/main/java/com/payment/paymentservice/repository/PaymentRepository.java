package com.payment.paymentservice.repository;


import com.payment.paymentservice.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByIdempotentKey(String key);
    Optional<PaymentTransaction> findByPaymentIntentId(String key);
}
