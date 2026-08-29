package com.payment.auditservice.repository;

import com.payment.auditservice.entity.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface AuditEventRepository extends MongoRepository<AuditEvent, UUID> {
}
