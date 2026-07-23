package com.ehtesham.kyc_service.audit.repository;


import com.ehtesham.kyc_service.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}