CREATE TABLE fixed_deposit_details (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       account_id BIGINT NOT NULL UNIQUE,
                                       principal_amount DECIMAL(19,4) NOT NULL,
                                       interest_rate DECIMAL(5,2) NOT NULL,
                                       duration_months INT NOT NULL,
                                       maturity_date DATE NOT NULL,
                                       maturity_amount DECIMAL(19,4) NOT NULL,
                                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                       CONSTRAINT fk_fd_account
                                           FOREIGN KEY (account_id) REFERENCES accounts(id)
);