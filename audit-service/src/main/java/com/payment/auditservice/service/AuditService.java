package com.payment.auditservice.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

@SuppressWarnings("java:S107")
public interface AuditService {
    void recordEvent(String eventType, String correlationId, String actorUserId,
                     String resourceType, String resourceId, String idempotencyKey,
                     JsonNode request, JsonNode response, JsonNode error,
                     OffsetDateTime occurredAt);
}

