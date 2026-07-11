CREATE TABLE cards (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       card_number VARCHAR(19) NOT NULL UNIQUE,
                       masked_number VARCHAR(19) NOT NULL,
                       user_id BIGINT NOT NULL,
                       account_id BIGINT,
                       card_type VARCHAR(20) NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                       expiry_date DATE NOT NULL,
                       cvv_hash VARCHAR(255) NOT NULL,
                       daily_limit DECIMAL(19,4),
                       credit_limit DECIMAL(19,4),
                       available_credit DECIMAL(19,4),
                       outstanding_bill DECIMAL(19,4) DEFAULT 0,
                       billing_cycle_day INT,
                       due_date DATE,
                       card_holder_name VARCHAR(100) NOT NULL,
                       version BIGINT NOT NULL DEFAULT 0,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       CONSTRAINT fk_card_user FOREIGN KEY (user_id) REFERENCES users(id),
                       CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE credit_card_statements (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        card_id BIGINT NOT NULL,
                                        billing_period_start DATE NOT NULL,
                                        billing_period_end DATE NOT NULL,
                                        total_spent DECIMAL(19,4) NOT NULL DEFAULT 0,
                                        total_paid DECIMAL(19,4) NOT NULL DEFAULT 0,
                                        opening_balance DECIMAL(19,4) NOT NULL,
                                        closing_balance DECIMAL(19,4) NOT NULL,
                                        minimum_due DECIMAL(19,4) NOT NULL,
                                        due_date DATE NOT NULL,
                                        paid BOOLEAN NOT NULL DEFAULT FALSE,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_statement_card FOREIGN KEY (card_id) REFERENCES cards(id)
);