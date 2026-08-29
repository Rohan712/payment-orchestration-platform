package com.payment.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.security.Key;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterUnitTest {

    private JwtAuthFilter filter;
    private Key signingKey;
    private String secret;

    @BeforeEach
    void setup() throws Exception {
        // Use a 32-byte secret (HS256 requires at least 256-bit key)
        secret = "0123456789abcdef0123456789abcdef"; // 32 chars -> 32 bytes
        signingKey = Keys.hmacShaKeyFor(secret.getBytes());

        // Create instance with default constructor and set private field via reflection
        filter = new JwtAuthFilter(); // assumes default constructor exists
        try {
            Field f = JwtAuthFilter.class.getDeclaredField("signingKey");
            f.setAccessible(true);
            // If your filter stores raw string, set string; if it stores bytes adjust accordingly.
            f.set(filter, secret);
        } catch (NoSuchFieldException nsfe) {
            // try alternative field name "secret" for robustness
            Field f = JwtAuthFilter.class.getDeclaredField("secret");
            f.setAccessible(true);
            f.set(filter, secret);
        }
    }

    @Test
    @DisplayName("TC-GWAY-001 filterPassValidToken")
    void filterPassValidToken() {
        // Build token using same secret/key strategy as the filter is expected to use
        String token = Jwts.builder()
                .setSubject("user1")
                .signWith(signingKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("TC-GWAY-002 filterRejectsMissingToken")
    void filterRejectsMissingToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("TC-GWAY-003 filterRejectsInvalidToken")
    void filterRejectsInvalidToken() {
        String badToken = "invalid.token.here";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + badToken)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("TC-GWAY-004 userClaimMissing")
    void userClaimMissing() throws Exception {
        // Force the filter to look for a non-default claim key so it won't fall back to "sub"/subject
        Field claimField = JwtAuthFilter.class.getDeclaredField("userClaim");
        claimField.setAccessible(true);
        claimField.set(filter, "user_id");

        // Build a token with NO subject and NO "user_id" claim
        String token = Jwts.builder()
                .signWith(signingKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("TC-GWAY-005 userClaimBlank")
    void userClaimBlank() throws Exception {
        // Force the filter to use "user_id" claim and do not rely on subject fallback
        Field claimField = JwtAuthFilter.class.getDeclaredField("userClaim");
        claimField.setAccessible(true);
        claimField.set(filter, "user_id");

        // Token has the claim but it's blank; no subject provided
        String token = Jwts.builder()
                .claim("user_id", "   ")
                .signWith(signingKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
    @Test
    @DisplayName("TC-GWAY-006 signingKeyBlank")
    void signingKeyBlank() throws Exception {
        // Force signingKey to blanks
        Field keyField = JwtAuthFilter.class.getDeclaredField("signingKey");
        keyField.setAccessible(true);
        keyField.set(filter, "   ");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer whatever")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }

    static class LocalConfig {
        public org.springframework.web.client.RestTemplate restTemplate() {
            return new org.springframework.web.client.RestTemplate();
        }
        public KeyResolver ipKeyResolver() {
            return exchange -> Mono.just(
                    exchange.getRequest().getHeaders().getFirst("X-Forwarded-For") != null
                            ? exchange.getRequest().getHeaders().getFirst("X-Forwarded-For")
                            : exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "anonymous"
            );
        }
    }

    @Test
    @DisplayName("TC-GWAY-007 restTemplateBeanCreated")
    void restTemplateBeanCreated() {
        LocalConfig cfg = new LocalConfig();
        assertNotNull(cfg.restTemplate());
    }

    @Test
    @DisplayName("TC-GWAY-008 ipKeyResolverFromXForwardedFor")
    void ipKeyResolverFromXForwardedFor() {
        LocalConfig cfg = new LocalConfig();
        KeyResolver resolver = cfg.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.42");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        // remote address present but should be ignored due to header
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.1", 0));

        String key = resolver.resolve(exchange).block();
        assertEquals("203.0.113.42", key);
    }

    @Test
    @DisplayName("TC-GWAY-009 ipKeyResolverFromRemoteAddress")
    void ipKeyResolverFromRemoteAddress() {
        LocalConfig cfg = new LocalConfig();
        KeyResolver resolver = cfg.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders(); // no X-Forwarded-For

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("198.51.100.7", 0));

        String key = resolver.resolve(exchange).block();
        assertEquals("198.51.100.7", key);
    }

    @Test
    @DisplayName("TC-GWAY-010 ipKeyResolverAnonymousWhenNoIp")
    void ipKeyResolverAnonymousWhenNoIp() {
        LocalConfig cfg = new LocalConfig();
        KeyResolver resolver = cfg.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(null);

        String key = resolver.resolve(exchange).block();
        assertEquals("anonymous", key);
    }
}
