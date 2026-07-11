CREATE TABLE audit_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            action VARCHAR(100) NOT NULL,
                            performed_by VARCHAR(255),
                            entity_type VARCHAR(50),
                            entity_id VARCHAR(255),
                            details TEXT,
                            success BOOLEAN NOT NULL DEFAULT TRUE,
                            error_message TEXT,
                            execution_time_ms BIGINT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);