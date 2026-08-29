package com.payment.auditservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.auditservice.entity.AuditEvent;
import com.payment.auditservice.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditEventRepository repo;

    @InjectMocks
    private AuditServiceImpl service;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("TC-AUD-006 recordEventSavesEventWithProvidedFields")
    void recordEventSavesEventWithProvidedFields() throws Exception {
        JsonNode req = mapper.readTree("{\"foo\":\"bar\"}");
        JsonNode resp = mapper.readTree("{\"ok\":true}");

        service.recordEvent(
                "EVENT_TYPE",
                "corr-123",
                "user-1",
                "RESOURCE",
                "res-1",
                "idem-1",
                req,
                resp,
                null,
                OffsetDateTime.now()
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo, times(1)).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("EVENT_TYPE");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-123");
        assertThat(saved.getActorUserId()).isEqualTo("user-1");
        assertThat(saved.getResourceType()).isEqualTo("RESOURCE");
        assertThat(saved.getResourceId()).isEqualTo("res-1");
        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(saved.getRequest()).isEqualTo(req);
        assertThat(saved.getResponse()).isEqualTo(resp);
    }

    @Test
    @DisplayName("TC-AUD-007 recordEventWhenRepoThrowsLogsAndDoesNotPropagate")
    void recordEventWhenRepoThrowsLogsAndDoesNotPropagate() {
        JsonNode req = mapper.createObjectNode();
        doThrow(new RuntimeException("db down")).when(repo).save(any(AuditEvent.class));
        service.recordEvent("T", "c", "u", "rt", "rid", null, req, null, null, null);
        verify(repo, times(1)).save(any(AuditEvent.class));
    }
}
