CREATE TABLE accounts (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          account_number VARCHAR(20) NOT NULL UNIQUE,
                          user_id BIGINT NOT NULL,
                          account_type VARCHAR(20) NOT NULL,
                          account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP
);