CREATE TABLE refresh_tokens (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                token VARCHAR(255) NOT NULL UNIQUE,
                                user_id BIGINT NOT NULL,
                                expiry_date DATETIME NOT NULL,
                                revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
                                CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);