package com.payment.auditservice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private UUID eventId;             // maps your 'event_id' (uuid)

    @Indexed
    private String eventType;         // event_type (text, not null)

    private Instant occurredAt; // occured_at (timestamp)

    @Indexed
    private String correlationId;     // correlation_id (text)

    private String actorUserId;       // actor_user_id (text)

    private String resourceType;      // resource_type (text)

    @Indexed
    private String resourceId;        // resource_id (text)

    private String idempotencyKey;    // idempotency_key (text)

    private JsonNode request;         // request (json)
    private JsonNode response;        // response (json)
    private JsonNode error;           // error (json)
}
