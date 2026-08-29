package com.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayApplicationTests {

    @Test
    @DisplayName("TC-GWAY-011 restTemplateBeanCreated")
    void restTemplateBeanCreated() {
        GatewayApplication app = new GatewayApplication();
        RestTemplate rt = app.restTemplate();
        assertNotNull(rt);
    }

    @Test
    @DisplayName("TC-GWAY-012 ipKeyResolverFromXForwardedFor")
    void ipKeyResolverFromXForwardedFor() {
        GatewayApplication app = new GatewayApplication();
        KeyResolver resolver = app.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.42");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.1", 0));

        String key = resolver.resolve(exchange).block();
        assertEquals("203.0.113.42", key);
    }

    @Test
    @DisplayName("TC-GWAY-013 ipKeyResolverFromRemoteAddress")
    void ipKeyResolverFromRemoteAddress() {
        GatewayApplication app = new GatewayApplication();
        KeyResolver resolver = app.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("198.51.100.7", 0));

        String key = resolver.resolve(exchange).block();
        assertEquals("198.51.100.7", key);
    }

    @Test
    @DisplayName("TC-GWAY-014 ipKeyResolverAnonymousWhenNoIp")
    void ipKeyResolverAnonymousWhenNoIp() {
        GatewayApplication app = new GatewayApplication();
        KeyResolver resolver = app.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(null);

        String key = resolver.resolve(exchange).block();
        assertEquals("anonymous", key);
    }

    @Test
    @DisplayName("TC-GWAY-015 ipKeyResolverMultipleForwardedForNoSplit")
    void ipKeyResolverMultipleForwardedForNoSplit() {
        GatewayApplication app = new GatewayApplication();
        KeyResolver resolver = app.ipKeyResolver();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "10.0.0.1, 203.0.113.5, 198.51.100.9");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 0));

        // Your code returns the header as-is (no splitting).
        String key = resolver.resolve(exchange).block();
        assertEquals("10.0.0.1, 203.0.113.5, 198.51.100.9", key);
    }
}
