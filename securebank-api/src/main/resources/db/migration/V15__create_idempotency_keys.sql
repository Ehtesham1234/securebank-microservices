CREATE TABLE idempotency_keys (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                                  user_id BIGINT NOT NULL,
                                  response_body TEXT,
                                  response_status INT,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_idempotency_user
                                      FOREIGN KEY (user_id) REFERENCES users(id)
);