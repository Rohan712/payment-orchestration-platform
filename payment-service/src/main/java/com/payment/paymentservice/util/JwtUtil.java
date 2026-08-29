package com.payment.paymentservice.util;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private static final String ISSUER = "payment-service";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TX_ID = "transactionId";
    private static final String SCOPE_PAYMENTS_CREATE = "payments:create";
;

    @Value("${integration.jwt.secret}")
    private String integrationJwtSecret;

    public String buildServiceJwt(final String userId, final UUID txId) {
        final Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId)
                .withClaim(CLAIM_SCOPE, SCOPE_PAYMENTS_CREATE)
                .withClaim(CLAIM_TX_ID, txId.toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(300)))
                .sign(Algorithm.HMAC256(integrationJwtSecret));
    }
}
