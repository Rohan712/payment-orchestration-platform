package com.payment.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${jwt.signing-key:}")
    private String signingKey; // HS256 secret

    @Value("${jwt.user-claim:sub}")
    private String userClaim;

    @Value("${jwt.clock-skew-seconds:60}")
    private long clockSkewSeconds;

    // Public endpoints that should NOT require a JWT
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/users/login",
            "/api/v1/users/register",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/actuator/health"
    );

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String path = exchange.getRequest().getPath().value();

        // Allow CORS preflight through
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Skip JWT validation for public endpoints
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        // From here on, JWT is required
        final String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.debug("Missing or malformed Authorization header for {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        final String token = auth.substring(7).trim();

        // Ensure signing key is configured
        if (signingKey == null || signingKey.isBlank()) {
            log.error("JWT signing key not configured");
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }

        try {
            Key key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));

            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setAllowedClockSkewSeconds(clockSkewSeconds) // tolerate small time drift
                    .build()
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();
            String userId = Optional.ofNullable(claims.get(userClaim, String.class))
                    .orElseGet(claims::getSubject);

            if (userId == null || userId.isBlank()) {
                log.warn("User id claim '{}' missing for path {}", userClaim, path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Remove any client-supplied X-User-Id and set the trusted one
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.set("X-User-Id", userId);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (Exception ex) {
            log.error("JWT validation failed for {} : {}", path, ex.getMessage());
            String body =String.format( """
    {
        "resultInfo": {
            "resultCode": "VALIDATION_FAILED",
            "resultCodeId": "400",
            "resultStatus": "F",
            "resultMsg": "Unauthorized access",
            "success": false
        },
        "data": null
    }
    """,path);

            return writeJson(exchange, HttpStatus.UNAUTHORIZED, body);
        }
    }
    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String jsonBody) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
    @Override
    public int getOrder() {
        return -1; // run early
    }
}
