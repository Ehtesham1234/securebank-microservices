CREATE TABLE kyc_documents (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               document_type VARCHAR(30) NOT NULL,
                               document_number VARCHAR(50) NOT NULL,
                               file_path VARCHAR(500) NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               rejection_reason VARCHAR(500),
                               verified_by BIGINT,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
                               INDEX idx_user_id (user_id),
                               INDEX idx_status (status)
);