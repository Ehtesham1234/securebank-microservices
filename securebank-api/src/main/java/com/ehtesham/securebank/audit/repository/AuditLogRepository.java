package com.ehtesham.securebank.audit.repository;

import com.ehtesham.securebank.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}