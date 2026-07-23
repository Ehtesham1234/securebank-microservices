package com.ehtesham.account_service.audit.component;



import com.ehtesham.account_service.audit.annotation.Auditable;
import com.ehtesham.account_service.audit.entity.AuditLog;
import com.ehtesham.account_service.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditAspect(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(
            ProceedingJoinPoint joinPoint,
            Auditable auditable) throws Throwable {

        long startTime = System.currentTimeMillis();

        String performedBy = extractCurrentUser();
        String action = auditable.action().isEmpty()
                ? joinPoint.getSignature().getName()
                : auditable.action();

        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setPerformedBy(performedBy);

        try {
            Object result = joinPoint.proceed();

            auditLog.setSuccess(true);
            auditLog.setExecutionTimeMs(
                    System.currentTimeMillis() - startTime);

            saveAuditLog(auditLog);
            return result;

        } catch (Throwable ex) {

            auditLog.setSuccess(false);
            auditLog.setErrorMessage(ex.getMessage());
            auditLog.setExecutionTimeMs(
                    System.currentTimeMillis() - startTime);

            saveAuditLog(auditLog);
            throw ex;
        }
    }

    private String extractCurrentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private void saveAuditLog(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // NEVER let audit logging failure break the actual business operation
            // log to SLF4J as fallback but swallow the exception
            org.slf4j.LoggerFactory
                    .getLogger(AuditAspect.class)
                    .error("Failed to save audit log: {}", e.getMessage());
        }
    }
}