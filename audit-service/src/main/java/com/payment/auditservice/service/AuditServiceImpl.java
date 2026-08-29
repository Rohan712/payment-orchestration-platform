package com.payment.auditservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.payment.auditservice.entity.AuditEvent;
import com.payment.auditservice.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    private final AuditEventRepository repo;

    @Override
    @Transactional
    public void recordEvent(String eventType, String correlationId, String actorUserId,
                            String resourceType, String resourceId, String idempotencyKey,
                            JsonNode request, JsonNode response, JsonNode error,
                            OffsetDateTime occurredAt) {
        try {
            log.info("Inside AuditServiceImpl.recordEvent");
            Instant timestamp = (occurredAt == null)
                    ? Instant.now()
                    : occurredAt.toInstant();

            AuditEvent e = AuditEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .occurredAt(timestamp)
                    .correlationId(correlationId)
                    .actorUserId(actorUserId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .idempotencyKey(idempotencyKey)
                    .request(request)
                    .response(response)
                    .error(error)
                    .build();
            AuditEvent saved = repo.save(e);
            log.info("Audit saved eventId={} type={} resource={}", saved.getEventId(), eventType, resourceId);

        }catch (Exception e){
            log.error("Error while recording an event");
        }

    }

}

