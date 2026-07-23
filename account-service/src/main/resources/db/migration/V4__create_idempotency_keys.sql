CREATE TABLE idempotency_keys (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  idempotency_key VARCHAR(255) NOT NULL,
                                  user_id BIGINT NOT NULL,
                                  operation_type VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
                                  response_body TEXT,
                                  response_status INT,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT uq_idempotency_key_user
                                      UNIQUE (idempotency_key, user_id)

);